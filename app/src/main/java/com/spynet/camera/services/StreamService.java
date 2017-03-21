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

package com.spynet.camera.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import android.location.Location;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Surface;
import android.widget.Toast;

import com.spynet.camera.R;
import com.spynet.camera.common.Utils;
import com.spynet.camera.media.AudioData;

import com.spynet.camera.media.CameraInfo;
import com.spynet.camera.media.Recorder;
import com.spynet.camera.media.VideoFrame;
import com.spynet.camera.network.Angelcam.AngelcamAdapter;
import com.spynet.camera.network.Mangocam.MangocamAdapter;
import com.spynet.camera.network.StreamConnection;
import com.spynet.camera.network.StreamServer;
import com.spynet.camera.ui.MainActivity;
import com.spynet.camera.ui.SettingsActivity;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.net.BindException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Defines the service that handles clients requests from the network,
 * sending video streams on demand.
 */
public class StreamService extends Service
        implements
        Recorder.RecorderCallback,
        StreamServer.StreamServerCallback,
        MangocamAdapter.MangocamAdapterCallback,
        AngelcamAdapter.AngelcamAdapterCallback,
        ConnectivityMonitor.ConnectivityCallback,
        LocationProvider.LocationCallback {

    public static final String ACTION_STOP_SERVICE = "com.spynet.camera.services.STOP_SERVICE";
    public static final int SHOW_REQUEST_ID = 9536464;
    public static final int STOP_REQUEST_ID = 8465868;

    private static final int FOREGROUND_NOTIFICATION_ID = 314159;

    protected final String TAG = getClass().getSimpleName();

    private final ConcurrentHashMap<Long, String>       // The HashMap where the running streams are stored
            mStreams = new ConcurrentHashMap<>();       //

    private final Object mServerLock = new Object();    // Lock to synchronize access to the server
    private final Object mRecorderLock = new Object();  // Lock to synchronize access to the recorder

    private WeakReference<IStreamServiceCallBack>       // Callback to communicate with the client
            mCallBack;                                  //
    private Recorder mRecorder;                         // The audio/video recorder
    private StreamServer mStreamServer;                 // The stream server
    private MangocamAdapter mMangocamAdapter;           // The Mangocam Connect API adapter
    private AngelcamAdapter mAngelcamAdapter;           // The Angelcam Ready API adapter
    private BroadcastReceiver mControlReceiver;         // The BroadcastReceiver to control the service
    private ConnectivityMonitor mConnectivityMonitor;   // The connectivity monitor
    private LocationProvider mLocationProvider;         // The location provider
    private Location mLocation;                         // Last known location
    private volatile boolean mWiFiAvailable;            // Indicates that the WiFi is available
    private volatile boolean mMobileAvailable;          // Indicates that the mobile data is available

    /**
     * Local-side IPC implementation of the IStreamService interface.
     */
    private final IStreamService.Stub mBinder = new IStreamService.Stub() {
        @Override
        public void registerCallBack(IStreamServiceCallBack cb) throws RemoteException {
            mCallBack = new WeakReference<>(cb);
        }

        @Override
        public void restart(final String what) throws RemoteException {
            boolean wifiOnly = SettingsActivity.getServerWiFiOnly(StreamService.this);
            switch (what) {
                case "StreamServer":
                    synchronized (mServerLock) {
                        // Stop the StreamServer
                        if (mStreamServer != null) {
                            mStreamServer.close();
                            mStreamServer = null;
                        }
                        // Start the StreamServer
                        // Note: restarting the server on the same port may lead in a EADDRINUSE error
                        // because the listening socket takes some time to close completely
                        int serverPort = SettingsActivity.getServerPort(StreamService.this);
                        for (int i = 0, retry = 3; i < retry; i++) {
                            try {
                                mStreamServer = new StreamServer(StreamService.this, serverPort);
                                break;
                            } catch (BindException e) {
                                if (i < retry - 1) {
                                    Log.w(TAG, "cannot create the StreamServer on port " + serverPort +
                                            ": " + e.getMessage());
                                    try {
                                        Thread.sleep(100);
                                    } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                    }
                                } else {
                                    Toast.makeText(StreamService.this,
                                            R.string.error_server_not_started, Toast.LENGTH_LONG).show();
                                    throw new RemoteException(e.getMessage());
                                }
                            } catch (Exception e) {
                                Toast.makeText(StreamService.this,
                                        R.string.error_server_not_started, Toast.LENGTH_LONG).show();
                                throw new RemoteException(e.getMessage());
                            }
                        }
                        // Update StreamServer status
                        mStreamServer.setWiFiAvailable(mWiFiAvailable);
                        mStreamServer.setMobileAvailable(mMobileAvailable && !wifiOnly);
                        mStreamServer.setLocation(mLocation);
                        synchronized (mRecorderLock) {
                            mStreamServer.setH264Available(mRecorder != null && mRecorder.isH264Available());
                            mStreamServer.setAudioAvailable(mRecorder != null && mRecorder.isAudioAvailable());
                            mStreamServer.setTorch(mRecorder != null && mRecorder.getTorch());
                        }
                        // Request the audio/video streams configuration to be sent again
                        synchronized (mRecorderLock) {
                            if (mRecorder != null)
                                mRecorder.requestConfiguration();
                        }
                    }
                    // Update the notification
                    startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification());
                    break;
                case "Recorder":
                    synchronized (mRecorderLock) {
                        // Stop the Recorder
                        if (mRecorder != null) {
                            mRecorder.close();
                            mRecorder = null;
                        }
                        // Start the Recorder
                        try {
                            mRecorder = new Recorder(StreamService.this);
                        } catch (Exception e) {
                            Toast.makeText(StreamService.this,
                                    R.string.error_recorder_not_started, Toast.LENGTH_LONG).show();
                            throw new RemoteException(e.getMessage());
                        }
                        // Update StreamServer status
                        synchronized (mServerLock) {
                            if (mStreamServer != null) {
                                mStreamServer.setH264Available(mRecorder.isH264Available());
                                mStreamServer.setAudioAvailable(mRecorder.isAudioAvailable());
                            }
                        }
                    }
                    // Update the notification
                    startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification());
                    break;
                case "MangocamAdapter":
                    synchronized (mServerLock) {
                        // Stop the MangocamAdapter
                        if (mMangocamAdapter != null) {
                            mMangocamAdapter.close();
                            mMangocamAdapter = null;
                        }
                        // Start the MangocamAdapter
                        if (SettingsActivity.getMangoEnabled(StreamService.this)) {
                            mMangocamAdapter = new MangocamAdapter(StreamService.this);
                            mMangocamAdapter.setWiFiAvailable(mWiFiAvailable);
                            mMangocamAdapter.setMobileAvailable(mMobileAvailable && !wifiOnly);
                        }
                    }
                    // Update the notification
                    startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification());
                    break;
                case "AngelcamAdapter":
                    synchronized (mServerLock) {
                        // Stop the AngelcamAdapter
                        if (mAngelcamAdapter != null) {
                            mAngelcamAdapter.close();
                            mAngelcamAdapter = null;
                        }
                        // Start the AngelcamAdapter
                        if (SettingsActivity.getAngelEnabled(StreamService.this)) {
                            mAngelcamAdapter = new AngelcamAdapter(StreamService.this);
                            mAngelcamAdapter.setWiFiAvailable(mWiFiAvailable);
                            mAngelcamAdapter.setMobileAvailable(mMobileAvailable && !wifiOnly);
                        }
                    }
                    // Update the notification
                    startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification());
                    break;
            }
        }

        @Override
        public int getNumberOfCameras() throws RemoteException {
            synchronized (mRecorderLock) {
                if (mRecorder == null)
                    return 0;
                return mRecorder.getCameraInfo().size();
            }
        }

        @Override
        public CameraInfo getCameraInfo(int index) throws RemoteException {
            synchronized (mRecorderLock) {
                if (mRecorder == null)
                    return null;
                if (index < 0 || index >= mRecorder.getCameraInfo().size())
                    throw new RemoteException("index out of range");
                return mRecorder.getCameraInfo().get(index);
            }
        }

        @Override
        public void setSurface(Surface surface) throws RemoteException {
            synchronized (mRecorderLock) {
                if (mRecorder != null) {
                    try {
                        mRecorder.setSurface(surface);
                    } catch (Exception e) {
                        throw new RemoteException(e.getMessage());
                    }
                }
            }
        }

        @Override
        public boolean isScreenCaptureCamera() throws RemoteException {
            synchronized (mRecorderLock) {
                return mRecorder != null && mRecorder.isScreenCaptureCamera();
            }
        }

        @Override
        public boolean isFrontCamera() throws RemoteException {
            synchronized (mRecorderLock) {
                return mRecorder != null && mRecorder.isFrontCamera();
            }
        }

        @Override
        public Point getFrameSize() throws RemoteException {
            synchronized (mRecorderLock) {
                return mRecorder != null ? mRecorder.getFrameSize() : null;
            }
        }

        @Override
        public float getAverageFrameRate() throws RemoteException {
            synchronized (mRecorderLock) {
                return mRecorder != null ? mRecorder.getAverageFps() : 0;
            }
        }

        @Override
        public void setZoom(float zoom) throws RemoteException {
            synchronized (mRecorderLock) {
                if (mRecorder != null) mRecorder.setZoom(zoom);
            }
        }

        @Override
        public float getZoom() throws RemoteException {
            synchronized (mRecorderLock) {
                return mRecorder != null ? mRecorder.getZoom() : 0.0f;
            }
        }

        @Override
        public void autoFocus(int x, int y) throws RemoteException {
            synchronized (mRecorderLock) {
                if (mRecorder != null)
                    mRecorder.autoFocus(x, y);
            }
        }

        @Override
        public int getVideoBitrate() throws RemoteException {
            synchronized (mRecorderLock) {
                return mRecorder != null ? mRecorder.getH264Bitrate() : 0;
            }
        }

        @Override
        public int getAudioBitrate() throws RemoteException {
            synchronized (mRecorderLock) {
                return mRecorder != null ? mRecorder.getAudioBitrate() : 0;
            }
        }

        @Override
        public int getNumberOfStreams() throws RemoteException {
            return mStreams.size();
        }

        @Override
        public int getNumberOfAudioStreams() throws RemoteException {
            int audio = 0;
            for (String t : mStreams.values()) {
                if (t.equals(StreamConnection.TYPE_AAC)) audio++;
            }
            return audio;
        }

        @Override
        public void setGain(double gain) throws RemoteException {
            synchronized (mRecorderLock) {
                if (mRecorder != null)
                    mRecorder.setGain(gain);
            }
        }

        @Override
        public void setMute(boolean mute) throws RemoteException {
            synchronized (mRecorderLock) {
                if (mRecorder != null)
                    mRecorder.setMute(mute);
            }
        }

        @Override
        public boolean getMute() throws RemoteException {
            synchronized (mRecorderLock) {
                return mRecorder == null || mRecorder.getMute();
            }
        }

        @Override
        public boolean isH264Available() throws RemoteException {
            synchronized (mRecorderLock) {
                return mRecorder != null && mRecorder.isH264Available();
            }
        }

        @Override
        public boolean isMJPEGAvailable() throws RemoteException {
            synchronized (mRecorderLock) {
                return mRecorder != null && mRecorder.isMJPEGAvailable();
            }
        }

        @Override
        public boolean isScreenCaptureAvailable() throws RemoteException {
            synchronized (mRecorderLock) {
                return mRecorder != null && mRecorder.isScreenCaptureAvailable();
            }
        }

        @Override
        public boolean isMangocamConnected() throws RemoteException {
            synchronized (mServerLock) {
                return mMangocamAdapter != null && mMangocamAdapter.isConnected();
            }
        }

        @Override
        public boolean isAngelcamConnected() throws RemoteException {
            synchronized (mServerLock) {
                return mAngelcamAdapter != null && mAngelcamAdapter.isConnected();
            }
        }
    };

    /**
     * Factory method that makes an explicit intent used to start the
     * StreamService when passed to bindService().
     *
     * @param context the context in which the server will run
     * @return the created Intent
     */
    @NotNull
    public static Intent MakeIntent(Context context) {
        return new Intent(context, StreamService.class);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Start the StreamServer
        int serverPort = SettingsActivity.getServerPort(this);
        try {
            mStreamServer = new StreamServer(this, serverPort);
        } catch (Exception e) {
            Toast.makeText(this, R.string.error_server_not_started, Toast.LENGTH_LONG).show();
            Log.e(TAG, "cannot create the StreamServer on port " + serverPort, e);
        }

        // Start the MangocamAdapter
        if (SettingsActivity.getMangoEnabled(this)) {
            mMangocamAdapter = new MangocamAdapter(this);
        }

        // Start the AngelcamAdapter
        if (SettingsActivity.getAngelEnabled(this)) {
            mAngelcamAdapter = new AngelcamAdapter(this);
        }

        // Start the Recorder
        try {
            mRecorder = new Recorder(this);
        } catch (Exception e) {
            Toast.makeText(this, R.string.error_recorder_not_started, Toast.LENGTH_LONG).show();
            Log.e(TAG, "cannot create the Recorder", e);
        }
        if (mRecorder != null && mStreamServer != null) {
            mStreamServer.setH264Available(mRecorder.isH264Available());
            mStreamServer.setAudioAvailable(mRecorder.isAudioAvailable());
        }

        // Register the ConnectivityMonitor
        mConnectivityMonitor = new ConnectivityMonitor(this);

        // Start the LocationProvider
        mLocationProvider = new LocationProvider(this);

        // Register the BroadcastReceiver to control the service
        IntentFilter filter = new IntentFilter(ACTION_STOP_SERVICE);
        mControlReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(ACTION_STOP_SERVICE))
                    stopSelf();
            }
        };
        registerReceiver(mControlReceiver, filter);

        // Make this service run in the foreground
        startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Stop running this service in the foreground
        stopForeground(true);

        // Unregister the BroadcastReceiver to control the service
        if (mControlReceiver != null)
            unregisterReceiver(mControlReceiver);

        // Stop the LocationProvider
        if (mLocationProvider != null)
            mLocationProvider.close();

        // Stop the ConnectivityMonitor
        if (mConnectivityMonitor != null)
            mConnectivityMonitor.close();

        // Stop the Recorder
        if (mRecorder != null)
            mRecorder.close();

        // Stop the AngelcamAdapter
        if (mAngelcamAdapter != null)
            mAngelcamAdapter.close();

        // Stop the MangocamAdapter
        if (mMangocamAdapter != null)
            mMangocamAdapter.close();

        // Stop the StreamServer
        if (mStreamServer != null)
            mStreamServer.close();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Helper to build the Notification used by the foreground service.
     */
    private Notification buildNotification() {
        String description;
        int icon = R.drawable.ic_videocam;
        synchronized (mRecorderLock) {
            // Default information
            String ip = Utils.getIPAddress(this);
            int port = SettingsActivity.getServerPort(this);
            if (mRecorder != null && ip != null) {
                description = "http://" + ip + ":" + port + "/";
                if (mRecorder.isMJPEGAvailable())
                    description += "\nhttp://" + ip + ":" + port + "/video/mjpeg";
                if (mRecorder.isH264Available())
                    description += "\nrtsp://" + ip + ":" + port + "/video/h264";
            } else {
                description = getString(R.string.app_service_running);
            }
            // Warning messages
            if (mRecorder != null && !mRecorder.isCameraAvailable()) {
                description += "\n" + getString(R.string.warning_camera_not_available);
                icon = R.drawable.ic_warning;
            }
            if (mRecorder != null && !mRecorder.isH264Available()) {
                description += "\n" + getString(R.string.warning_h264_not_available);
                icon = R.drawable.ic_warning;
            }
            if (mRecorder != null && !mRecorder.isAudioAvailable() && SettingsActivity.getAACEnabled(this)) {
                description += "\n" + getString(R.string.warning_audio_not_available);
                icon = R.drawable.ic_warning;
            }
            if (mRecorder != null && !mRecorder.isScreenCaptureAvailable() && SettingsActivity.getCameraIndex(this) == -1) {
                description += "\n" + getString(R.string.warning_screen_capture_not_available);
                icon = R.drawable.ic_warning;
            }
            // Error messages
            if (mStreamServer == null) {
                description += "\n" + getString(R.string.error_server_not_started);
                icon = R.drawable.ic_error;
            }
            if (mRecorder == null) {
                description += "\n" + getString(R.string.error_recorder_not_started);
                icon = R.drawable.ic_error;
            }
        }
        // Build the notification
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("id", SHOW_REQUEST_ID);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                SHOW_REQUEST_ID, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        Intent stopIntent = new Intent(ACTION_STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(this,
                STOP_REQUEST_ID, stopIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setAutoCancel(false)
                .setOngoing(true)
                .setColor(0x546E7A) // the primary color
                .setContentIntent(pendingIntent)
                .setTicker(getString(R.string.app_service_name))
                .setContentTitle(getString(R.string.app_service_name))
                .setContentText(getString(R.string.app_service_running))
                .setSmallIcon(icon)
                .addAction(R.drawable.ic_stop, getString(R.string.app_service_action_stop), stopPendingIntent)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(description));
        // Return the Notification
        return builder.build();
    }

    @Override
    public void onStreamStarted(String type, long id) {
        int streams = 0;
        int audio = 0;
        // Add the stream to the list
        synchronized (mStreams) {
            if (mStreams.putIfAbsent(id, type) == null) {
                streams = mStreams.size();
                for (String t : mStreams.values()) {
                    if (t.equals(StreamConnection.TYPE_AAC)) audio++;
                }
            }
        }
        // Notify the client
        IStreamServiceCallBack cb = (mCallBack != null ? mCallBack.get() : null);
        if (cb != null) {
            try {
                if (streams == 1)
                    cb.onStreamingStarted();
                if (audio == 1)
                    cb.onAudioStarted();
            } catch (RemoteException e) {
                Log.e(TAG, "failed to notify stream started", e);
            }
        }
    }

    @Override
    public void onStreamStopped(String type, long id) {
        int streams = 0;
        int audio = 0;
        // Remove the stream from the list
        synchronized (mStreams) {
            if (mStreams.remove(id) != null) {
                streams = mStreams.size();
                for (String t : mStreams.values()) {
                    if (t.equals(StreamConnection.TYPE_AAC)) audio++;
                }
            }
        }
        // Notify the client
        IStreamServiceCallBack cb = (mCallBack != null ? mCallBack.get() : null);
        if (cb != null) {
            try {
                if (streams == 0)
                    cb.onStreamingStopped();
                if (audio == 0)
                    cb.onAudioStopped();
            } catch (RemoteException e) {
                Log.e(TAG, "failed to notify stream stopped", e);
            }
        }
    }

    @Override
    public void onAdapterConnected(String adapter, String host) {
        // Notify the client that an adapter has connected
        IStreamServiceCallBack cb = (mCallBack != null ? mCallBack.get() : null);
        if (cb != null) {
            try {
                cb.onAdapterConnected(adapter);
            } catch (RemoteException e) {
                Log.e(TAG, "failed to notify adapter connected", e);
            }
        }
    }

    @Override
    public void onAdapterDisconnected(String adapter) {
        // Notify the client that an adapter has disconnected
        IStreamServiceCallBack cb = (mCallBack != null ? mCallBack.get() : null);
        if (cb != null) {
            try {
                cb.onAdapterDisconnected(adapter);
            } catch (RemoteException e) {
                Log.e(TAG, "failed to notify adapter disconnected", e);
            }
        }
    }

    @Override
    public void onControlRequest(String action, String params) {
        if (action == null || params == null)
            return;
        // Handle control requests
        Log.v(TAG, "onControlRequest: " + action + " (" + params + ")");
        switch (action) {
            // Control the camera zoom
            case "zoom":
                synchronized (mRecorderLock) {
                    if (mRecorder != null) {
                        if (params.startsWith("+")) {
                            // Increment
                            float zoom = mRecorder.getZoom();
                            float inc = Utils.tryParseInt(params.substring(1), 0) / 100f;
                            mRecorder.setZoom(zoom * (1 + inc));
                        } else if (params.startsWith("-")) {
                            // Decrement
                            float zoom = mRecorder.getZoom();
                            float dec = Utils.tryParseInt(params.substring(1), 0) / 100f;
                            mRecorder.setZoom(zoom * (1 - dec));
                        } else {
                            // Absolute
                            float zoom = Utils.tryParseInt(params, 1) / 100f;
                            mRecorder.setZoom(zoom);
                        }
                        SettingsActivity.setCameraZoom(this, mRecorder.getZoom());
                    }
                }
                break;
            // Start autofocus
            case "autofocus":
                synchronized (mRecorderLock) {
                    if (mRecorder != null) {
                        if (params.equals("start")) {
                            mRecorder.autoFocus(0, 0);
                        } else if (params.matches("([+-]*[0-9])+,([+-]*[0-9])+")) {
                            String[] coords = params.split(",");
                            if (coords.length == 2) {
                                int x = Utils.tryParseInt(coords[0], 0);
                                int y = Utils.tryParseInt(coords[1], 0);
                                mRecorder.autoFocus(x, y);
                            }
                        }
                    }
                }
                break;
            // Turn torch on/off
            case "torch":
                synchronized (mRecorderLock) {
                    if (mRecorder != null) {
                        switch (params) {
                            case "on":
                                mRecorder.setTorch(true);
                                break;
                            case "off":
                                mRecorder.setTorch(false);
                                break;
                            case "toggle":
                                boolean torch = mRecorder.getTorch();
                                mRecorder.setTorch(!torch);
                                break;
                        }
                    }
                }
                break;
            // Request a sync frame
            case "video-sync":
                synchronized (mRecorderLock) {
                    if (mRecorder != null) {
                        if (params.equals("send")) {
                            mRecorder.requestSyncFrame();
                        }
                    }
                }
                break;
            // Wait for the specified time (useful when using multiple commands on the same request)
            case "delay":
                int delay = Utils.tryParseInt(params, 0);
                if (delay > 0 && delay <= 1000) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        Log.w(TAG, "delay interrupted");
                        Thread.currentThread().interrupt();
                    }
                }
                break;
            // Change the GPS mode
            case "gps-mode":
                if (mLocationProvider != null) {
                    if (params.equals("fine"))
                        mLocationProvider.requestFineUpdates();
                    else if (params.equals("coarse"))
                        mLocationProvider.requestCoarseUpdates();
                }
                break;
            // Unhandled
            default:
                Log.w(TAG, "unhandled control request: " + action + " (" + params + ")");
        }
    }

    @Override
    public void onLog(int code, String message) {
        // Send the log information to the client
        IStreamServiceCallBack cb = (mCallBack != null ? mCallBack.get() : null);
        if (cb != null) {
            try {
                cb.onLog(code, message);
            } catch (RemoteException e) {
                Log.e(TAG, "failed to notify log message", e);
            }
        }
    }

    @Override
    public void onDataAvailable(VideoFrame frame) {
        // Forward the video frame (compressed or uncompressed)
        synchronized (mServerLock) {
            try {
                if (mStreamServer != null)
                    mStreamServer.push(frame);
                if (mMangocamAdapter != null)
                    mMangocamAdapter.push(frame);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e(TAG, "push frame interrupted");
            }
        }
    }

    @Override
    public void onDataAvailable(AudioData data) {
        // Forward the audio data
        synchronized (mServerLock) {
            try {
                if (mStreamServer != null)
                    mStreamServer.push(data);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e(TAG, "push audio interrupted");
            }
        }
    }

    @Override
    public void onFrameSizeChanged(Point size) {
        // Send the frame size to the client
        IStreamServiceCallBack cb = (mCallBack != null ? mCallBack.get() : null);
        if (cb != null) {
            try {
                cb.onFrameSizeChanged(size);
            } catch (RemoteException e) {
                Log.e(TAG, "failed to notify frame size", e);
            }
        }
    }

    @Override
    public void onBitrateChanged(int video, int audio) {
        // Send the bitrates to the client
        IStreamServiceCallBack cb = (mCallBack != null ? mCallBack.get() : null);
        if (cb != null) {
            try {
                cb.onBitrateChanged(video, audio);
            } catch (RemoteException e) {
                Log.e(TAG, "failed to notify bitrate", e);
            }
        }
    }

    @Override
    public void onZoom(float zoom) {
        // Send the zoom to the client
        IStreamServiceCallBack cb = (mCallBack != null ? mCallBack.get() : null);
        if (cb != null) {
            try {
                cb.onZoom(zoom);
            } catch (RemoteException e) {
                Log.e(TAG, "failed to notify zoom", e);
            }
        }
    }

    @Override
    public void onTorch(boolean state) {
        // Send the zoom to the client
        IStreamServiceCallBack cb = (mCallBack != null ? mCallBack.get() : null);
        if (cb != null) {
            try {
                cb.onTorch(state);
            } catch (RemoteException e) {
                Log.e(TAG, "failed to notify torch", e);
            }
        }
        // Forward the torch status
        synchronized (mServerLock) {
            if (mStreamServer != null)
                mStreamServer.setTorch(state);
        }
    }

    @Override
    public void onMute(boolean mute) {
        // Send the mute state to the client
        IStreamServiceCallBack cb = (mCallBack != null ? mCallBack.get() : null);
        if (cb != null) {
            try {
                cb.onMute(mute);
            } catch (RemoteException e) {
                Log.e(TAG, "failed to notify mute", e);
            }
        }
    }

    @Override
    public void onFrameRate(float fps) {
        // Send the frame rate to the client
        IStreamServiceCallBack cb = (mCallBack != null ? mCallBack.get() : null);
        if (cb != null) {
            try {
                cb.onFrameRate(fps);
            } catch (RemoteException e) {
                Log.e(TAG, "failed to notify frame rate", e);
            }
        }
    }

    @Override
    public void onScreenCapture(boolean authorized) {
        // Forward the notification to the client
        IStreamServiceCallBack cb = (mCallBack != null ? mCallBack.get() : null);
        if (cb != null) {
            try {
                cb.onScreenCapture(authorized);
            } catch (RemoteException e) {
                Log.e(TAG, "failed to notify screen capture", e);
            }
        }
        // Update the notification
        startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification());
    }

    @Override
    public void onLocationAvailable(Location location) {
        // Store the location locally
        mLocation = location;
        // Forward the location
        synchronized (mServerLock) {
            if (mStreamServer != null)
                mStreamServer.setLocation(location);
        }
    }

    @Override
    public void onWiFiAvailableChange(boolean available) {
        // Store the WiFi status locally
        mWiFiAvailable = available;
        // Forward the WiFi status
        synchronized (mServerLock) {
            if (mStreamServer != null)
                mStreamServer.setWiFiAvailable(available);
            if (mMangocamAdapter != null)
                mMangocamAdapter.setWiFiAvailable(available);
            if (mAngelcamAdapter != null)
                mAngelcamAdapter.setWiFiAvailable(available);
        }
        // Update the notification
        startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification());
    }

    @Override
    public void onMobileAvailableChange(boolean available) {
        // Store the mobile data status locally
        mMobileAvailable = available;
        // Forward the mobile data status
        // Note: if the 'use WiFi only' setting is set, we assume that the mobile data
        //       is not available
        boolean wifiOnly = SettingsActivity.getServerWiFiOnly(StreamService.this);
        synchronized (mServerLock) {
            if (mStreamServer != null)
                mStreamServer.setMobileAvailable(available && !wifiOnly);
            if (mMangocamAdapter != null)
                mMangocamAdapter.setMobileAvailable(available && !wifiOnly);
            if (mAngelcamAdapter != null)
                mAngelcamAdapter.setMobileAvailable(available && !wifiOnly);
        }
        // Update the notification
        startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification());
    }
}
