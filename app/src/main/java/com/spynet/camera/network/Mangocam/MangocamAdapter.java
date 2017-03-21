/*
 * This file is part of spyNet Camera, the Android IP camera
 *
 * Copyright (C) 2016-2017 Paolo Dematteis
 *
 * spyNet Camera is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * spyNet Camera is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Paolo Dematteis - spynet314@gmail.com
 */

package com.spynet.camera.network.Mangocam;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import com.spynet.camera.R;
import com.spynet.camera.common.Image;
import com.spynet.camera.common.Utils;
import com.spynet.camera.media.VideoFrame;
import com.spynet.camera.network.Mangocam.API.DisconnectCommand;
import com.spynet.camera.network.Mangocam.API.HelloCommand;
import com.spynet.camera.network.Mangocam.API.InfoCommand;
import com.spynet.camera.network.Mangocam.API.MangocamAPI;
import com.spynet.camera.network.Mangocam.API.NoopCommand;
import com.spynet.camera.network.Mangocam.API.QuitCommand;
import com.spynet.camera.network.Mangocam.API.ReconnectCommand;
import com.spynet.camera.network.Mangocam.API.SendMJPEGCommand;
import com.spynet.camera.network.Mangocam.API.StatsCommand;
import com.spynet.camera.network.Mangocam.API.StopMJPEGCommand;
import com.spynet.camera.network.StreamConnection;
import com.spynet.camera.network.TCPConnection;
import com.spynet.camera.ui.SettingsActivity;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.net.SocketFactory;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Defines the adapter used to push the images on Mangocam cloud.
 */
public class MangocamAdapter implements Closeable, StreamConnection.ConnectionCallback {

    protected final String TAG = getClass().getSimpleName();

    // Timeout to read from the server in ms
    private final static int READ_TIMEOUT = 5000;

    // List used to keep track of all the active connections
    private final ConcurrentLinkedQueue<StreamConnection> mConnections;

    private final Context mContext;                     // The context that uses the MangocamAdapter
    private final Random mRandom;                       // Random generator
    private final ClipboardManager mClipboard;          // Clipboard manager
    private final MangocamNotification mNotification;   // Notification used to notify the user
    private final Thread mCommThread;                   // Thread to handle connections and communications
    private final ConcurrentHashMap<Long, String>       // Thread-safe streams list
            mStreams;
    private MangocamAdapterCallback mCallback;          // The callback to notify the client
    private List<String> mHosts;                        // List of known hosts
    private int mHostIndex;                             // Index of the next host to use
    private long mStartTime;                            // Start time in milliseconds
    private int mRetryDelay;                            // Delay before next connection in seconds
    private int mReconnectTimer;                        // Time before reconnection in seconds
    private String mReconnectReason;                    // Reason for reconnection
    private SendMJPEGCommand mSendCmd;                  // Command used to send the MJPEG stream
    private volatile int mImageWidth;                   // Image width
    private volatile int mImageHeight;                  // Image height
    private volatile boolean mWiFiAvailable;            // Whether the WiFi is available
    private volatile boolean mMobileAvailable;          // Indicates that the mobile data is available
    private volatile boolean mIsConnected;              // Indicates whether the adapter is connected

    /**
     * Defines the interface that the client has to implement to handle server events..
     */
    public interface MangocamAdapterCallback {
        /**
         * Notifies that a new stream has started.
         *
         * @param type the stream type
         * @param id   the stream id
         */
        void onStreamStarted(String type, long id);

        /**
         * Notifies that a stream has stopped.
         *
         * @param type the stream type
         * @param id   the stream id
         */
        void onStreamStopped(String type, long id);

        /**
         * Notifies that an adapter has connected to its server.
         *
         * @param adapter the adapter name
         * @param host    the connected host
         */
        void onAdapterConnected(String adapter, String host);

        /**
         * Notifies that an adapter has disconnected from the server.
         *
         * @param adapter the adapter name
         */
        void onAdapterDisconnected(String adapter);

        /**
         * Notifies that an action has been requested by a client.
         *
         * @param code    the log code
         * @param message the log message
         */
        void onLog(int code, String message);
    }

    /**
     * Creates a new MangocamAdapter object.
     *
     * @param context the context where the MangocamAdapter is used
     */
    public MangocamAdapter(@NotNull Context context) {
        // Initialize variables
        mContext = context;
        if (mContext instanceof MangocamAdapterCallback) {
            mCallback = (MangocamAdapterCallback) context;
        } else {
            Log.w(TAG, "MangocamAdapterCallback is not supported by the specified context");
        }
        mRandom = new Random();
        mClipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
        mNotification = new MangocamNotification(mContext);
        mConnections = new ConcurrentLinkedQueue<>();
        mStreams = new ConcurrentHashMap<>();
        // Read the hosts list from preferences, use the default host if none
        mHosts = SettingsActivity.getMangoServers(mContext);
        String defaultHost = SettingsActivity.getMangoHost(mContext);
        if (!mHosts.contains(defaultHost))
            mHosts.add(defaultHost);
        mHostIndex = 0;
        // Initialize connections
        mReconnectTimer = 0;
        mReconnectReason = "";
        // Start connection loop
        mCommThread = new Thread(new Runnable() {
            @Override
            public void run() {
                connectionLoop();
            }
        });
        mCommThread.start();
    }

    /**
     * Closes the connection.
     */
    @Override
    public void close() {
        mCommThread.interrupt();
        log(0, null);
    }

    /**
     * Sets the WiFi availability flag.
     */
    public void setWiFiAvailable(boolean available) {
        mWiFiAvailable = available;
    }

    /**
     * Sets the mobile data availability flag.
     */
    public void setMobileAvailable(boolean available) {
        mMobileAvailable = available;
    }

    /**
     * @return {@code true} if the adapter is connected to the underlaying service,
     * {@code false} otherwise
     */
    public boolean isConnected() {
        return mIsConnected;
    }

    /**
     * Pushes an uncompressed video data buffer to the queue.
     *
     * @param frame the uncompressed video data
     * @throws InterruptedException if interrupted while waiting
     */
    public void push(VideoFrame frame) throws InterruptedException {
        // Save image size
        if (!frame.isCompressed()) {
            mImageWidth = frame.getWidth();
            mImageHeight = frame.getHeight();
        }
        // Forward to all the opened connections
        for (StreamConnection c : mConnections)
            c.push(frame);
    }

    /**
     * Tries to connect to one of the available Mangocam servers.
     */
    private void connectionLoop() {

        mStartTime = System.currentTimeMillis();
        mRetryDelay = 0;

        // Connection loop
        Log.d(TAG, "connection loop started");
        try {
            while (!Thread.currentThread().isInterrupted()) {

                // Random sleep between 1 and 500 ms to reduces reconnect concurrency to servers
                Thread.sleep(1 + mRandom.nextInt(500));

                // Reconnect delay, sleep until timer finishes after a forced server disconnect
                if (mReconnectTimer > 0) {
                    Log.w(TAG, "sleeping until " + mReconnectTimer + " seconds have elapsed" +
                            ", reason: " + mReconnectReason);
                    Thread.sleep(mReconnectTimer * 1000);
                    mReconnectTimer = 0;
                    mReconnectReason = "";
                }

                // Retry delay, sleep after connection error then increase the timeout
                // Double value until 32, then random interval between 30 and 60 seconds
                if (mRetryDelay > 0) {
                    Log.w(TAG, "retrying in " + mRetryDelay + " seconds");
                    Thread.sleep(mRetryDelay * 1000);
                    if (mRetryDelay < 30) {
                        mRetryDelay *= 2;
                    } else {
                        mRetryDelay = 30 + mRandom.nextInt(30);
                    }
                } else {
                    mRetryDelay = 1;
                }

                // Try to connect to next available server
                String host = mHosts.get(mHostIndex++);
                int port = SettingsActivity.getMangoPort(mContext);
                mHostIndex %= mHosts.size();
                connectToServer(host, port);
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "connection loop interrupted");
        } finally {
            mNotification.cancel();
            Log.d(TAG, "connection loop stopped");
        }
    }

    /**
     * Connects to a Mangocam server and handles the control channel.
     *
     * @param host the hostname to connect to
     * @param port the server port to use
     */
    private void connectToServer(String host, int port) {

        TCPConnection connection = null;            // Control connection
        HelloCommand helloCmd;                      // HELLO command

        try {

            // Do not connect if the network is not available
            if (!(mWiFiAvailable || mMobileAvailable)) {
                Log.w(TAG, "Network is not available");
                mRetryDelay = 10;
                return;
            }

            // Prepare HELLO command
            // Read the uuid from preferences or generate and save a new one
            String uuid = SettingsActivity.getMangoUUID(mContext);
            helloCmd = new HelloCommand(mContext, uuid);
            if (!helloCmd.getUUID().equals(uuid))
                SettingsActivity.setMangoUUID(mContext, helloCmd.getUUID());

            // Connect to the server
            Log.d(TAG, "client is trying to connect to " + host + ":" + port);
            log(0, R.string.mango_log_connecting, host);
            Socket socket = connect(host, port, MangocamAPI.CONNECT_TIMEOUT * 1000);
            connection = new TCPConnection(socket, null, null);
            connection.setTimeout(READ_TIMEOUT);
            Log.d(TAG, "client connected to " + host + ":" + port);
            mRetryDelay = 1;

            // Send HELLO command to the server and read response
            Log.v(TAG, "client sending HELLO");
            connection.write(helloCmd.get());
            helloCmd.parse(connection.readLine());

            // If we have received the OK from a HELLO request
            if (helloCmd.getErrorCode() == 0) {
                Log.v(TAG, "received OK");
                log(helloCmd.getErrorCode(), R.string.mango_log_connected, host);
                // Notify new connection to the user
                mIsConnected = true;
                if (mCallback != null)
                    mCallback.onAdapterConnected("Mangocam", host);
                mNotification.notify(MangocamNotification.TYPE_DEFAULT,
                        mContext.getString(R.string.mango_notify_connected_title),
                        host,
                        null);
                // Save the list of available hosts
                if (helloCmd.getHosts().size() > 0)
                    SettingsActivity.setMangoServers(mContext, helloCmd.getHosts());
                // Handle the control connection
                try {
                    handleControlConnection(connection, helloCmd.getKeepAliveInterval());
                } finally {
                    mIsConnected = false;
                    if (mCallback != null)
                        mCallback.onAdapterDisconnected("Mangocam");
                    mNotification.cancel();
                }
                // Connection closed normally
                mRetryDelay = 0;
            }
            // If we have received a command saying that the UUID / pin are expired
            else if (helloCmd.getErrorCode() == 1) {
                Log.v(TAG, "received ERR, code " + helloCmd.getErrorCode());
                log(helloCmd.getErrorCode(), helloCmd.getErrorMessge());
                // Delete the cached uuid and start over
                SettingsActivity.setMangoUUID(mContext, "");
                // Notify the user
                mNotification.notify(MangocamNotification.TYPE_DEFAULT,
                        mContext.getString(R.string.mango_notify_error_title),
                        helloCmd.getErrorMessge(),
                        helloCmd.getErrorMessge());
                // Retry in 5 seconds
                mRetryDelay = 5;
            }
            // If we received a new PIN for a the camera
            else if (helloCmd.getErrorCode() == 2) {
                Log.v(TAG, "received ERR, code " + helloCmd.getErrorCode());
                log(helloCmd.getErrorCode(), helloCmd.getErrorMessge());
                int pin = helloCmd.getPIN();
                // Copy the PIN in the clipboard
                mClipboard.setPrimaryClip(
                        ClipData.newPlainText("MangocamPin", String.valueOf(pin)));
                // Notify the user
                mNotification.notify(MangocamNotification.TYPE_REGISTER,
                        mContext.getString(R.string.mango_notify_pin_title),
                        String.format(mContext.getString(R.string.mango_notify_pin_text), pin),
                        String.format(mContext.getString(R.string.mango_notify_pin_description), pin),
                        helloCmd.getLink());
                // Retry in 15 seconds
                mRetryDelay = 15;
            }
            // If we received another error
            else {
                Log.v(TAG, "received ERR, code " + helloCmd.getErrorCode() +
                        ", \"" + helloCmd.getErrorMessge() + "\"");
                log(helloCmd.getErrorCode(), helloCmd.getErrorMessge());
                // Notify the user
                mNotification.notify(MangocamNotification.TYPE_DEFAULT,
                        mContext.getString(R.string.mango_notify_error_title),
                        helloCmd.getErrorMessge(),
                        helloCmd.getErrorMessge());
                // Retry in 15 seconds
                mRetryDelay = 15;
            }

        } catch (Exception e) {
            Log.e(TAG, "connection terminated by error", e);
            log(-1, R.string.mango_log_connection_error);
        } finally {
            // Terminate the uploads
            for (StreamConnection c : mConnections)
                c.close();
            // Close the control channel
            if (connection != null)
                connection.close();
        }
    }

    /**
     * Handles the current control connection.
     *
     * @param connection        control connection
     * @param keepAliveInterval keep-alive interval in seconds
     */
    private void handleControlConnection(TCPConnection connection, int keepAliveInterval)
            throws IOException, JSONException {

        long lastKeepaliveTime = System.currentTimeMillis();
        boolean mjpegUploading = false;
        String command;

        while (!Thread.currentThread().isInterrupted()) {

            // Check if the network is available
            if (!(mWiFiAvailable || mMobileAvailable))
                break;

            // Check if the server failed to send keepalives and is not responsive,
            // if so reconnect to a different server
            if (lastKeepaliveTime < (System.currentTimeMillis() - (2 * keepAliveInterval * 1000))) {
                Log.e(TAG, "keepalive heartbeat to server failed, re-connecting");
                throw new IllegalStateException("keepalive failed");
            }

            // Read next command from server
            try {
                command = connection.readLine();
            } catch (SocketTimeoutException e) {
                continue;
            }

            // NOOP - no-operation
            if (command.startsWith("NOOP ")) {
                Log.v(TAG, "received NOOP");
                lastKeepaliveTime = System.currentTimeMillis();
                NoopCommand noopCmd = new NoopCommand(mContext);
                noopCmd.parse(command);
                Log.v(TAG, "replying to NOOP with OK");
                connection.write(noopCmd.get());
            }
            // INFO - send client information
            else if (command.startsWith("INFO ")) {
                Log.v(TAG, "received INFO");
                lastKeepaliveTime = System.currentTimeMillis();
                InfoCommand infoCmd = new InfoCommand(mContext, mImageWidth, mImageHeight);
                infoCmd.parse(command);
                Log.v(TAG, "replying to INFO with OK");
                connection.write(infoCmd.get());
            }
            // STATS - send statistics
            else if (command.startsWith("STATS ")) {
                Log.v(TAG, "received STATS");
                lastKeepaliveTime = System.currentTimeMillis();
                long uptime = (System.currentTimeMillis() - mStartTime) / 1000;
                StatsCommand statsCmd = new StatsCommand(mContext, uptime, mStreams.size() > 0);
                statsCmd.parse(command);
                Log.v(TAG, "replying to STATS with OK");
                connection.write(statsCmd.get());
            }
            // SEND_MJPEG - start sending mjpeg stream to host provided
            else if (command.startsWith("SEND_MJPEG ")) {
                Log.v(TAG, "received SEND_MJPEG");
                lastKeepaliveTime = System.currentTimeMillis();
                mSendCmd = new SendMJPEGCommand(mContext);
                mSendCmd.parse(command);
                Log.v(TAG, "replying to SEND_MJPEG with OK");
                connection.write(mSendCmd.get());
                // Start uploading
                if (!mjpegUploading) {
                    Socket socket = connect(mSendCmd.getHost(), 443, MangocamAPI.CONNECT_TIMEOUT * 1000);
                    mConnections.add(new StreamConnection(socket, this, StreamConnection.TYPE_MJPEG));
                    mjpegUploading = true;
                    log(0, R.string.mango_log_mjpeg_started);
                    Log.v(TAG, "mjpeg split time is " + mSendCmd.getSplitSec() + " sec");
                } else {
                    Log.w(TAG, "mjpeg stream is already active");
                }
            }
            // STOP_MJPEG - stop mjpeg stream
            else if (command.startsWith("STOP_MJPEG ")) {
                Log.v(TAG, "received STOP_MJPEG");
                lastKeepaliveTime = System.currentTimeMillis();
                StopMJPEGCommand stopCmd = new StopMJPEGCommand(mContext);
                stopCmd.parse(command);
                Log.v(TAG, "replying to STOP_MJPEG with OK");
                connection.write(stopCmd.get());
                // Stop uploading
                if (mjpegUploading) {
                    if (stopCmd.getTask() != mSendCmd.getTask())
                        Log.w(TAG, "wrong task specified");
                    for (StreamConnection c : mConnections) {
                        if (c.isStreamingMJPEG())
                            c.close();
                    }
                    mjpegUploading = false;
                    log(0, R.string.mango_log_mjpeg_stopped);
                } else {
                    Log.w(TAG, "mjpeg stream is not active");
                }
            }
            // RECONNECT - to different server
            else if (command.startsWith("RECONNECT ")) {
                Log.v(TAG, "received RECONNECT");
                lastKeepaliveTime = System.currentTimeMillis();
                ReconnectCommand reconnectCmd = new ReconnectCommand(mContext);
                reconnectCmd.parse(command);
                Log.v(TAG, "replying to RECONNECT with OK");
                connection.write(reconnectCmd.get());
                if (reconnectCmd.getHost() == null)
                    continue;
                // Rebuild hosts hash (first the address just given, then the server list from preference,
                // the initial server), finally disconnect
                int index = mHosts.indexOf(reconnectCmd.getHost());
                if (index == -1) {
                    mHosts.add(0, reconnectCmd.getHost());
                    mHostIndex = 0;
                } else {
                    mHostIndex = index;
                }
                // Disconnect
                break;
            }
            // DISCONNECT - forced by server with timeout
            else if (command.startsWith("DISCONNECT ")) {
                Log.v(TAG, "received DISCONNECT");
                DisconnectCommand disconnectCmd = new DisconnectCommand(mContext);
                disconnectCmd.parse(command);
                Log.v(TAG, "replying to DISCONNECT with OK");
                connection.write(disconnectCmd.get());
                // Get time and reason for disconnect
                mReconnectTimer = disconnectCmd.getWaitDelay();
                mReconnectReason = disconnectCmd.getWaitReason();
                // Notify the user
                Resources res = mContext.getResources();
                mNotification.notify(MangocamNotification.TYPE_DEFAULT,
                        mContext.getString(R.string.mango_notify_error_title),
                        String.format(res.getString(R.string.mango_notify_sleeping_text), mReconnectTimer),
                        String.format(res.getString(R.string.mango_notify_sleeping_description), mReconnectTimer, mReconnectReason));
                // Disconnect
                log(0, mReconnectReason);
                break;
            }
            // Unknown
            else {
                Log.v(TAG, "received unknown command \"" + command + "\"");
                lastKeepaliveTime = System.currentTimeMillis();
            }
        }

        // Send QUIT command
        Log.v(TAG, "client sending QUIT");
        QuitCommand quitCmd = new QuitCommand(mContext);
        connection.write(quitCmd.get());
        quitCmd.parse(connection.readLine());
        if (quitCmd.getErrorCode() == 0) {
            Log.v(TAG, "received OK");
        } else {
            Log.v(TAG, "received ERR, code " + quitCmd.getErrorCode());
        }
    }

    /**
     * Uploads the MJPEG images to the connected server.
     *
     * @param connection the active connection to use
     */
    private void sendMJPEG(StreamConnection connection) throws IOException, InterruptedException {

        VideoFrame frame;
        int jpegQuality = SettingsActivity.getMJPEGQuality(mContext);
        double mjpegFps = Math.min(
                SettingsActivity.getMJPEGFrameSpeed(mContext),
                mSendCmd.getRate());
        long uploadStart = System.currentTimeMillis();
        long delay = (long) (1000000.0 / mjpegFps);
        long lastTime = 0;

        connection.clearFrames();
        connection.write(mSendCmd.getQuery());
        while (!Thread.currentThread().isInterrupted()) {
            // Check if the network is available
            if (!(mWiFiAvailable || mMobileAvailable))
                break;
            // Get a frame from the queue
            if ((frame = connection.popFrame()) == null)
                continue;
            // Control the fps
            if (frame.getTimestamp() < lastTime + delay)
                continue;
            lastTime = frame.getTimestamp();
            // Compress and send the JPEG image
            ByteArrayOutputStream jout = new ByteArrayOutputStream();
            Image.compressToJpeg(frame.getData(), frame.getWidth(), frame.getHeight(),
                    frame.getFormat(), jpegQuality, jout);
            String header = "" +
                    "Mango-Tag: " + System.currentTimeMillis() / 1000 + "\r\n" +
                    "Content-Type: image/jpeg\r\n" +
                    "Content-Length: " + jout.size() + "\r\n" +
                    "\r\n";
            connection.write(header);
            connection.write(jout.toByteArray());
            // Check if need to re-post video (splitting into chunks)
            if (uploadStart < System.currentTimeMillis() - mSendCmd.getSplitSec() * 1000) {
                Log.v(TAG, "splitting video");
                Socket socket = connect(mSendCmd.getHost(), 443, MangocamAPI.CONNECT_TIMEOUT * 1000);
                mConnections.add(new StreamConnection(socket, this, connection.getBundledData()));
                break;
            }
        }
    }

    @Override
    public void onConnectionOpened(TCPConnection connection) {
    }

    @Override
    public void handleConnection(TCPConnection connection) throws IOException {
        StreamConnection c = (StreamConnection) connection;
        final String type = (String) c.getBundledData();
        final long id = Utils.getUniqueID();
        c.notifyStreamStarted(type, id);
        try {
            switch (type) {
                case StreamConnection.TYPE_MJPEG:
                    sendMJPEG(c);
                    break;
            }
        } catch (InterruptedException e) {
            Log.v(TAG, "stream interrupted");
        } finally {
            c.notifyStreamStopped(type, id);
        }
    }

    @Override
    public void onConnectionClosed(TCPConnection connection) {
        StreamConnection c = (StreamConnection) connection;
        mConnections.remove(c);
    }

    @Override
    public void onStreamStarted(StreamConnection connection, String type, long id) {
        mStreams.putIfAbsent(id, type);
        if (mCallback != null)
            mCallback.onStreamStarted(type, id);
        Log.d(TAG, "upload started, type " + type + ", id " + id);
    }

    @Override
    public void onStreamStopped(StreamConnection connection, String type, long id) {
        mStreams.remove(id);
        if (mCallback != null)
            mCallback.onStreamStopped(type, id);
        Log.d(TAG, "upload stopped, type " + type + ", id " + id);
    }

    @Override
    public void onControlRequest(StreamConnection connection, String action, String params) {
    }

    /**
     * Establishes an SSL connection.
     *
     * @param host    the host to connect to
     * @param port    connection port
     * @param timeout connection timeout in milliseconds
     * @return the connected socket
     */
    private Socket connect(String host, int port, int timeout) throws IOException {
        SSLSocket socket;
        // Open the SSLSocket
        SocketFactory sf = SSLSocketFactory.getDefault();
        socket = (SSLSocket) sf.createSocket();
        socket.connect(new InetSocketAddress(host, port), timeout);
        // Verify that the certificate hostname is for the specified host
        HostnameVerifier hv = HttpsURLConnection.getDefaultHostnameVerifier();
        SSLSession s = socket.getSession();
        if (!hv.verify(host, s)) {
            socket.close();
            throw new SSLHandshakeException("Expected " + host + ", " +
                    "found " + s.getPeerPrincipal());
        }
        // At this point SSLSocket performed certificate verification and
        // we have performed hostname verification, so it is safe to proceed
        return socket;
    }

    /**
     * Sends a log message to the client.
     *
     * @param code      log code
     * @param messageId log message resource ID
     * @param args      the list of arguments passed to the formatter
     */
    private void log(int code, int messageId, Object... args) {
        log(code, String.format(mContext.getString(messageId), args));
    }

    /**
     * Sends a log message to the client.
     *
     * @param code    log code
     * @param message log message
     */
    private void log(int code, String message) {
        if (mCallback != null && SettingsActivity.getMangoLog(mContext)) {
            if (message == null || message.isEmpty()) {
                mCallback.onLog(code, "");
            } else {
                String msg = "Mangocam: ";
                msg += message;
                mCallback.onLog(code, msg);
            }
        }
    }
}
