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

package com.spynet.camera.media;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.hardware.display.VirtualDisplay;
import android.media.CamcorderProfile;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;

import com.spynet.camera.R;
import com.spynet.camera.common.Image;
import com.spynet.camera.ui.ScreenCaptureRequestActivity;
import com.spynet.camera.ui.SettingsActivity;

import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines the audio/video recorder.
 */
@SuppressWarnings("deprecation")
public class Recorder
        implements
        Closeable,
        LiveCamera.FrameCallback,
        AudioRecorder.AudioCallback,
        VideoCodec.CodecCallback,
        AudioCodec.CodecCallback {

    // Action to start the screen capture after user authorization
    public static final String ACTION_START_SCREEN_CAPTURE = "com.spynet.camera.media.START_SCREEN_CAPTURE";
    // Extra tag to retrieve the underlying MediaProjection Intent
    public static final String EXTRA_MEDIA_PROJECTION_INTENT = "com.spynet.camera.media.EXTRA_MEDIA_PROJECTION_INTENT";
    // Extra tag to retrieve the underlying MediaProjection authorization request result code
    public static final String EXTRA_MEDIA_PROJECTION_RESULT = "com.spynet.camera.media.EXTRA_MEDIA_PROJECTION_RESULT";
    // Perform autofocus on app start
    private final static boolean INITIAL_AUTOFOCUS = false;
    // Delay in milliseconds after the camera has started to perform autofocus
    private final static int AUTOFOCUS_DELAY = 2000;
    // Time interval at which the fps is updated onn the overlay, in milliseconds
    private final static int FPS_UPDATE_TIME = 5000;

    private final String TAG = getClass().getSimpleName();

    private final Context mContext;                     // The context that uses the Recorder
    private final ArrayList<CameraInfo> mCameraInfo;    // The information of all the available cameras
    private RecorderCallback mCallback;                 // The RecorderCallback implemented by mContext
    private BroadcastReceiver mScreenCaptureReceiver;   // The BroadcastReceiver to receive screen capture authorization
    private Camera mCamera;                             // LiveCamera that will generate the video stream
    private AudioRecorder mAudioRecorder;               // AudioRecorder that will generate the audio stream
    private VideoEncoder mVideoEncoder;                 // Encoder to encode the video
    private AudioEncoder mAudioEncoder;                 // Encoder to encode the audio
    private MediaProjection mMediaProjection;           // MediaProjection to capture the screen
    private VirtualDisplay mVirtualDisplay;             // VirtualDisplay to capture the screen
    private Point mFrameSize;                           // Video frame size
    private int mFrameFormat;                           // Video frame pixel format
    private long mFrameInterval;                        // Interval between the frames sent to the video encoder in microseconds
    private long mLastTimestamp;                        // Timestamp of the last frame sent to the video encoder
    private long mLastFpsUpdate;                        // Last timestamp when fps was updated
    private volatile boolean mVideoCfgSent;             // Whether the video configuration has been sent (SPS and PPS)
    private volatile boolean mAudioCfgSent;             // Whether the audio configuration has been sent


    /**
     * A client may implement this interface to receive audio and video data buffers
     * as they are available.
     */
    public interface RecorderCallback {
        /**
         * Called when new video frame is available.
         * May be called from different threads.
         *
         * @param frame the video frame
         */
        void onDataAvailable(VideoFrame frame);

        /**
         * Called when new audio data is available.
         * May be called from different threads.
         *
         * @param data the audio data
         */
        void onDataAvailable(AudioData data);

        /**
         * Notifies that the frame size changed.
         *
         * @param size the frame size in pixels
         */
        void onFrameSizeChanged(Point size);

        /**
         * Notifies that the bitrate changed.
         *
         * @param video the video bitrate
         * @param audio the audio bitrate
         */
        void onBitrateChanged(int video, int audio);

        /**
         * Called when a new zoom factor has set.
         *
         * @param zoom the new zoom factor
         */
        void onZoom(float zoom);

        /**
         * Called when the torch state changes.
         *
         * @param on {@code true} if the torch is on, {@code false} if it is off
         */
        void onTorch(boolean on);

        /**
         * Called when a new mute state has set.
         *
         * @param mute the new mute state
         */
        void onMute(boolean mute);

        /**
         * Called when a new frame rate vaue is available.
         *
         * @param fps the new average frame rate
         */
        void onFrameRate(float fps);

        /**
         * Called when the screen capture has been denied or authorized by the user.
         *
         * @param authorized whether the screen capture has been authorized by the user
         */
        void onScreenCapture(boolean authorized);
    }

    /**
     * Creates a new Recorder object.<br>
     * We need the camera but the audio source and the audio/video encoders may or may not
     * be available, depending on resource availability and permissions.
     *
     * @param context the context where the Recorder is used; it should implement
     *                {@link RecorderCallback}
     */
    public Recorder(@NotNull Context context) throws IOException {

        Point preferredImageSize = new Point(640, 480);
        int preferredAudioSampleRate = 44100;

        // Store the client Context
        mContext = context;
        if (mContext instanceof RecorderCallback) {
            mCallback = (RecorderCallback) mContext;
        } else {
            Log.w(TAG, "RecorderCallback is not implemented by the specified context");
        }

        // Read the information of all the available cameras
        mCameraInfo = new ArrayList<>(readCameraInfo(mContext));

        // Read preferences
        int cameraId = SettingsActivity.getCameraIndex(mContext);
        int[] resolution = SettingsActivity.getVideoResolution(mContext);
        int bitrate = SettingsActivity.getH264Bitrate(mContext);
        int fps = SettingsActivity.getH264FrameSpeed(mContext);
        int distance = SettingsActivity.getH264IDistance(mContext);
        float zoom = SettingsActivity.getCameraZoom(mContext);

        // Create the Camera object
        try {
            if (cameraId == -1)
                mCamera = new ScreenCaptureCamera(this);
            else
                mCamera = new LiveCamera(this, cameraId);
        } catch (Exception e) {
            Log.e(TAG, "cannot access the camera", e);
        }

        // Get the preferred settings
        if (mCamera != null) {
            if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_HIGH)) {
                CamcorderProfile profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
                preferredImageSize = new Point(profile.videoFrameWidth, profile.videoFrameHeight);
                preferredAudioSampleRate = profile.audioSampleRate;
            } else if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_LOW)) {
                CamcorderProfile profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW);
                preferredImageSize = new Point(profile.videoFrameWidth, profile.videoFrameHeight);
                preferredAudioSampleRate = profile.audioSampleRate;
            }
        }

        // Create the VideoEncoder object
        if (mCamera != null) {
            try {
                mVideoEncoder = new VideoEncoder(this);
            } catch (Exception e) {
                Log.e(TAG, "unable to create the video encoder for MIME " + VideoEncoder.getMIME(), e);
            }
        }

        // Try to open the camera and the encoder
        // We may need to try different settings to match the encoder requirements
        for (int n = 0; mCamera != null && mVideoEncoder != null; n++) {

            // Open the camera
            mCamera.open(resolution[0], resolution[1], ImageFormat.NV21);
            mCamera.setZoom(zoom);
            mFrameSize = mCamera.getFrameSize();
            mFrameFormat = mCamera.getFrameFormat();
            if (INITIAL_AUTOFOCUS) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mCamera != null)
                            mCamera.autoFocus(0, 0);
                    }
                }, AUTOFOCUS_DELAY);
            }

            // Open the video encoder
            int format;
            if (mVideoEncoder.supportsColorFormat(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)) {
                format = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
            } else if (mVideoEncoder.supportsColorFormat(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar)) {
                format = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
            } else {
                mVideoEncoder = null;
                break;
            }
            try {
                mVideoEncoder.open(
                        mFrameSize.x, mFrameSize.y, format,
                        fps, bitrate, distance);
                mFrameInterval = 1000000 / fps;
            } catch (Exception e) {
                if (n == 0) {
                    // Try default encoder settings for the best available resolution
                    // Note: these are not actually the best available settings
                    resolution = new int[]{preferredImageSize.x, preferredImageSize.y};
                    Log.w(TAG, "video encoder failed to start: trying to use default encoder settings");
                    // Reset the encoder because it doesn't like to be restarted
                    try {
                        mVideoEncoder.release();
                        mVideoEncoder = new VideoEncoder(this);
                        continue;
                    } catch (Exception e1) {
                        Log.e(TAG, "cannot reset the video encoder", e1);
                    }
                }
                mVideoEncoder = null;
                break;
            }

            // No more attempts
            break;
        }

        // Ask the user to authorize the screen capture
        if (mCamera != null && mCamera instanceof ScreenCaptureCamera) {
            // Register the BroadcastReceiver to control the service
            IntentFilter filter = new IntentFilter(ACTION_START_SCREEN_CAPTURE);
            mScreenCaptureReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    synchronized (Recorder.this) {
                        // Unregister self
                        context.unregisterReceiver(mScreenCaptureReceiver);
                        mScreenCaptureReceiver = null;
                        // Start screen capture
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            Intent mpIntent = intent.getParcelableExtra(EXTRA_MEDIA_PROJECTION_INTENT);
                            int resultCode = intent.getIntExtra(EXTRA_MEDIA_PROJECTION_RESULT, Activity.RESULT_CANCELED);
                            MediaProjectionManager mpManager =
                                    (MediaProjectionManager) context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE);
                            if (mpManager != null) {
                                mMediaProjection = mpManager.getMediaProjection(resultCode, mpIntent);
                                if (mMediaProjection != null) {
                                    mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                                            "screen_capture_display",
                                            mFrameSize.x, mFrameSize.y, DisplayMetrics.DENSITY_HIGH,
                                            0, mCamera.getSurface(), null, null);
                                    if (mCallback != null)
                                        mCallback.onScreenCapture(true);
                                } else {
                                    if (mCallback != null)
                                        mCallback.onScreenCapture(false);
                                }
                            } else {
                                Log.e(TAG, "can't start screen capture, Media Projection Service not available");
                            }
                        } else {
                            Log.e(TAG, "can't start screen capture, requires API " + Build.VERSION_CODES.LOLLIPOP);
                        }
                    }
                }
            };
            mContext.registerReceiver(mScreenCaptureReceiver, filter);
            // Start the Activity to prompt the user
            Intent intent = ScreenCaptureRequestActivity.MakeIntent(mContext);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        }

        // Create the AudioRecorder object
        if (SettingsActivity.getAACEnabled(mContext)) {
            try {
                mAudioRecorder = new AudioRecorder(this,
                        preferredAudioSampleRate,
                        MediaRecorder.AudioSource.MIC);
            } catch (Exception e) {
                Log.e(TAG, "unable to access the microphone", e);
            }
        }

        // Create the AudioEncoder object
        if (mAudioRecorder != null) {
            try {
                mAudioEncoder = new AudioEncoder(this);
            } catch (Exception e) {
                Log.e(TAG, "unable to create the audio encoder for MIME " + AudioEncoder.getMIME(), e);
            }
        }

        // Start the audio recorder and encoder
        try {
            if (mAudioRecorder != null && mAudioEncoder != null) {
                mAudioRecorder.setGain(SettingsActivity.getAACGain(mContext));
                mAudioRecorder.open();
                mAudioEncoder.open(
                        SettingsActivity.getAACBitrate(mContext),
                        mAudioRecorder.getSampleRate(),
                        mAudioRecorder.getChannelCount(),
                        mAudioRecorder.getBufferSize());
            }
        } catch (Exception e) {
            Log.e(TAG, "audio encoder failed to start", e);
        }

        // Save the preferences that may have been forced by the camera or the encoder
        if (mFrameSize != null)
            SettingsActivity.setVideoResolution(mContext, mFrameSize.x, mFrameSize.y);
        if (mVideoEncoder != null)
            SettingsActivity.setH264Bitrate(mContext, mVideoEncoder.getBitrate());
        if (mAudioEncoder != null)
            SettingsActivity.setAACBitrate(mContext, mAudioEncoder.getBitrate());

        // Notify the initial state to the client
        if (mCallback != null) {
            mCallback.onBitrateChanged(
                    mVideoEncoder != null ? mVideoEncoder.getBitrate() : 0,
                    mAudioEncoder != null ? mAudioEncoder.getBitrate() : 0);
            if (mCamera != null) {
                mCallback.onFrameSizeChanged(mCamera.getFrameSize());
                mCallback.onFrameRate(mCamera.getAverageFps());
                mCallback.onZoom(mCamera.getZoom());
            }
            if (mAudioRecorder != null) {
                mCallback.onMute(mAudioRecorder.getMute());
            }
        }
    }

    /**
     * Closes the Recorder.
     */
    @Override
    public synchronized void close() {

        // Unregister the BroadcastReceiver to receive screen capture authorization
        if (mScreenCaptureReceiver != null) {
            mContext.unregisterReceiver(mScreenCaptureReceiver);
            mScreenCaptureReceiver = null;
        }

        // Shutdown MediaProjection
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (mVirtualDisplay != null) {
                mVirtualDisplay.release();
                mVirtualDisplay = null;
            }
            if (mMediaProjection != null) {
                mMediaProjection.stop();
                mMediaProjection = null;
            }
        }

        // Shutdown the camera
        if (mCamera != null) {
            mCamera.close();
            mCamera.release();
            mCamera = null;
        }

        // Shutdown the video encoder
        if (mVideoEncoder != null) {
            mVideoEncoder.close();
            mVideoEncoder.release();
            mVideoEncoder = null;
        }

        // Shutdown the audio recorder
        if (mAudioRecorder != null) {
            mAudioRecorder.close();
            mAudioRecorder.release();
            mAudioRecorder = null;
        }

        // Shutdown the audio encoder
        if (mAudioEncoder != null) {
            mAudioEncoder.close();
            mAudioEncoder.release();
            mAudioEncoder = null;
        }
    }

    /**
     * Updates the information of all the available cameras.
     * If available, the screen capture virtual camera is added as well.
     */
    private List<CameraInfo> readCameraInfo(Context context) {
        ArrayList<CameraInfo> infos = new ArrayList<>();
        for (int i = 0; i < android.hardware.Camera.getNumberOfCameras(); i++) {
            android.hardware.Camera.CameraInfo cameraInfo = new android.hardware.Camera.CameraInfo();
            android.hardware.Camera.getCameraInfo(i, cameraInfo);
            String name = String.format(context.getString(R.string.pref_camera_name), i);
            if (cameraInfo.facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT)
                name += String.format(" (%s)", context.getString(R.string.pref_camera_front));
            else
                name += String.format(" (%s)", context.getString(R.string.pref_camera_back));
            infos.add(new CameraInfo(name, i, getSupportedPreviewSizes(i)));
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            infos.add(new CameraInfo(context.getString(R.string.pref_camera_name_screen_capture),
                    -1, ScreenCaptureCamera.getSupportedSizes()));
        }
        return infos;
    }

    /**
     * @return the list of all the supported preview resolutions for the specified camera.
     */
    private List<Point> getSupportedPreviewSizes(int cameraId) {
        ArrayList<Point> sizes = null;
        android.hardware.Camera camera = null;
        try {
            camera = android.hardware.Camera.open(cameraId);
            if (camera != null) {
                sizes = new ArrayList<>();
                for (android.hardware.Camera.Size s : camera.getParameters().getSupportedPreviewSizes()) {
                    sizes.add(new Point(s.width, s.height));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "can't access the camera " + cameraId, e);
        } finally {
            if (camera != null)
                camera.release();
        }
        return sizes;
    }

    /**
     * @return the information on all the available cameras
     */
    public synchronized List<CameraInfo> getCameraInfo() {
        return mCameraInfo;
    }

    /**
     * Starts the preview on a different {@link Surface}.<br>
     * If {@code surface} is {@code null}, the stream will be rendered offscreen.
     *
     * @param surface the {@link Surface} on which to render the stream,
     *                {@code null} to render the stream offscreen
     * @throws IOException if setting the surface fails
     */
    public synchronized void setSurface(Surface surface) throws IOException {
        if (mCamera != null) mCamera.setSurface(surface);
    }

    /**
     * @return {@code true} if a front camera is used, {@code false} otherwise
     */
    public synchronized boolean isFrontCamera() {
        return mCamera != null && mCamera.isFront();
    }

    /**
     * @return {@code true} if the screenn capture virtual camera is used, {@code false} otherwise
     */
    public synchronized boolean isScreenCaptureCamera() {
        return mCamera != null && mCamera instanceof ScreenCaptureCamera;
    }

    /**
     * @return the current frame size, {@code null} if not available
     */
    public synchronized Point getFrameSize() {
        return mCamera != null ? mCamera.getFrameSize() : null;
    }

    /**
     * @return the average frame rate in frames per second
     */
    public synchronized float getAverageFps() {
        return mCamera != null ? mCamera.getAverageFps() : 0.0f;
    }

    /**
     * @return the current zoom factor
     */
    public synchronized float getZoom() {
        return mCamera != null ? mCamera.getZoom() : 1.0f;
    }

    /**
     * Sets the camera zoom.
     *
     * @param value zoom factor
     */
    public synchronized void setZoom(float value) {
        if (mCamera != null) {
            mCamera.setZoom(value);
            if (mCallback != null)
                mCallback.onZoom(mCamera.getZoom());
        }
    }

    /**
     * Starts camera auto-focus.
     *
     * @param x the horizontal coordinate of the focus area, in the range -1000 to 1000.
     * @param y the vertical coordinate of the focus area, in the range -1000 to 1000.
     */
    public synchronized void autoFocus(int x, int y) {
        if (mCamera != null) mCamera.autoFocus(x, y);
    }

    /**
     * @return {@code true} if the torch is on, {@code false} if it is off
     */
    public synchronized boolean getTorch() {
        return mCamera != null && mCamera.getTorch();
    }

    /**
     * Sets the torch on or off.
     *
     * @param state {@code true} to turn the torch on, {@code false} to turn it off
     */
    public synchronized void setTorch(boolean state) {
        if (mCamera != null) {
            mCamera.setTorch(state);
            if (mCallback != null)
                mCallback.onTorch(mCamera.getTorch());
        }
    }

    /**
     * @return the current audio mute state
     */
    public synchronized boolean getMute() {
        return mAudioRecorder == null || mAudioRecorder.getMute();
    }

    /**
     * Sets the audio mute state.
     *
     * @param mute mute state
     */
    public synchronized void setMute(boolean mute) {
        if (mAudioRecorder != null) {
            mAudioRecorder.setMute(mute);
            if (mCallback != null)
                mCallback.onMute(mAudioRecorder.getMute());
        }
    }

    /**
     * Sets the audio gain.
     *
     * @param gain the audio gain in dB
     */
    public synchronized void setGain(double gain) {
        if (mAudioRecorder != null) mAudioRecorder.setGain(gain);
    }

    /**
     * @return {@code true} if the camera is available, {@code false} otherwise
     */
    public synchronized boolean isCameraAvailable() {
        return mCamera != null;
    }

    /**
     * @return {@code true} if the screen capture is available, {@code false} otherwise
     */
    public synchronized boolean isScreenCaptureAvailable() {
        return mMediaProjection != null && mVirtualDisplay != null;
    }

    /**
     * @return {@code true} if the H264 encoder is available, {@code false} otherwise
     */
    public synchronized boolean isH264Available() {
        return mCamera != null && mVideoEncoder != null;
    }

    /**
     * Whether the uncompressed video frames are available.
     *
     * @return {@code true} if the MJPEG encoder is available, {@code false} otherwise
     */
    public synchronized boolean isMJPEGAvailable() {
        return mCamera != null;
    }

    /**
     * @return the H264 bitrate
     */
    public synchronized int getH264Bitrate() {
        return mVideoEncoder != null ? mVideoEncoder.getBitrate() : 0;
    }

    /**
     * Requests a reference frame to be generate as soon as possible.
     */
    public synchronized void requestSyncFrame() {
        if (mVideoEncoder != null) mVideoEncoder.requestSyncFrame();
    }

    /**
     * @return {@code true} if the audio is available, {@code false} otherwise
     */
    public synchronized boolean isAudioAvailable() {
        return mAudioEncoder != null;
    }

    /**
     * @return the AAC bitrate
     */
    public synchronized int getAudioBitrate() {
        return mAudioEncoder != null ? mAudioEncoder.getBitrate() : 0;
    }

    /**
     * Requests the configuration information (SPS, PPS and audio configuration) to be sent.
     */
    public synchronized void requestConfiguration() {
        mVideoCfgSent = mAudioCfgSent = false;
    }

    @Override
    public void onFrameAvailable(byte[] data, long timestamp) {
        // Forward to the client
        if (mCallback != null) {
            mCallback.onDataAvailable(new VideoFrame(
                    data, mFrameSize.x, mFrameSize.y, mFrameFormat, timestamp));
        }
        // Convert the frame format and send it to the video encoder
        if (mVideoEncoder != null) {
            if (timestamp - mLastTimestamp >= mFrameInterval) {
                mLastTimestamp = timestamp;
                switch (mVideoEncoder.getColorFormat()) {
                    case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                        Image.convertNV12ToYUV420SemiPlanar(data, mFrameSize.x, mFrameSize.y);
                        break;
                    case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                        Image.convertNV12ToYUV420Planar(data, mFrameSize.x, mFrameSize.y);
                        break;
                }
                try {
                    mVideoEncoder.push(new VideoFrame(
                            data, mFrameSize.x, mFrameSize.y, mFrameFormat, timestamp));
                } catch (InterruptedException e) {
                    Log.e(TAG, "cannot send the frame to the encoder, operation interrupted");
                    Thread.currentThread().interrupt();
                }
            }
        }
        // Update the frame rate
        if (mCamera != null && mCallback != null) {
            if (timestamp - mLastFpsUpdate > FPS_UPDATE_TIME * 1000) {
                mLastFpsUpdate = timestamp;
                mCallback.onFrameRate(mCamera.getAverageFps());
            }
        }
    }

    @Override
    public void onDataAvailable(VideoCodec codec, byte[] data, MediaCodec.BufferInfo info) {
        if (codec == mVideoEncoder) {
            // Forward to the client
            if (mCallback != null) {
                if (!mVideoCfgSent) {
                    ByteBuffer sps = mVideoEncoder.getSPS();
                    ByteBuffer pps = mVideoEncoder.getPPS();
                    if (sps != null && pps != null) {
                        mCallback.onDataAvailable(new VideoFrame(sps.array(), "sps"));
                        mCallback.onDataAvailable(new VideoFrame(pps.array(), "pps"));
                        mVideoCfgSent = true;
                    }
                }
                mCallback.onDataAvailable(new VideoFrame(data, info.presentationTimeUs));
            }
        }
    }

    @Override
    public void onOutputFormatChanged(VideoCodec codec, MediaFormat newFormat) {
        if (codec == mVideoEncoder) {
            Log.d(TAG, "video encoder output format changed to " + newFormat.toString());
        }
    }

    @Override
    public void onDataAvailable(byte[] data, long timestamp) {
        // Send data to the encoder
        if (mAudioEncoder != null) {
            try {
                mAudioEncoder.push(new AudioData(
                        data, mAudioRecorder.getAudioFormat(), timestamp));
            } catch (InterruptedException e) {
                Log.e(TAG, "cannot send the data to the encoder, operation interrupted");
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void onDataAvailable(AudioCodec codec, byte[] data, MediaCodec.BufferInfo info) {
        if (codec == mAudioEncoder) {
            // Forward to the client
            if (mCallback != null) {
                if (!mAudioCfgSent) {
                    ByteBuffer cfg = mAudioEncoder.getAudioConfig();
                    if (cfg != null) {
                        mCallback.onDataAvailable(new AudioData(cfg.array()));
                        mAudioCfgSent = true;
                    }
                }
                mCallback.onDataAvailable(new AudioData(data, info.presentationTimeUs));
            }
        }
    }

    @Override
    public void onOutputFormatChanged(AudioCodec codec, MediaFormat newFormat) {
        if (codec == mAudioEncoder) {
            Log.d(TAG, "audio encoder output format changed to " + newFormat.toString());
        }
    }
}
