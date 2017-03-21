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

import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.spynet.camera.common.TimeStamp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements a wrapper to the underlying Camera devices.<br>
 * The acquired frames will be delivered to the client Context trough the FrameCallback.
 */
@SuppressWarnings("deprecation")
public class LiveCamera implements com.spynet.camera.media.Camera {

    protected final String TAG = getClass().getSimpleName();

    // The focus window half-size, in the range 0-1000
    private final static int FOCUS_WINDOW_SIZE = 250;

    private final Camera mCamera;                   // The underlying Camera device
    private final int mFacing;                      // The camera facing
    private final SurfaceTexture mSurfaceTexture;   // The SurfaceTexture used to render the preview offscreen
    private FrameCallback mFrameCallback;           // The FrameCallback implemented by mContext
    private DummySurfaceHolder mSurfaceHolder;      // The SurfaceHolder that embeds the preview Surface
    private long mStartTime;                        // Time when start counting frames
    private long mFrameCount;                       // Number of counted frames
    private volatile float mAverageFps;             // Average frames speed

    /**
     * Creates a new LiveCamera object.
     *
     * @param callback the callback to receive the acquired video frames
     * @param cameraId the id of the camera to open.
     * @throws IOException if opening the camera fails
     */
    public LiveCamera(FrameCallback callback, int cameraId) throws IOException {
        // Store the callback
        mFrameCallback = callback;
        // Open the Camera
        if ((mCamera = Camera.open(cameraId)) == null)
            throw new IOException("cannot open the camera");
        Camera.CameraInfo ci = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, ci);
        mFacing = ci.facing;
        // Create the SurfaceTexture to render the preview offscreen
        int[] mTextureHandles = new int[1];
        GLES20.glGenTextures(1, mTextureHandles, 0);
        mSurfaceTexture = new SurfaceTexture(mTextureHandles[0]);
    }

    /**
     * Opens the camera.<br>
     * The final width, height and format might differ from the desired ones,
     * depending on the device capabilities:
     * <ul>
     * <li>use {@link #getFrameSize()} to determine the effective frame size</li>
     * <li>use {@link #getFrameFormat()} to determine the color format.</li>
     * </ul>
     */
    @Override
    public void open(int width, int height, int format) throws IOException {

        // Read current camera parameters
        Camera.Parameters params;
        params = mCamera.getParameters();

        // Find the best picture size
        Camera.Size bestSize = null;
        float bestScore = 0.0f;
        for (Camera.Size s : params.getSupportedPreviewSizes()) {
            Log.v(TAG, "available resolution " + s.width + "x" + s.height);
            float scoreX = (float) width / (float) s.width;
            if (scoreX > 1.0f) scoreX = 1.0f / scoreX;
            float scoreY = (float) height / (float) s.height;
            if (scoreY > 1.0f) scoreY = 1.0f / scoreY;
            float score = Math.min(scoreX, scoreY);
            if (score > bestScore || bestSize == null) {
                bestSize = s;
                bestScore = score;
            }
        }
        assert bestSize != null; // getSupportedPreviewSizes() always returns a list with at least one element
        Log.i(TAG, "preview size is " + bestSize.width + "x" + bestSize.height);
        params.setPreviewSize(bestSize.width, bestSize.height);

        // Set the desired pixel-format
        int colorFormat = ImageFormat.UNKNOWN;
        List<Integer> formats = params.getSupportedPreviewFormats();
        for (int f : formats) {
            Log.v(TAG, "supported preview format : " + f);
            if (f == format) colorFormat = f;
        }
        if (colorFormat == ImageFormat.UNKNOWN)
            colorFormat = ImageFormat.NV21;
        Log.i(TAG, "preview format is " + colorFormat);
        params.setPreviewFormat(colorFormat);

        // Set the focus mode to auto
        if (params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO))
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);

        // Set the flash mode to off
        List<String> flashModes = params.getSupportedFlashModes();
        if (flashModes != null && flashModes.contains(Camera.Parameters.FLASH_MODE_OFF))
            params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);

        // Allow full fps preview
        params.setRecordingHint(true);

        // Set new camera parameters
        mCamera.setParameters(params);

        // Start the preview
        mStartTime = 0;
        mFrameCount = 0;
        startPreview(null);
    }

    /**
     * Starts the preview on the surface associated to the specified {@link SurfaceHolder}.<br>
     * If {@code holder} is {@code null}, the stream will be rendered offscreen.
     *
     * @param holder SurfaceHolder containing the Surface on which to place the preview,
     *               or null to render the stream offscreen
     * @throws IOException if starting the preview fails
     */
    private void startPreview(SurfaceHolder holder) throws IOException {
        if (holder != null) {
            mCamera.setPreviewDisplay(holder);
        } else {
            mCamera.setPreviewTexture(mSurfaceTexture);
        }
        mCamera.setPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                processPreviewFrame(data);
            }
        });
        mCamera.startPreview();
    }

    /**
     * Stops the preview.
     */
    private void stopPreview() {
        mCamera.setPreviewCallback(null);
        mCamera.stopPreview();
    }

    @Override
    public void close() {
        stopPreview();
        mAverageFps = 0.0f;
    }

    @Override
    public void release() {
        mCamera.release();
    }

    @Override
    public void setSurface(Surface surface) throws IOException {
        stopPreview();
        if (surface != null) {
            mSurfaceHolder = new DummySurfaceHolder(surface);
            startPreview(mSurfaceHolder);
        } else {
            mSurfaceHolder = null;
            startPreview(null);
        }
    }

    @Override
    public Surface getSurface() {
        return mSurfaceHolder != null ? mSurfaceHolder.getSurface() : null;
    }

    @Override
    public boolean isFront() {
        return mFacing == Camera.CameraInfo.CAMERA_FACING_FRONT;
    }

    @Override
    public Point getFrameSize() {
        Camera.Size size = mCamera.getParameters().getPreviewSize();
        return new Point(size.width, size.height);
    }

    @Override
    public int getFrameFormat() {
        return mCamera.getParameters().getPreviewFormat();
    }

    @Override
    public float getZoom() {
        Camera.Parameters parameters = mCamera.getParameters();
        if (parameters.isZoomSupported())
            return (float) parameters.getZoomRatios().get(parameters.getZoom()) / 100f;
        return 1.0f;
    }

    @Override
    public void setZoom(float value) {
        Camera.Parameters parameters = mCamera.getParameters();
        if (parameters.isZoomSupported()) {
            int zoom;
            // Determine the zoom value from the zoom factor
            List<Integer> zoomRatios = parameters.getZoomRatios();
            value *= 100f;
            if (value <= zoomRatios.get(0)) {
                zoom = 0;
            } else if (value >= zoomRatios.get(zoomRatios.size() - 1)) {
                zoom = zoomRatios.size() - 1;
            } else {
                for (zoom = 0; zoom < zoomRatios.size() - 1; zoom++) {
                    float r1 = zoomRatios.get(zoom);
                    float r2 = zoomRatios.get(zoom + 1);
                    if (r1 <= value && r2 > value) {
                        if (Math.abs(1 - r2 / value) < Math.abs(1 - r1 / value))
                            zoom++;
                        break;
                    }
                }
            }
            Log.v(TAG, "zoom value " + zoom + ", factor " + zoomRatios.get(zoom));
            // Set new zoom value
            parameters.setZoom(zoom);
            try {
                mCamera.setParameters(parameters);
            } catch (RuntimeException e) {
                Log.e(TAG, "can't set the zoom", e);
            }
        }
    }

    @Override
    public void autoFocus(int x, int y) {
        // Setup the focus (and metering) area
        Camera.Parameters params = mCamera.getParameters();
        if (!params.getFocusMode().equals(Camera.Parameters.FOCUS_MODE_AUTO))
            return;
        if (params.getMaxNumFocusAreas() > 0) {
            int l = Math.max(x - FOCUS_WINDOW_SIZE, -1000);
            int t = Math.max(y - FOCUS_WINDOW_SIZE, -1000);
            int r = Math.min(x + FOCUS_WINDOW_SIZE, 1000);
            int b = Math.min(y + FOCUS_WINDOW_SIZE, 1000);
            Camera.Area area = new Camera.Area(new Rect(l, t, r, b), 1000);
            List<Camera.Area> areasList = new ArrayList<>();
            areasList.add(area);
            params.setFocusAreas(areasList);
            if (params.getMaxNumMeteringAreas() > 0)
                params.setMeteringAreas(areasList);
            try {
                mCamera.setParameters(params);
                Log.v(TAG, "autofocus area: (" + l + "," + t + ")-(" + r + "," + b + ")");
            } catch (RuntimeException e) {
                Log.e(TAG, "can't setup the autofocus", e);
            }
        }
        // Do autofocus
        try {
            mCamera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    Log.v(TAG, "autofocus operation " + (success ? "succeeded" : "failed"));
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "autofocus operation failed", e);
        }
    }

    @Override
    public boolean getTorch() {
        String flashMode = mCamera.getParameters().getFlashMode();
        return flashMode != null && flashMode.equals(Camera.Parameters.FLASH_MODE_TORCH);
    }

    @Override
    public void setTorch(boolean state) {
        Camera.Parameters params;
        params = mCamera.getParameters();
        List<String> flashModes = params.getSupportedFlashModes();
        if (state) {
            if (flashModes != null && flashModes.contains(Camera.Parameters.FLASH_MODE_TORCH))
                params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        } else {
            if (flashModes != null && flashModes.contains(Camera.Parameters.FLASH_MODE_OFF))
                params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        }
        try {
            mCamera.setParameters(params);
        } catch (RuntimeException e) {
            Log.e(TAG, "can't set the torch", e);
        }
    }

    @Override
    public float getAverageFps() {
        return mAverageFps;
    }

    /**
     * Handles a preview frame.<br>
     * It is invoked on the event thread open(int) was called from.
     *
     * @param data the content of the preview frame
     */
    private void processPreviewFrame(byte[] data) {
        // Call the client callback
        if (mFrameCallback != null)
            mFrameCallback.onFrameAvailable(data, TimeStamp.getTimeStamp());
        // Compute average fps
        if (mStartTime == 0) {
            mStartTime = System.currentTimeMillis();
            mFrameCount = 0;
        } else if (++mFrameCount == 50) {
            long dt = System.currentTimeMillis() - mStartTime;
            float fps = (float) mFrameCount / (float) (dt) * 1000;
            if (mAverageFps == 0) mAverageFps = fps;
            else mAverageFps = (mAverageFps * 4 + fps) / 5;
            mStartTime = 0;
        }
    }
}
