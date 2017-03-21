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

package com.spynet.camera.network;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.BatteryManager;
import android.support.annotation.Nullable;
import android.util.Base64;
import android.util.Log;

import com.spynet.camera.common.TimeoutCache;
import com.spynet.camera.common.Image;
import com.spynet.camera.common.Utils;
import com.spynet.camera.media.AudioData;
import com.spynet.camera.media.ByteArrayInputBitStream;
import com.spynet.camera.media.VideoFrame;
import com.spynet.camera.network.DDNS.DDNSClient;
import com.spynet.camera.network.DDNS.DNSdynamicClient;
import com.spynet.camera.network.DDNS.DuckDNSClient;
import com.spynet.camera.network.DDNS.DynuClient;
import com.spynet.camera.network.DDNS.FreeDNSDClient;
import com.spynet.camera.network.DDNS.NoIpClient;
import com.spynet.camera.network.UPnP.PortMapper;
import com.spynet.camera.ui.SettingsActivity;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Defines the video server used to stream the encoded video to the remote players.
 */
public class StreamServer
        implements
        Closeable,
        TCPListener.ListenerCallback,
        StreamConnection.ConnectionCallback {

    protected final String TAG = getClass().getSimpleName();

    // Socket timeout in milliseconds
    private static final int SOCKET_TIMEOUT = 5000;
    // Socket read buffer size
    private static final int SOCKET_READ_BUFFER = 1024;
    // RTSP session timeout in seconds (declared)
    private static final int RTSP_SESSION_TIMEOUT = 30;
    // RTSP session timeout in seconds (effective, extra time to be safe)
    private static final int RTSP_SAFE_TIMEOUT = RTSP_SESSION_TIMEOUT + 5;
    // MJPEG min quality
    private static final int MJPEG_MIN_QUALITY = 10;
    // MJPEG min speed
    private static final double MJPEG_MIN_FPS = 0.1;
    // DDNS initial update delay in seconds
    private static final long DDNS_UPDATE_DELAY = 30;
    // DDNS update period in seconds
    private static final long DDNS_UPDATE_PERIOD = 10 * 60;

    // List used to keep track of all the active connections
    private final ConcurrentLinkedQueue<StreamConnection> mConnections;
    // Cache to store the POST-GET tunnel to handle RTSP over HTTP streaming
    private final TimeoutCache<String, StreamConnection> mTunnelCache;

    private final Context mContext;                 // The context that uses the StreamServer
    private final ConcurrentHashMap<Long, String>   // Thread-safe streams list
            mStreams;                               //
    private final TCPListener mTcpListener;         // Listener
    private final String mCredentials;              // The authentication credentials
    private final DDNSClient mDDNSClient;           // The DDNS client
    private final PortMapper mPortMapper;           // The UPnP port mapper
    private StreamServerCallback mCallback;         // The callback to notify the client
    private volatile boolean mWiFiAvailable;        // Whether the WiFi is available
    private volatile boolean mMobileAvailable;      // Indicates that the mobile data is available
    private volatile boolean mH264Available;        // Whether the H264 stream is available
    private volatile boolean mAudioAvailable;       // Whether the audio stream is available
    private volatile boolean mTorchOn;              // Whether the torch is on
    private Location mLastLocation;                 // Last known location (null = unknown)
    private byte[] mSPS;                            // Sequence Parameter Set
    private byte[] mPPS;                            // Picture Parameter Set
    private byte[] mAudioCfg;                       // Audio configuration
    private int mAudioFrequency;                    // Audio sampling rate
    private int mAudioChannels;                     // Audio channel configuration

    /**
     * Defines the interface that the client has to implement to handle server events.
     */
    public interface StreamServerCallback {
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
         * Notifies that an action has been requested by a client.
         *
         * @param action the action that has to be handled
         * @param params the action parameters
         */
        void onControlRequest(String action, String params);
    }

    /**
     * Creates a new StreamServer object.
     *
     * @param context the context where the StreamServer is used; it should implement
     *                {@link StreamServerCallback}
     * @param port    the server port number
     */
    public StreamServer(@NotNull Context context, int port) throws IOException {
        mContext = context;
        if (mContext instanceof StreamServerCallback) {
            mCallback = (StreamServerCallback) context;
        } else {
            Log.w(TAG, "StreamServerCallback is not supported by the specified context");
        }
        if (SettingsActivity.getServerAuthentication(mContext)) {
            String username = SettingsActivity.getServerUsername(mContext);
            String password = SettingsActivity.getServerPassword(mContext);
            mCredentials = Base64.encodeToString((username + ":" + password).getBytes(), Base64.NO_WRAP);
        } else {
            mCredentials = null;
        }
        // Setup the server
        mConnections = new ConcurrentLinkedQueue<>();
        mTunnelCache = new TimeoutCache<>();
        mStreams = new ConcurrentHashMap<>();
        mTcpListener = new TCPListener(port, this);
        // Setup the DDNS client
        if (SettingsActivity.getServerUpdateDDNS(mContext)) {
            String hostname = SettingsActivity.getServerDDNSHostname(mContext);
            String username = SettingsActivity.getServerDDNSUsername(mContext);
            String password = SettingsActivity.getServerDDNSPassword(mContext);
            switch (SettingsActivity.getServerDDNSService(mContext)) {
                case "noip":
                    mDDNSClient = new NoIpClient(mContext, hostname, username, password);
                    break;
                case "dynu":
                    mDDNSClient = new DynuClient(mContext, hostname, username, password);
                    break;
                case "dnsdynamic":
                    mDDNSClient = new DNSdynamicClient(mContext, hostname, username, password);
                    break;
                case "freedns":
                    mDDNSClient = new FreeDNSDClient(mContext, hostname, username, password);
                    break;
                case "duckdns":
                    mDDNSClient = new DuckDNSClient(mContext, hostname, username, password);
                    break;
                default:
                    mDDNSClient = null;
            }
        } else {
            mDDNSClient = null;
        }
        // Setup the UPnP port mapper
        if (SettingsActivity.getServerUPnP(mContext)) {
            mPortMapper = new PortMapper(port);
        } else {
            mPortMapper = null;
        }
    }

    /**
     * Closes the server and shutdowns all the active connections.
     */
    @Override
    public void close() {
        mTcpListener.close();
        mTunnelCache.close();
        for (StreamConnection c : mConnections)
            c.close();
        if (mDDNSClient != null)
            mDDNSClient.close();
        if (mPortMapper != null)
            mPortMapper.close();
    }

    /**
     * Sets the WiFi availability flag.
     */
    public void setWiFiAvailable(boolean available) {
        mWiFiAvailable = available;
        setupDDNSClient();
    }

    /**
     * Sets the mobile data availability flag.
     */
    public void setMobileAvailable(boolean available) {
        mMobileAvailable = available;
        setupDDNSClient();
    }

    /**
     * Helper to setup the DDNS client.
     */
    private void setupDDNSClient() {
        if (mDDNSClient != null) {
            String network = SettingsActivity.getServerDDNSNetwork(mContext);
            if (mWiFiAvailable && (network.equals("wifi") || network.equals("all"))) {
                if (!mDDNSClient.isStarted())
                    mDDNSClient.start(DDNS_UPDATE_DELAY, DDNS_UPDATE_PERIOD);
            } else if (mMobileAvailable && (network.equals("mobile") || network.equals("all"))) {
                if (!mDDNSClient.isStarted())
                    mDDNSClient.start(DDNS_UPDATE_DELAY, DDNS_UPDATE_PERIOD);
            } else {
                mDDNSClient.close();
            }
        }
    }

    /**
     * Sets the H264 availability flag.
     */
    public void setH264Available(boolean available) {
        mH264Available = available;
    }

    /**
     * Sets the audio availability flag.
     */
    public void setAudioAvailable(boolean available) {
        mAudioAvailable = available;
    }

    /**
     * Sets the torch on flag.
     */
    public void setTorch(boolean state) {
        mTorchOn = state;
    }

    /**
     * Sets the last known location.
     */
    public synchronized void setLocation(Location location) {
        mLastLocation = location;
    }

    /**
     * Pushes a video data frame to all the active connections.<br>
     * Saves SPS and PPS locally for later use.
     *
     * @param frame the video frame
     * @throws InterruptedException if interrupted while waiting
     */
    public void push(VideoFrame frame) throws InterruptedException {
        if (frame.isConfig()) {
            // Save SPS and PPS
            byte[] data = frame.getData();
            if (data != null) {
                if (frame.getKey().equals("sps")) {
                    synchronized (this) {
                        mSPS = Arrays.copyOfRange(data, 4, data.length);
                    }
                } else if (frame.getKey().equals("pps")) {
                    synchronized (this) {
                        mPPS = Arrays.copyOfRange(data, 4, data.length);
                    }
                }
            }
        } else {
            // Forward to all the opened connections
            for (StreamConnection c : mConnections)
                c.push(frame);
        }
    }

    /**
     * Pushes a chunk of audio data to all the active connections.<br>
     *
     * @param data the audio data
     * @throws InterruptedException if interrupted while waiting
     */
    public void push(AudioData data) throws InterruptedException {
        if (data.isConfig()) {
            // Save audio configuration
            synchronized (this) {
                mAudioCfg = data.getData();
                // ISO/IEC 14496-3, Syntax of AudioSpecificConfig():
                //  5 bits: object type
                //  4 bits: frequency index
                //  if (frequency index == 15)
                //      24 bits: frequency
                //  4 bits: channel configuration
                //  1 bit: frame length flag
                //  1 bit: dependsOnCoreCoder
                //  1 bit: extensionFlag
                int[] frequencies = new int[]{
                        96000, 88200, 64000, 48000, 44100, 32000, 24000,
                        22050, 16000, 12000, 11025, 8000, 7350, -1, -1
                };
                ByteArrayInputBitStream bs = new ByteArrayInputBitStream(mAudioCfg);
                int objectType = bs.read(5);
                if (objectType == 2) {  // AAC LC
                    int frequencyIndex = bs.read(4);
                    if (frequencyIndex == 15)
                        mAudioFrequency = bs.read(24);
                    else
                        mAudioFrequency = frequencies[frequencyIndex];
                    mAudioChannels = bs.read(4);
                }
            }
        } else {
            // Forward to all the opened connections
            for (StreamConnection c : mConnections) {
                c.push(data);
            }
        }
    }

    @Override
    public void onNewConnection(TCPListener listener, Socket socket) throws IOException {
        // Create a new StreamConnection wrapped around the accepted socket
        new StreamConnection(socket, this, null);
    }

    @Override
    public void onConnectionOpened(TCPConnection connection) {
        StreamConnection c = (StreamConnection) connection;
        mConnections.add(c);
    }

    @Override
    public void handleConnection(TCPConnection connection)
            throws IOException {

        String rtspSessionCookie = null;                    // Session cookie for RTSP over HTTP
        String[] request;                                   // {method, URL, protocol}
        String url;                                         // Requested URL
        HashMap<String, String> headers = new HashMap<>();  // Headers (key-value pairs)
        HashMap<String, String> query = new HashMap<>();    // Query (key-value pairs)
        byte[] body;                                        // Request body
        int bodyLength;                                     // The length of the body content

        // Set the default socket timeout so that the connection will shutdown
        // if there's no client activity
        connection.setTimeout(SOCKET_TIMEOUT);

        while (true) {

            // Read the request
            try {
                headers.clear();
                if (rtspSessionCookie != null)
                    request = getBase64Request(connection, headers);
                else
                    request = getRequest(connection, headers);
                if (request == null)
                    return;
                if (request[0] == null)
                    continue;
                Log.v(TAG, "request: " + request[0] + " " + request[1] + " " + request[2] +
                        " on socket " + connection.toString());
            } catch (SocketTimeoutException e) {
                return;
            }

            // Parse the request
            if ((url = parseRequest(request[1], query)) == null) {
                sendErrorReply(connection, "HTTP/1.1", 400, "Bad Request");
                return;
            }

            // Handle authentication
            if (mCredentials != null) {
                String authentication = headers.get("authorization");
                if (authentication == null) {
                    sendUnauthorizedReply(connection, request[2]);
                    continue;
                } else {
                    boolean authorized = false;
                    String[] parts = authentication.split(" ");
                    if (parts.length == 2) {
                        if (parts[0].equals("Basic") && parts[1].equals(mCredentials)) {
                            authorized = true;
                        }
                    }
                    if (!authorized) {
                        sendUnauthorizedReply(connection, request[2]);
                        continue;
                    }
                }
            }

            // Read the content to flush the input stream
            // Note: 32767 is the dummy size of the RTSP over HTTP POST request content
            int contentLength = Utils.tryParseInt(headers.get("content-length"), 0);
            if (contentLength > 0 && contentLength != 32767) {
                body = new byte[contentLength];
                bodyLength = connection.read(body);
                Log.v(TAG, "Content-Length = " + contentLength + ", read = " + bodyLength);
            } else {
                body = null;
                bodyLength = 0;
            }

            // Handle video streams aliases to support some standard IP cameras
            switch (url) {
                case "/h264":
                case "/H264":
                case "/live":
                case "/live.sdp":
                case "/live/h264":
                case "/live/0/h264.sdp":
                    url = "/video/h264";
                    break;
                case "/h264/trackID=1":
                case "/H264/trackID=1":
                case "/live/trackID=1":
                case "/live.sdp/trackID=1":
                case "/live/h264/trackID=1":
                case "/live/0/h264.sdp/trackID=1":
                    url = "/video/h264/trackID=1";
                    break;
                case "/h264/trackID=2":
                case "/H264/trackID=2":
                case "/live/trackID=2":
                case "/live.sdp/trackID=2":
                case "/live/h264/trackID=2":
                case "/live/0/h264.sdp/trackID=2":
                    url = "/video/h264/trackID=2";
                    break;
                case "/mjpeg":
                case "/live/mjpeg":
                case "/video.mjpg":
                case "/mjpg/video.mjpg":
                case "/live/0/mjpeg.jpg":
                case "/live/0/mjpeg.sdp":
                    url = "/video/mjpeg";
                    break;
            }

            // Serve the request
            switch (request[2]) {

                // HTTP protocol
                case "HTTP/1.1":
                    switch (request[0]) {

                        case "POST":
                            switch (url) {
                                // Remote control
                                // Commands are placed in the request body in the form command=value
                                // It is possible to send more than one command in separate lines,
                                // using both the CR or the CRLF terminator, or on the same line, using the
                                // query syntax command1=value1&command2=value2
                                case "/control":
                                    String contentType = headers.get("content-type");
                                    if (contentType == null || !contentType.contains("text/plain")) {
                                        sendErrorReply(connection, request[2], 400, "Bad Request");
                                        return;
                                    }
                                    if (mCallback == null) {
                                        sendErrorReply(connection, request[2], 503, "Service Unavailable");
                                        return;
                                    }
                                    if (body != null && bodyLength > 0) {
                                        String content = new String(body, 0, bodyLength);
                                        String[] lines = content.split("\n");
                                        for (String ln : lines) {
                                            String[] commands = ln.replace("\r", "").split("&");
                                            for (String cmd : commands) {
                                                String[] parts = cmd.split("=");
                                                if (parts.length == 2) {
                                                    mCallback.onControlRequest(
                                                            parts[0].trim().toLowerCase(),
                                                            parts[1].trim());
                                                }
                                            }
                                        }
                                    }
                                    sendOkReply(connection);
                                    return;
                                // H264 stream (RTSP over HTTP, POST request)
                                // The POST request is never replied to by the server
                                case "/video/h264":
                                    rtspSessionCookie = headers.get("x-sessioncookie");
                                    if (rtspSessionCookie == null)
                                        return;
                                    if (mTunnelCache.get(rtspSessionCookie) == null)
                                        return;
                                    connection.setTimeout(RTSP_SAFE_TIMEOUT * 1000);
                                    break;
                                // Unknown resource
                                default:
                                    return;
                            }
                            break;

                        case "GET":
                            switch (url) {
                                // Debug information
                                case "/status":
                                    String gpsMode = headers.get("gps-mode");
                                    if (gpsMode != null && mCallback != null)
                                        mCallback.onControlRequest("gps-mode", gpsMode);
                                    sendStatusInfos(connection);
                                    return;
                                // Sensors information
                                case "/sensors":
                                    sendSensorsInfos(connection);
                                    return;
                                // Supported video streams list
                                case "/video":
                                    sendVideoList(connection);
                                    return;
                                // Supported audio streams list
                                case "/audio":
                                    sendAudioList(connection);
                                    return;
                                // JPEG stream (quality, fps)
                                case "/video/mjpeg":
                                    if (!canStream(url)) {
                                        sendErrorReply(connection, request[2], 503, "Service Unavailable");
                                        return;
                                    }
                                    sendMJPEGStream((StreamConnection) connection,
                                            query.get("quality"), query.get("fps"));
                                    return;
                                // H264 stream (RTSP over HTTP, GET connection)
                                case "/video/h264":
                                    if (!canStream(url)) {
                                        sendErrorReply(connection, request[2], 503, "Service Unavailable");
                                        return;
                                    }
                                    rtspSessionCookie = headers.get("x-sessioncookie");
                                    if (rtspSessionCookie == null) {
                                        sendErrorReply(connection, "HTTP/1.1", 400, "Bad Request");
                                        return;
                                    }
                                    rtspOverHttpOk((StreamConnection) connection);
                                    mTunnelCache.put(rtspSessionCookie,
                                            (StreamConnection) connection, RTSP_SAFE_TIMEOUT);
                                    while (mTunnelCache.get(rtspSessionCookie) != null) {
                                        // The streaming is controlled by the POST connection
                                        // that uses this connection's output stream to send
                                        // RTP packets and RTSP responses.
                                        // The cache is controlled by the POST connection as well.
                                        try {
                                            Thread.sleep(1000);
                                        } catch (InterruptedException e) {
                                            return;
                                        }
                                    }
                                    return;
                                // Other files from assets/www
                                default:
                                    if (url.equals("/"))
                                        url += "index.html";
                                    sendFile(connection, url);
                                    String connectionType = headers.get("connection");
                                    if (connectionType != null && connectionType.equals("keep-alive"))
                                        break;
                                    else
                                        return;
                            }
                    }
                    break;

                // RTSP protocol
                case "RTSP/1.0":

                    StreamConnection c = (StreamConnection) connection;
                    if (rtspSessionCookie != null) {
                        if ((c = mTunnelCache.get(rtspSessionCookie)) == null)
                            return;
                        mTunnelCache.put(rtspSessionCookie, c, RTSP_SAFE_TIMEOUT);
                    }
                    int seq = Utils.tryParseInt(headers.get("cseq"), 1);

                    switch (request[0]) {
                        case "OPTIONS":
                            rtspOptions(c, seq);
                            break;
                        case "DESCRIBE":
                            rtspDescribe(c, url, seq);
                            break;
                        case "SETUP":
                            if (!canStream(url)) {
                                sendErrorReply(c, request[2], 503, "Service Unavailable");
                                return;
                            }
                            rtspSetup(c, url, seq, headers.get("transport"));
                            break;
                        case "PLAY":
                            rtspPlay(c, url, seq, headers.get("session"));
                            break;
                        case "GET_PARAMETER":
                            rtspGetParameters(c, url, seq, headers.get("session"));
                            break;
                        case "TEARDOWN":
                            rtspTeardown(c, url, seq, headers.get("session"));
                            if (rtspSessionCookie != null) {
                                // This will shutdown the GET connection
                                mTunnelCache.remove(rtspSessionCookie);
                            }
                            break;
                        default:
                            rtspMethodNotAllowed(c, seq);
                            return;
                    }
                    break;

                // Unsupported protocol
                default:
                    sendErrorReply(connection, request[2], 400, "Bad Request");
                    return;
            }
        }
    }

    @Override
    public void onStreamStarted(StreamConnection connection, String type, long id) {
        mStreams.putIfAbsent(id, type);
        if (mCallback != null)
            mCallback.onStreamStarted(type, id);
        Log.v(TAG, "stream started on connection " + connection.toString());
    }

    @Override
    public void onStreamStopped(StreamConnection connection, String type, long id) {
        mStreams.remove(id);
        if (mCallback != null)
            mCallback.onStreamStopped(type, id);
        Log.v(TAG, "stream stopped on connection " + connection.toString());
    }

    @Override
    public void onControlRequest(StreamConnection connection, String action, String params) {
        if (mCallback != null)
            mCallback.onControlRequest(action, params);
    }

    @Override
    public void onConnectionClosed(TCPConnection connection) {
        StreamConnection c = (StreamConnection) connection;
        mConnections.remove(c);
    }

    /**
     * @return true if the connection can stream now the specified stream type
     */
    private boolean canStream(String url) {
        switch (url) {
            case "/video/mjpeg":
                if (!(mWiFiAvailable || mMobileAvailable)) {
                    return false;
                }
                break;
            case "/video/h264":
            case "/video/h264/trackID=1":
            case "/video/h264/trackID=2":
                if (!(mH264Available && (mWiFiAvailable || mMobileAvailable))) {
                    return false;
                }
                break;
            default:
                return false;
        }
        return true;
    }

    /**
     * Helper to read the client request.
     *
     * @param connection the TCPConnection to read from
     * @param headers    the HashMap that will be filled with the key-value pairs from the headers,
     *                   pass mull to ignore
     * @return a three-element String array that contains the method, the URL and the protocol,
     * null on error, {null, null, null} if the request should be ignored
     */
    @Nullable
    private String[] getRequest(TCPConnection connection,
                                @Nullable HashMap<String, String> headers)
            throws IOException {

        byte[] buffer = new byte[SOCKET_READ_BUFFER];
        String request, line;
        int read;

        // Read some bytes of the request so that we can determine which type it is
        if (connection.read(buffer, 0, 4) < 4)
            return null;

        // If we received an interleaved packet just drop it
        // (RTCP over RTSP - see RFC 2326, session 10.12)
        if (buffer[0] == '$') {
            int size = ((int) buffer[2] << 8) + (int) buffer[3];
            read = 0;
            while (read < size)
                read += connection.read(buffer, 0, size - read);
            return new String[]{null, null, null};
        }

        // Read the request line
        request = new String(buffer, 0, 4);
        request = request.concat(connection.readLine());

        // Read the headers
        while (true) {
            line = connection.readLine();
            if (line == null || line.isEmpty())
                break;
            if (headers != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    headers.put(parts[0].trim().toLowerCase(), parts[1].trim());
                }
            }
        }

        // Return the requested resource
        String[] parts = request.split(" ");
        return parts.length == 3 ? parts : null;
    }

    /**
     * Helper to read the Base64 encoded client request.
     *
     * @param connection the TCPConnection to read from
     * @param headers    the HashMap that will be filled with the key-value pairs from the headers,
     *                   pass mull to ignore
     * @return a three-element String array that contains the method, the URL and the protocol,
     * null on error
     */
    @Nullable
    private String[] getBase64Request(TCPConnection connection,
                                      @Nullable HashMap<String, String> headers)
            throws IOException {

        byte[] buffer = new byte[SOCKET_READ_BUFFER];
        String request, line;
        int read;

        // Read the request
        if ((read = connection.read(buffer)) == -1)
            return null;
        buffer = Base64.decode(buffer, 0, read, Base64.NO_WRAP);
        BufferedReader reader = new BufferedReader(new StringReader(new String(buffer)));

        // Read the request line
        request = reader.readLine();

        // Read the headers
        while (true) {
            line = reader.readLine();
            if (line == null || line.isEmpty())
                break;
            if (headers != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    headers.put(parts[0].trim().toLowerCase(), parts[1].trim());
                }
            }
        }

        // Return the requested resource
        String[] parts = request.split(" ");
        return parts.length == 3 ? parts : null;
    }

    /**
     * Helper to parse the Request-URI.
     *
     * @param requestURI the Request-URI the client requested
     * @param params     the HashMap that will be filled with the key-value pairs from the query
     *                   part of the requestURI (pass mull to ignore)
     * @return the path part of the requestURI
     */
    @Nullable
    private String parseRequest(String requestURI, HashMap<String, String> params) {

        final URI uri;

        // Create an URI object from the Request-URI
        try {
            uri = new URI(requestURI);
        } catch (URISyntaxException e) {
            return null;
        }

        // Parse the query
        if (params != null) {
            if (uri.getQuery() != null) {
                for (String p : uri.getQuery().split("&")) {
                    String[] kvp = p.split("=");
                    if (kvp.length == 2) {
                        params.put(kvp[0].toLowerCase(), kvp[1]);
                    }
                }
            }
        }

        // Return the path (without the final '/')
        String path = uri.getPath();
        if (path.length() > 1 && path.endsWith("/"))
            path = path.substring(0, path.length() - 1);
        return path;
    }

    /**
     * Helper to send an error reply.
     */
    private void sendErrorReply(TCPConnection connection,
                                String protocol, int statusCode, String reasonPhrase)
            throws IOException {
        String response = "" +
                protocol + " " + statusCode + " " + reasonPhrase + "\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Pragma: no-cache\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: " + reasonPhrase.length() + "\r\n" +
                "\r\n";
        connection.write(response + reasonPhrase);
    }

    /**
     * Helper to send the message to request basic authentication.
     */
    private void sendUnauthorizedReply(TCPConnection connection, String protocol)
            throws IOException {
        String response = "" +
                protocol + " " + 401 + " Unauthorized\r\n" +
                "WWW-Authenticate: Basic realm=\"spyNet\"\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Pragma: no-cache\r\n" +
                "\r\n";
        connection.write(response);
    }

    /**
     * Helper to send the OK reply.
     */
    private void sendOkReply(TCPConnection connection)
            throws IOException {
        String response = "" +
                "HTTP/1.1 200 OK\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Pragma: no-cache\r\n" +
                "\r\n";
        connection.write(response);
    }

    /**
     * Helper to send a file from assets/www
     */
    private void sendFile(TCPConnection connection, String fileName)
            throws IOException {

        fileName = "www" + fileName;
        String contentType = URLConnection.getFileNameMap().getContentTypeFor(fileName);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (Utils.readAssetFile(mContext, fileName, out)) {
            String response = "" +
                    "HTTP/1.1 200 OK\r\n" +
                    //"Cache-Control: no-cache\r\n" +
                    //"Pragma: no-cache\r\n" +
                    "Content-Type: " + contentType + "\r\n" +
                    "Content-Length: " + out.size() + "\r\n" +
                    "\r\n";
            connection.write(response);
            connection.write(out.toByteArray());
        } else {
            sendErrorReply(connection, "HTTP/1.1", 404, "Not Found");
        }
    }

    /**
     * Helper to send a JSONObject.
     */
    private void sendJSONObject(TCPConnection connection, JSONObject jObject)
            throws IOException {
        String content = jObject.toString();
        String response = "" +
                "HTTP/1.1 200 OK\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Pragma: no-cache\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: " + content.length() + "\r\n" +
                "\r\n";
        connection.write(response + content);
    }

    /**
     * Helper to send the debug information.
     */
    private void sendStatusInfos(TCPConnection connection)
            throws IOException {
        try {
            JSONObject jObject = new JSONObject();
            JSONObject jObjectLocation = new JSONObject();
            synchronized (this) {
                jObjectLocation
                        .put("latitude", mLastLocation != null ? mLastLocation.getLatitude() : 0)
                        .put("longitude", mLastLocation != null ? mLastLocation.getLongitude() : 0)
                        .put("time", mLastLocation != null ? mLastLocation.getTime() : 0)
                        .put("accuracy", mLastLocation != null ? mLastLocation.getAccuracy() : -1)
                        .put("provider", mLastLocation != null ? mLastLocation.getProvider() : "none");
            }
            JSONArray jArrayConnections = new JSONArray();
            for (StreamConnection c : mConnections) {
                InetAddress address = c.getInetAddress();
                jArrayConnections.put(new JSONObject()
                        .put("client_address", address != null ? address.getHostName() : "0.0.0.0")
                        .put("MJPEG_stream", c.isStreamingMJPEG())
                        .put("H264_stream", c.isStreamingH264())
                        .put("AAC_stream", c.isStreamingAAC())
                        .put("RTSP_session_ID", c.getRTSPSessionID())
                );
            }
            jObject
                    .put("location", jObjectLocation)
                    .put("connections", jArrayConnections)
                    .put("streams", mStreams.size())
                    .put("WiFi", mWiFiAvailable)
                    .put("mobile", mMobileAvailable)
                    .put("H264", mH264Available)
                    .put("audio", mAudioAvailable);
            sendJSONObject(connection, jObject);
        } catch (JSONException e) {
            sendErrorReply(connection, "HTTP/1.1", 500, "Internal Error");
            Log.e(TAG, "unexpected exception while sending the JSON debug information", e);
        }
    }

    /**
     * Helper to send the sensors information.
     */
    private void sendSensorsInfos(TCPConnection connection)
            throws IOException {

        String batteryCharge = "unknown";
        float batteryPct = -1;

        // Determine battery information
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = mContext.registerReceiver(null, filter);
        if (batteryStatus != null) {
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;
            if (isCharging) {
                int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                if (chargePlug == BatteryManager.BATTERY_PLUGGED_USB)
                    batteryCharge = "USB";
                else if (chargePlug == BatteryManager.BATTERY_PLUGGED_AC)
                    batteryCharge = "AC";
                else
                    batteryCharge = "unknown";
            } else {
                batteryCharge = "unplugged";
            }
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            batteryPct = level * 100.0f / (float) scale;
        }

        try {
            JSONObject jObject = new JSONObject();
            jObject
                    .put("battery", new JSONObject()
                            .put("connection", batteryCharge)
                            .put("level", (int) batteryPct))
                    .put("torch", mTorchOn);
            sendJSONObject(connection, jObject);
        } catch (JSONException e) {
            sendErrorReply(connection, "HTTP/1.1", 500, "Internal Error");
            Log.e(TAG, "unexpected exception while sending the JSON sensors information", e);
        }
    }

    /**
     * Helper to send a the list of the supported video streams.
     */
    private void sendVideoList(TCPConnection connection)
            throws IOException {
        try {
            JSONObject jObject = new JSONObject();
            jObject.put("streams", new JSONArray()
                    .put(new JSONObject()
                            .put("url", "/video/mjpeg")
                            .put("mime", "image/jpeg")
                            .put("available", canStream("/video/mjpeg"))
                            .put("parameters", new JSONArray()
                                    .put(new JSONObject()
                                            .put("name", "quality")
                                            .put("min", MJPEG_MIN_QUALITY)
                                            .put("max", SettingsActivity.getMJPEGQuality(mContext)))
                                    .put(new JSONObject()
                                            .put("name", "fps")
                                            .put("min", MJPEG_MIN_FPS)
                                            .put("max", SettingsActivity.getMJPEGFrameSpeed(mContext)))))
                    .put(new JSONObject()
                            .put("url", "/video/h264")
                            .put("mime", "video/avc")
                            .put("available", canStream("/video/h264"))
                            .put("parameters", new JSONArray()))
            );
            sendJSONObject(connection, jObject);
        } catch (JSONException e) {
            sendErrorReply(connection, "HTTP/1.1", 500, "Internal Error");
            Log.e(TAG, "unexpected exception while sending the JSON video streams list", e);
        }
    }

    /**
     * Helper to send a the list of the supported audio streams.
     */
    private void sendAudioList(TCPConnection connection)
            throws IOException {
        try {
            JSONObject jObject = new JSONObject();
            jObject.put("streams", new JSONArray()
                    .put(new JSONObject()
                            .put("url", "/video/h264")
                            .put("mime", "audio/mp4a-latm")
                            .put("available", canStream("/video/h264") && mAudioAvailable)
                            .put("parameters", new JSONArray()))
            );
            sendJSONObject(connection, jObject);
        } catch (JSONException e) {
            sendErrorReply(connection, "HTTP/1.1", 500, "Internal Error");
            Log.e(TAG, "unexpected exception while sending the JSON audio streams list", e);
        }
    }

    /**
     * Helper to send the MJPEG stream.
     */
    private void sendMJPEGStream(final StreamConnection connection, String quality, String fps)
            throws IOException {

        final long id = Utils.getUniqueID();
        VideoFrame frame;

        int jpegQuality = Utils.coerce(
                Utils.tryParseInt(quality, SettingsActivity.getMJPEGQuality(mContext)),
                MJPEG_MIN_QUALITY, SettingsActivity.getMJPEGQuality(mContext));
        double mjpegFps = Utils.coerce(
                Utils.tryParseDouble(fps, SettingsActivity.getMJPEGFrameSpeed(mContext)),
                MJPEG_MIN_FPS, SettingsActivity.getMJPEGFrameSpeed(mContext));
        long delay = (long) (1000000.0 / mjpegFps);
        long lastTime = 0;

        String response = "" +
                "HTTP/1.1 200 OK\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Pragma: no-cache\r\n" +
                "Content-Type: multipart/x-mixed-replace;boundary=jpegboundary\r\n" +
                "\r\n";
        connection.write(response);

        connection.clearFrames();
        connection.notifyStreamStarted(StreamConnection.TYPE_MJPEG, id);
        try {
            connection.clearFrames();
            while (!Thread.currentThread().isInterrupted()) {
                // Check WiFi status
                if (!canStream("/video/mjpeg"))
                    break;
                // Get a frame from the queue
                if ((frame = connection.popFrame()) == null)
                    continue;
                // Control the fps
                if (frame.getTimestamp() < lastTime + delay)
                    continue;
                lastTime = frame.getTimestamp();
                // Compress and send the JPEG image
                connection.write("" +
                        "--jpegboundary\r\n" +
                        "Content-Type: image/jpeg\r\n" +
                        "\r\n");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                Image.compressToJpeg(frame.getData(), frame.getWidth(), frame.getHeight(),
                        frame.getFormat(), jpegQuality, out);
                connection.write(out.toByteArray());
            }
        } catch (InterruptedException e) {
            Log.v(TAG, "stream interrupted");
        } finally {
            connection.notifyStreamStopped(StreamConnection.TYPE_MJPEG, id);
        }
    }

    /**
     * Sends RTSP OPTIONS response.
     */
    private void rtspOptions(StreamConnection connection, int seq)
            throws IOException {
        String response = "" +
                "RTSP/1.0 200 OK\r\n" +
                "CSeq: " + seq + "\r\n" +
                "Public: DESCRIBE, SETUP, PLAY, GET_PARAMETER, TEARDOWN\r\n" +
                "\r\n";
        connection.write(response);
    }

    /**
     * Sends RTSP DESCRIBE response.
     */
    private void rtspDescribe(StreamConnection connection, String url, int seq)
            throws IOException {

        String sps, pps, audioCfg;
        int frequency, channels;

        // Check URI
        if (!url.equals("/video/h264")) {
            sendErrorReply(connection, "RTSP/1.0", 404, "Not Found");
            return;
        }

        // Check SPS and PPS available
        synchronized (this) {
            if (mSPS == null || mPPS == null) {
                sps = pps = null;
            } else {
                sps = Base64.encodeToString(mSPS, Base64.NO_WRAP);
                pps = Base64.encodeToString(mPPS, Base64.NO_WRAP);
            }
        }
        if (sps == null || pps == null) {
            sendErrorReply(connection, "RTSP/1.0", 503, "Service Unavailable");
            return;
        }

        // Parse audio configuration
        synchronized (this) {
            if (mAudioCfg == null) {
                audioCfg = null;
            } else {
                audioCfg = "";
                for (byte b : mAudioCfg) {
                    audioCfg += String.format("%02x", b);
                }
            }
            frequency = mAudioFrequency;
            channels = mAudioChannels;
        }

        // Describe
        String content = "" +
                "v=0\r\n" +
                "m=video 0 RTP/AVP 96\r\n" +
                "a=rtpmap:96 H264/90000\r\n" +
                "a=fmtp:96 packetization-mode=1;sprop-parameter-sets=" + sps + "," + pps + "\r\n" +
                "a=control:trackID=1\r\n";
        if (mAudioAvailable && audioCfg != null && frequency > 0 && channels > 0) {
            content += "" +
                    "m=audio 0 RTP/AVP 96\r\n" +
                    "a=rtpmap:96 mpeg4-generic/" + frequency + "/" + channels + "\r\n" +
                    "a=fmtp:96 profile-level-id=1;mode=AAC-hbr;sizelength=13;indexlength=3;indexdeltalength=3;config=" + audioCfg + "\r\n" +
                    "a=control:trackID=2\r\n";
        }
        String response = "" +
                "RTSP/1.0 200 OK\r\n" +
                "CSeq: " + seq + "\r\n" +
                "Content-Type: application/sdp\r\n" +
                "Content-Length: " + content.length() + "\r\n" +
                "\r\n";
        connection.write(response + content);
    }

    /**
     * Sends RTSP SETUP response and setup the session.
     */
    private void rtspSetup(StreamConnection connection, String url, int seq, String transport)
            throws IOException {

        String transportSetup, session;

        // Check URI
        if (!url.equals("/video/h264/trackID=1") && !url.equals("/video/h264/trackID=2")) {
            sendErrorReply(connection, "RTSP/1.0", 404, "Not Found");
            return;
        }

        // Open RTSP session
        if (connection.getRTSPSessionID() == null) {
            connection.openRTSPSession();
        }

        // Get client port
        if (transport == null) {
            sendErrorReply(connection, "RTSP/1.0", 400, "Bad Request");
            return;
        }
        if (transport.startsWith("RTP/AVP/UDP;") || transport.startsWith("RTP/AVP;")) {
            int rtpPort = -1, rtcpPort = -1;
            int rtpServerPort, rtcpServerPort;
            for (String p : transport.split(";")) {
                if (p.matches("client_port=([0-9]+)-([0-9]+)")) {
                    rtpPort = Utils.tryParseInt(p.split("=")[1].split("-")[0], 0);
                    rtcpPort = Utils.tryParseInt(p.split("=")[1].split("-")[1], 0);
                    break;
                }
            }
            if (rtpPort == -1 || rtcpPort == -1) {
                sendErrorReply(connection, "RTSP/1.0", 400, "Bad Request");
                return;
            }
            if (url.equals("/video/h264/trackID=1")) {
                connection.setupVideoUDP(90000, rtpPort, rtcpPort);
                rtpServerPort = connection.getRTPVideoLocalPort();
                rtcpServerPort = connection.getRTCPVideoLocalPort();
            } else {    // "/video/h264/trackID=2"
                connection.setupAudioUDP(mAudioFrequency, rtpPort, rtcpPort);
                rtpServerPort = connection.getRTPAudioLocalPort();
                rtcpServerPort = connection.getRTCPAudioLocalPort();
            }
            transportSetup = "RTP/AVP/UDP;unicast" +
                    ";client_port=" + rtpPort + "-" + rtcpPort +
                    ";server_port=" + rtpServerPort + "-" + rtcpServerPort;
            session = connection.getRTSPSessionID();
        } else if (transport.startsWith("RTP/AVP/TCP;")) {
            int rtpChannel = -1, rtcpChannel = -1;
            for (String p : transport.split(";")) {
                if (p.matches("interleaved=([0-9]+)-([0-9]+)")) {
                    rtpChannel = Utils.tryParseInt(p.split("=")[1].split("-")[0], 0);
                    rtcpChannel = Utils.tryParseInt(p.split("=")[1].split("-")[1], 0);
                    break;
                }
            }
            if (rtpChannel == -1 || rtcpChannel == -1) {
                sendErrorReply(connection, "RTSP/1.0", 400, "Bad Request");
                return;
            }
            transportSetup = "RTP/AVP/TCP;unicast;interleaved=" + rtpChannel + "-" + rtcpChannel;
            if (url.equals("/video/h264/trackID=1")) {
                connection.setupVideoTCP(90000, rtpChannel, rtcpChannel);
            } else {    // "/video/h264/trackID=2"
                connection.setupAudioTCP(mAudioFrequency, rtpChannel, rtcpChannel);
            }
            session = connection.getRTSPSessionID();
        } else {
            sendErrorReply(connection, "RTSP/1.0", 400, "Bad Request");
            return;
        }

        // Setup
        connection.setTimeout(RTSP_SAFE_TIMEOUT * 1000);
        String response = "" +
                "RTSP/1.0 200 OK\r\n" +
                "CSeq: " + seq + "\r\n" +
                "Transport: " + transportSetup + "\r\n" +
                "Session: " + session + ";timeout=" + RTSP_SESSION_TIMEOUT + "\r\n" +
                "\r\n";
        connection.write(response);
    }

    /**
     * Sends RTSP PLAY response and start streaming.
     */
    private void rtspPlay(StreamConnection connection, String url, int seq, String session)
            throws IOException {

        // Check URI
        if (!url.equals("/video/h264") && !url.equals("/video/h264/trackID=1") && !url.equals("/video/h264/trackID=2")) {
            sendErrorReply(connection, "RTSP/1.0", 404, "Not Found");
            return;
        }

        // Check session
        if (session == null) {
            sendErrorReply(connection, "RTSP/1.0", 400, "Bad Request");
            return;
        }
        if (connection.getRTSPSessionID() == null) {
            sendErrorReply(connection, "RTSP/1.0", 455, "Method Not Valid in This State");
            return;
        }
        if (!session.equals(connection.getRTSPSessionID())) {
            sendErrorReply(connection, "RTSP/1.0", 454, "Session Not Found");
            return;
        }

        // Play
        String response = "" +
                "RTSP/1.0 200 OK\r\n" +
                "CSeq: " + seq + "\r\n" +
                "RTP-Info: url=" + url + ";seq=" + connection.getRTPSeq() + "\r\n" +
                "Session: " + connection.getRTSPSessionID() + ";timeout=" + RTSP_SESSION_TIMEOUT + "\r\n" +
                "\r\n";
        connection.write(response);
        if (url.equals("/video/h264") || url.equals("/video/h264/trackID=1"))
            connection.playRTP(1);
        if (url.equals("/video/h264") || url.equals("/video/h264/trackID=2"))
            connection.playRTP(2);
    }

    /**
     * Sends RTSP GET_PARAMETERS response.
     */
    private void rtspGetParameters(StreamConnection connection, String url, int seq, String session)
            throws IOException {

        // Check URI
        if (!url.equals("/video/h264")) {
            sendErrorReply(connection, "RTSP/1.0", 404, "Not Found");
            return;
        }

        // Check session
        if (session == null) {
            sendErrorReply(connection, "RTSP/1.0", 400, "Bad Request");
            return;
        }
        if (connection.getRTSPSessionID() == null) {
            sendErrorReply(connection, "RTSP/1.0", 455, "Method Not Valid in This State");
            return;
        }
        if (!session.equals(connection.getRTSPSessionID())) {
            sendErrorReply(connection, "RTSP/1.0", 454, "Session Not Found");
            return;
        }

        // Get parameters
        String response = "" +
                "RTSP/1.0 200 OK\r\n" +
                "CSeq: " + seq + "\r\n" +
                "Session: " + connection.getRTSPSessionID() + ";timeout=" + RTSP_SESSION_TIMEOUT + "\r\n" +
                "\r\n";
        connection.write(response);
    }

    /**
     * Sends RTSP TEARDOWN response.
     */
    private void rtspTeardown(StreamConnection connection, String url, int seq, String session)
            throws IOException {

        // Check URI
        if (!url.equals("/video/h264") && !url.equals("/video/h264/trackID=1") && !url.equals("/video/h264/trackID=2")) {
            sendErrorReply(connection, "RTSP/1.0", 404, "Not Found");
            return;
        }

        // Check session
        if (session == null) {
            sendErrorReply(connection, "RTSP/1.0", 400, "Bad Request");
            return;
        }
        if (connection.getRTSPSessionID() == null) {
            sendErrorReply(connection, "RTSP/1.0", 455, "Method Not Valid in This State");
            return;
        }
        if (!session.equals(connection.getRTSPSessionID())) {
            sendErrorReply(connection, "RTSP/1.0", 454, "Session Not Found");
            return;
        }

        // Teardown
        String response = "" +
                "RTSP/1.0 200 OK\r\n" +
                "CSeq: " + seq + "\r\n" +
                "Session: " + connection.getRTSPSessionID() + ";timeout=" + RTSP_SESSION_TIMEOUT + "\r\n" +
                "\r\n";
        connection.write(response);
        if (url.equals("/video/h264") || url.equals("/video/h264/trackID=1"))
            connection.stopRTP(1);
        if (url.equals("/video/h264") || url.equals("/video/h264/trackID=2"))
            connection.stopRTP(2);
    }

    /**
     * Sends the RTSP method not allowed response.
     */
    private void rtspMethodNotAllowed(StreamConnection connection, int seq)
            throws IOException {
        String response = "" +
                "RTSP/1.0 405 Method not allowed\r\n" +
                "CSeq: " + seq + "\r\n" +
                "Allow: DESCRIBE, SETUP, PLAY, GET_PARAMETER, TEARDOWN\r\n" +
                "\r\n";
        connection.write(response);
    }

    /**
     * Sends the RTSP over HTTP positive response.
     */
    private void rtspOverHttpOk(StreamConnection connection)
            throws IOException {
        String response = "" +
                "HTTP/1.1 200 OK\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Pragma: no-cache\r\n" +
                "Content-Type: application/x-rtsp-tunneled\r\n" +
                "\r\n";
        connection.write(response);
    }
}
