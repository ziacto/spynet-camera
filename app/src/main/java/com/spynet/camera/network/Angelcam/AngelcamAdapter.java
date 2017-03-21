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

package com.spynet.camera.network.Angelcam;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.util.Log;

import com.spynet.camera.R;
import com.spynet.camera.common.Utils;
import com.spynet.camera.network.Angelcam.API.AckMessage;
import com.spynet.camera.network.Angelcam.API.AngelcamAPI;
import com.spynet.camera.network.Angelcam.API.ControlMessage;
import com.spynet.camera.network.Angelcam.API.HungUpMessage;
import com.spynet.camera.network.Angelcam.API.Message;
import com.spynet.camera.network.Angelcam.API.PingMessage;
import com.spynet.camera.network.Angelcam.API.RedirectMessage;
import com.spynet.camera.network.Angelcam.API.RegisterMessage;
import com.spynet.camera.network.Angelcam.API.ServiceRecord;
import com.spynet.camera.network.Angelcam.API.StatusMessage;
import com.spynet.camera.network.TCPConnection;
import com.spynet.camera.ui.SettingsActivity;

import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

/**
 * Defines the adapter that implements the Arrow protocol, aka Angelcam Ready.
 */
public class AngelcamAdapter implements Closeable, TCPConnection.ConnectionCallback {

    protected final String TAG = getClass().getSimpleName();

    // Service ID for H264 (RTSP)
    private final static int SVC_H264 = 1;
    // Service ID for MJPEG
    private final static int SVC_MJPEG = 2;

    // Timeout to read from the server in ms
    private final static int READ_TIMEOUT = 5000;

    // Proxy buffer size
    private static final int PROXY_BUFFER_SIZE = 65000;

    // Hash map to keep track of all the active connections
    private final ConcurrentHashMap<Integer, TCPConnection> mConnections;

    private final Context mContext;                     // The context that uses the AngelcamAdapter
    private final Random mRandom;                       // Random generator
    private final AngelcamNotification mNotification;   // Notification used to notify the user
    private final Thread mCommThread;                   // Thread to handle connections and communications
    private AngelcamAdapterCallback mCallback;          // The callback to notify the client
    private String mHost;                               // Host to connect to
    private int mPort;                                  // Host connection port
    private int mRetryDelay;                            // Delay before next connection in seconds
    private volatile boolean mWiFiAvailable;            // Whether the WiFi is available
    private volatile boolean mMobileAvailable;          // Indicates that the mobile data is available
    private volatile boolean mIsConnected;              // Indicates whether the adapter is connected

    /**
     * Defines the interface that the client has to implement to handle server events..
     */
    public interface AngelcamAdapterCallback {
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
     * Defines session parameters used to connect to a local service
     */
    private class SessionParameters {

        private final int service;
        private final int session;
        private final TCPConnection connection;

        public SessionParameters(int service, int session, TCPConnection connection) {
            this.service = service;
            this.session = session;
            this.connection = connection;
        }

        public int getServiceID() {
            return service;
        }

        public int getSessionID() {
            return session;
        }

        public TCPConnection getConnection() {
            return connection;
        }
    }

    /**
     * Creates a new AngelcamAdapter object.
     *
     * @param context the context where the AngelcamAdapter is used
     */
    public AngelcamAdapter(@NotNull Context context) {
        // Initialize variables
        mContext = context;
        if (mContext instanceof AngelcamAdapterCallback) {
            mCallback = (AngelcamAdapterCallback) context;
        } else {
            Log.w(TAG, "AngelcamAdapterCallback is not supported by the specified context");
        }
        mRandom = new Random();
        mNotification = new AngelcamNotification(mContext);
        mConnections = new ConcurrentHashMap<>();
        // Setup the default host
        mHost = SettingsActivity.getAngelHost(mContext);
        mPort = SettingsActivity.getAngelPort(mContext);
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
     * @return {@code true} if the adapter is supported on the current device, {@code false} otherwise
     */
    public static boolean isSupported() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
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
     * Tries to connect to the Angelcam server.
     */
    private void connectionLoop() {

        mRetryDelay = 0;

        // Connection loop
        Log.d(TAG, "connection loop started");
        try {
            while (!Thread.currentThread().isInterrupted()) {

                // Random sleep between 1 and 500 ms to reduces reconnect concurrency to servers
                Thread.sleep(1 + mRandom.nextInt(500));

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

                // Try to connect to the server
                connectToServer(mHost, mPort);
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "connection loop interrupted");
        } finally {
            mNotification.cancel();
            Log.d(TAG, "connection loop stopped");
        }
    }

    /**
     * Connects to an Angelcam server and handles the Arrow protocol.
     *
     * @param host the hostname to connect to
     * @param port the server port to use
     */
    private void connectToServer(String host, int port) {

        TCPConnection connection = null;            // Arrow connection
        String uuid, pwd;                           // Connection UUID and passphrase
        String mac;                                 // MAC address

        try {

            // Do not connect if the network is not available
            if (!(mWiFiAvailable || mMobileAvailable)) {
                Log.w(TAG, "Network is not available");
                mRetryDelay = 10;
                return;
            }

            // Read the WiFi MAC address
            // Some devices may report null when the WiFi is turned off
            mac = Utils.getMACAddress("wlan0");
            if (mac == null) {
                Log.w(TAG, "MAC address is not available");
                // Notify the need to turn the WiFi on
                mNotification.notify(AngelcamNotification.TYPE_WIFI,
                        mContext.getString(R.string.angel_notify_no_mac_title),
                        mContext.getString(R.string.angel_notify_no_mac_text),
                        null);
                mRetryDelay = 30;
                return;
            }

            // Read the uuid and passphrase from preferences or generate and save new ones
            uuid = SettingsActivity.getAngelUUID(mContext);
            if (uuid.isEmpty()) {
                uuid = UUID.randomUUID().toString();
                SettingsActivity.setAngelUUID(mContext, uuid);
            }
            pwd = SettingsActivity.getAngelPassphrase(mContext);
            if (pwd.isEmpty()) {
                pwd = UUID.randomUUID().toString();
                SettingsActivity.setAngelPassphrase(mContext, pwd);
            }

            // Setup the service table
            int ip = 0x0100007f; // 127.0.0.1
            int svrPort = SettingsActivity.getServerPort(mContext);
            ServiceRecord[] svc = new ServiceRecord[]{
                    new ServiceRecord(SVC_H264, ServiceRecord.TYPE_RTSP, mac, ip, svrPort, "/video/h264"),
                    // MJPEG is currently not fully supported by Angelcam and may use too much bandwidth,
                    // preventing the correct device registration
                    // new ServiceRecord(SVC_MJPEG, ServiceRecord.TYPE_MJPEG, mac, ip, svrPort, "/video/mjpeg?fps=1")
            };

            // Connect to the server
            Log.d(TAG, "client is trying to connect to " + host + ":" + port);
            log(0, R.string.angel_log_connecting, host);
            Socket socket = connect(host, port, AngelcamAPI.CONNECT_TIMEOUT * 1000);
            connection = new TCPConnection(socket, null, null);
            connection.setTimeout(READ_TIMEOUT);
            Log.d(TAG, "client connected to " + host + ":" + port);
            mRetryDelay = 1;

            // Send REGISTER message to the server and read response
            Log.v(TAG, "client sending REGISTER");
            RegisterMessage reg = new RegisterMessage(0, uuid, pwd, mac, svc);
            connection.write(reg.toByteArray());
            Message resp = Message.fromStream(connection);
            if (resp == null)
                throw new InvalidAlgorithmParameterException("cannot read the response");
            if (resp.getServiceID() != 0x00)
                throw new InvalidAlgorithmParameterException("wrong service ID" + resp.getServiceID());
            ControlMessage ctrl = new ControlMessage(resp);
            if (ctrl.getType() != ControlMessage.TYPE_ACK)
                throw new InvalidAlgorithmParameterException("wrong message type ID" + ctrl.getType());
            if (ctrl.getMessageID() != reg.getMessageID())
                throw new InvalidAlgorithmParameterException("message ID doesn't match");
            AckMessage ack = new AckMessage(ctrl);
            // If the REGISTER succeeded
            if (ack.getErrorCode() == AckMessage.ERROR_NO_ERROR) {
                Log.v(TAG, "received ACK");
                log(ack.getErrorCode(), R.string.angel_log_connected, host);
                // Notify successful registration to the user
                mIsConnected = true;
                if (mCallback != null)
                    mCallback.onAdapterConnected("Angelcam", host);
                mNotification.notify(AngelcamNotification.TYPE_DEFAULT,
                        mContext.getString(R.string.angel_notify_registered_title),
                        host,
                        null);
                // Handle the control connection
                try {
                    handleControlConnection(connection);
                } finally {
                    mIsConnected = false;
                    if (mCallback != null)
                        mCallback.onAdapterDisconnected("Angelcam");
                    mNotification.cancel();
                }
                // Connection closed normally
                mRetryDelay = 0;
            }
            // If we received the UNAUTHORIZED error
            else if (ack.getErrorCode() == AckMessage.ERROR_UNAUTHORIZED) {
                Log.v(TAG, "received NACK, code " + ack.getErrorCode());
                log(ack.getErrorCode(), "Unauthorized");
                // Notify the need to register the device to the user
                mNotification.notify(AngelcamNotification.TYPE_REGISTER,
                        mContext.getString(R.string.angel_notify_register_title),
                        String.format(mContext.getString(R.string.angel_notify_register_text), mac),
                        String.format(mContext.getString(R.string.angel_notify_register_description), mac));
                // Retry in 30 seconds
                mRetryDelay = 30;
            }
            // If we received another error
            else {
                Log.v(TAG, "received NACK, code " + ack.getErrorCode());
                log(ack.getErrorCode(), "Error " + ack.getErrorCode());
                // Retry in 30 seconds
                mRetryDelay = 30;
            }

        } catch (Exception e) {
            Log.e(TAG, "connection terminated by error", e);
            log(-1, R.string.angel_log_connection_error);
        } finally {
            // Terminate the uploads
            for (TCPConnection c : mConnections.values())
                c.close();
            // Close the control channel
            if (connection != null)
                connection.close();
        }
    }

    /**
     * Handles the current control connection.
     *
     * @param connection control connection
     */
    private void handleControlConnection(TCPConnection connection)
            throws IOException {

        long lastKeepaliveTime = System.currentTimeMillis();
        Message msg;

        while (!Thread.currentThread().isInterrupted()) {

            // Check if the network is available
            if (!(mWiFiAvailable || mMobileAvailable))
                break;

            // Check if the server failed to send keepalives and is not responsive
            if (lastKeepaliveTime < (System.currentTimeMillis() - (2 * AngelcamAPI.KEEPALIVE_INTERVAL * 1000))) {
                Log.e(TAG, "keepalive heartbeat to server failed, re-connecting");
                throw new IllegalStateException("keepalive failed");
            }

            // Read next message from server
            try {
                msg = Message.fromStream(connection);
                if (msg == null) {
                    Log.d(TAG, "disconnecting, server socket has been closed");
                    break;
                }
            } catch (SocketTimeoutException e) {
                continue;
            }

            // Arrow Control Protocol message
            if (msg.getServiceID() == 0x00) {
                ControlMessage ctrl = new ControlMessage(msg);
                // REDIRECT message
                if (ctrl.getType() == ControlMessage.TYPE_REDIRECT) {
                    Log.v(TAG, "received REDIRECT");
                    RedirectMessage redir = new RedirectMessage(ctrl);
                    // Disconnect
                    mHost = redir.getHost();
                    mPort = redir.getPort();
                    Log.v(TAG, "redirecting to host " + mHost + ", port " + mPort);
                    break;
                }
                // PING message
                else if (ctrl.getType() == ControlMessage.TYPE_PING) {
                    Log.v(TAG, "received PING");
                    lastKeepaliveTime = System.currentTimeMillis();
                    PingMessage ping = new PingMessage(ctrl);
                    Log.v(TAG, "replying to PING with ACK (NO_ERROR)");
                    AckMessage ack = new AckMessage(ping.getMessageID(), AckMessage.ERROR_NO_ERROR);
                    connection.write(ack.toByteArray());
                }
                // GET_STATUS message
                else if (ctrl.getType() == ControlMessage.TYPE_GET_STATUS) {
                    Log.v(TAG, "received GET_STATUS");
                    lastKeepaliveTime = System.currentTimeMillis();
                    int sessions = mConnections.size();
                    Log.v(TAG, "replying to GET_STATUS with STATUS (sessions = " + sessions + ")");
                    StatusMessage status = new StatusMessage(ctrl.getMessageID(), 0, sessions);
                    connection.write(status.toByteArray());
                }
                // HUP message
                else if (ctrl.getType() == ControlMessage.TYPE_HUP) {
                    Log.v(TAG, "received HUP");
                    lastKeepaliveTime = System.currentTimeMillis();
                    HungUpMessage hup = new HungUpMessage(ctrl);
                    int session = hup.getSessionID();
                    int error = hup.getErrorCode();
                    if (mConnections.containsKey(session)) {
                        Log.v(TAG, "shutting down session, id " + session + ", error " + error);
                        mConnections.get(session).close();
                    } else {
                        Log.w(TAG, "unknown session, id " + session);
                    }
                }
                // RESET_SVC_TABLE message
                else if (ctrl.getType() == ControlMessage.TYPE_RESET_SVC_TABLE) {
                    Log.v(TAG, "received RESET_SVC_TABLE (ignored)");
                    lastKeepaliveTime = System.currentTimeMillis();
                }
                // SCAN_NETWORK message
                else if (ctrl.getType() == ControlMessage.TYPE_SCAN_NETWORK) {
                    Log.v(TAG, "received SCAN_NETWORK (ignored)");
                    lastKeepaliveTime = System.currentTimeMillis();
                }
                // GET_SCAN_REPORT message
                else if (ctrl.getType() == ControlMessage.TYPE_GET_SCAN_REPORT) {
                    Log.v(TAG, "received GET_SCAN_REPORT (ignored)");
                    lastKeepaliveTime = System.currentTimeMillis();
                }
                // Unhandled message
                else {
                    Log.w(TAG, "received unhandled message, type " + ctrl.getType());
                }
            }
            // Local service request
            else {
                int svc = msg.getServiceID();
                int session = msg.getSessionID();
                TCPConnection c;
                // Create or retrieve the connection to the local service
                if (!mConnections.containsKey(session)) {
                    Log.d(TAG, "starting new session, id " + session + ", service " + svc);
                    int port = SettingsActivity.getServerPort(mContext);
                    Socket s = new Socket("127.0.0.1", port);
                    c = new TCPConnection(s, this, new SessionParameters(svc, session, connection));
                    mConnections.put(session, c);
                } else {
                    c = mConnections.get(session);
                }
                // Forward the message body to the local service
                try {
                    c.write(msg.getBody());
                } catch (Exception e) {
                    Log.e(TAG, "unexpected exception on session id " + session + ", service " + svc, e);
                    c.close();
                }
            }
        }
    }

    @Override
    public void onConnectionOpened(TCPConnection connection) {
        SessionParameters params = (SessionParameters) connection.getBundledData();
        Log.d(TAG, "session started, id " + params.getSessionID() +
                ", service " + params.getServiceID());
        log(0, R.string.angel_log_session_start, params.getServiceID());
    }

    @Override
    public void handleConnection(TCPConnection connection) throws IOException {

        byte[] buffer = new byte[PROXY_BUFFER_SIZE];
        SessionParameters params = (SessionParameters) connection.getBundledData();

        connection.setTimeout(READ_TIMEOUT);
        while (!Thread.currentThread().isInterrupted()) {
            int read = connection.read(buffer);
            if (read == -1) {
                Log.d(TAG, "shutting down session, id " + params.getSessionID() +
                        ", service " + params.getServiceID() + " has been closed");
                break;
            }
            byte[] body = new byte[read];
            System.arraycopy(buffer, 0, body, 0, read);
            Message msg = new Message(AngelcamAPI.VERSION,
                    params.getServiceID(),
                    params.getSessionID(),
                    body);
            params.getConnection().write(msg.toByteArray());
        }
    }

    @Override
    public void onConnectionClosed(TCPConnection connection) {
        SessionParameters params = (SessionParameters) connection.getBundledData();
        mConnections.remove(params.getSessionID());
        try {
            Log.v(TAG, "sending HUP (session id " + params.getSessionID() + ")");
            HungUpMessage hup = new HungUpMessage(0, params.getSessionID(), AckMessage.ERROR_NO_ERROR);
            params.getConnection().write(hup.toByteArray());
        } catch (Exception e) {
            Log.e(TAG, "unexpected exception while sending HUP", e);
        }
        Log.d(TAG, "session stopped, id " + params.getSessionID() +
                ", service " + params.getServiceID());
        log(0, R.string.angel_log_session_stop, params.getServiceID());
    }

    /**
     * Establishes an SSL connection.
     *
     * @param host    the host to connect to
     * @param port    connection port
     * @param timeout connection timeout in milliseconds
     * @return the connected socket
     */
    private Socket connect(String host, int port, int timeout)
            throws IOException, CertificateException, KeyStoreException,
            NoSuchAlgorithmException, KeyManagementException {

        SSLSocket socket;

        // Load self-signed CA
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        AssetManager am = mContext.getAssets();
        InputStream is = am.open("Angelcam/ca.pem", AssetManager.ACCESS_BUFFER);
        Certificate ca;
        try {
            ca = cf.generateCertificate(is);
            Log.v(TAG, "ca: " + ((X509Certificate) ca).getSubjectDN());
        } finally {
            is.close();
        }
        // Create a KeyStore containing the CA
        String keyStoreType = KeyStore.getDefaultType();
        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        keyStore.load(null, null);
        keyStore.setCertificateEntry("ca", ca);
        // Create a TrustManager that trusts the CA in the KeyStore
        String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
        tmf.init(keyStore);
        // Create an SSLContext that uses our TrustManager
        SSLContext context = SSLContext.getInstance("TLSv1.2");
        context.init(null, tmf.getTrustManagers(), null);
        // Open the SSLSocket
        SocketFactory sf = context.getSocketFactory();
        socket = (SSLSocket) sf.createSocket();
        socket.setEnabledProtocols(new String[]{"TLSv1.2"});
        socket.setEnabledCipherSuites(new String[]{"TLS_DHE_RSA_WITH_AES_128_CBC_SHA256"});
        socket.connect(new InetSocketAddress(host, port), timeout);
        /* TODO: now using a self-signed certificate
        // Verify that the certificate hostname is for the specified host
        HostnameVerifier hv = HttpsURLConnection.getDefaultHostnameVerifier();
        SSLSession s = socket.getSession();
        if (!hv.verify(host, s)) {
            socket.close();
            throw new SSLHandshakeException("Expected " + host + ", " +
                    "found " + s.getPeerPrincipal());
        }
        */
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
        if (mCallback != null && SettingsActivity.getAngelLog(mContext)) {
            if (message == null || message.isEmpty()) {
                mCallback.onLog(code, "");
            } else {
                String msg = "Angelcam: ";
                msg += message;
                mCallback.onLog(code, msg);
            }
        }
    }
}
