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

import android.content.res.Resources;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.Surface;

import com.spynet.camera.common.TimeStamp;
import com.spynet.camera.gl.EGLOffscreenContext;
import com.spynet.camera.gl.TextureRender;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements the dummy Camera used when the screen capture is selected.
 */
public class ScreenCaptureCamera implements com.spynet.camera.media.Camera {

    protected final String TAG = getClass().getSimpleName();

    private final static int FRAME_TIMEOUT = 100;   // Encoder doesn't like less than 10 fps

    private final Object mFrameSyncObject = new Object();

    private FrameCallback mFrameCallback;           // The FrameCallback implemented by mContext
    private Point mFrameSize;                       // The frame size in pixel
    private int mFrameFormat;                       // The frame format
    private SurfaceTexture mSurfaceTexture;         // The SurfaceTexture where the frames will be rendered
    private Surface mSurface;                       // The Surface attached to the SurfaceTexture
    private TextureRender mTextureRender;           // The TextureRender that will render the frames
    private EGLOffscreenContext mEglContext;        // The offscreen EGL context used by OpenGL
    private ByteBuffer mFrameBuffer;                // The input frame buffer (from OpenGL)
    private Thread mDeliverThread;                  // The thread that deliver the frames to the client
    private long mStartTime;                        // Time when start counting frames
    private long mFrameCount;                       // Number of counted frames
    private volatile float mAverageFps;             // Average frames speed

    /**
     * @return the list of supported capture sizes
     */
    public static List<Point> getSupportedSizes() {
        ArrayList<Point> sizes = new ArrayList<>();
        int cx = Resources.getSystem().getDisplayMetrics().widthPixels;
        int cy = Resources.getSystem().getDisplayMetrics().heightPixels;
        do {
            sizes.add(new Point(cx, cy));
            cx /= 2;
            cy /= 2;
        } while (cx >= 320 || cy >= 240); // a reasonable minimum size
        return sizes;
    }

    /**
     * Creates a new ScreenCaptureCamera object.
     *
     * @param callback the callback to receive the acquired video frames
     */
    public ScreenCaptureCamera(FrameCallback callback) {
        // Store the callback
        mFrameCallback = callback;
    }

    @Override
    public void open(int width, int height, int format) {

        // Store the format
        if (format != ImageFormat.NV21)
            throw new IllegalArgumentException("unsupported format");
        mFrameFormat = format;

        // Find the best picture size
        float bestScore = 0.0f;
        for (Point size : getSupportedSizes()) {
            Log.v(TAG, "available resolution " + size.x + "x" + size.y);
            float scoreX = (float) width / (float) size.x;
            if (scoreX > 1.0f) scoreX = 1.0f / scoreX;
            float scoreY = (float) height / (float) size.y;
            if (scoreY > 1.0f) scoreY = 1.0f / scoreY;
            float score = Math.min(scoreX, scoreY);
            if (score > bestScore || mFrameSize == null) {
                mFrameSize = size;
                bestScore = score;
            }
        }
        assert mFrameSize != null; // getSupportedSizes() always returns a list with at least one element
        Log.i(TAG, "capture size is " + mFrameSize.x + "x" + mFrameSize.y);

        // Allocate frame buffers
        mFrameBuffer = ByteBuffer.allocateDirect(mFrameSize.x * mFrameSize.y * 4);

        // Initialize OpenGL stuff
        mEglContext = new EGLOffscreenContext(
                EGLOffscreenContext.CONFIG_PIXEL_RGBA_BUFFER,
                mFrameSize.x, mFrameSize.y);
        mEglContext.makeCurrent();
        mTextureRender = new TextureRender();
        mSurfaceTexture = new SurfaceTexture(mTextureRender.getTextureId());
        mSurfaceTexture.setDefaultBufferSize(mFrameSize.x, mFrameSize.y);
        mSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                synchronized (mFrameSyncObject) {
                    grabNextFrame();
                    mFrameSyncObject.notifyAll();
                }
            }
        });
        mSurface = new Surface(mSurfaceTexture);

        // Start the thread to deliver frames to the client
        mStartTime = 0;
        mFrameCount = 0;
        mDeliverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] data = new byte[mFrameSize.x * mFrameSize.y * 3 / 2];
                synchronized (mFrameSyncObject) {
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            mFrameSyncObject.wait(FRAME_TIMEOUT);
                            convertToNV12(mFrameBuffer.array(), data, mFrameSize.x, mFrameSize.y);
                            processPreviewFrame(data);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }
            }
        });
        mDeliverThread.start();
    }

    @Override
    public void close() {
        if (mDeliverThread != null) {
            mDeliverThread.interrupt();
            try {
                mDeliverThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "stop deliver interrupted");
            }
            mDeliverThread = null;
        }
        synchronized (mFrameSyncObject) {
            if (mSurfaceTexture != null)
                mSurfaceTexture.setOnFrameAvailableListener(null);
        }
    }

    @Override
    public void release() {
        synchronized (mFrameSyncObject) {
            if (mSurface != null)
                mSurface.release();
            if (mSurfaceTexture != null)
                mSurfaceTexture.release();
            if (mEglContext != null)
                mEglContext.release();
        }
    }

    @Override
    public void setSurface(Surface surface) throws IOException {
    }

    @Override
    public Surface getSurface() {
        return mSurface;
    }

    @Override
    public boolean isFront() {
        return true;
    }

    @Override
    public Point getFrameSize() {
        return mFrameSize;
    }

    @Override
    public int getFrameFormat() {
        return mFrameFormat;
    }

    @Override
    public float getZoom() {
        return 1.0f;
    }

    @Override
    public void setZoom(float value) {
    }

    @Override
    public void autoFocus(int x, int y) {
    }

    @Override
    public boolean getTorch() {
        return false;
    }

    @Override
    public void setTorch(boolean state) {
    }

    @Override
    public float getAverageFps() {
        return mAverageFps;
    }

    /**
     * Acquire the next video frame.
     */
    private void grabNextFrame() {
        // Check if still alive
        if (mSurface == null || !mSurface.isValid())
            return;
        // Draw the frame and copy its pixels in the frame buffer
        mEglContext.makeCurrent();
        mSurfaceTexture.updateTexImage();
        mTextureRender.draw();
        mTextureRender.getPixels(mFrameSize.x, mFrameSize.y, mFrameBuffer);
    }

    /**
     * Adjust the frame format.
     * The frame buffer acquired from the OpenGL surface contains pixels whose size is 4 bytes.
     * Bytes represent Y, U, V, A (alpha color not used here).
     * We have to rearrange bytes in the NV12 format, that is 4:2:0 YCrCb with planar Y,
     * followed by interleaved Cr/Cb plane.
     */
    public void convertToNV12(byte[] argb, byte[] nv12, int width, int height) {

        int index = 0;
        int yIndex = 0;
        int uvIndex = width * height;
        byte Y, U, V;

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                Y = argb[index++];
                U = argb[index++];
                V = argb[index++];
                index++;
                nv12[yIndex++] = Y;
                if (j % 2 == 1 && i % 2 == 1) {
                    nv12[uvIndex++] = V;
                    nv12[uvIndex++] = U;
                }
            }
        }
    }

    /**
     * Handles a frame.
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
