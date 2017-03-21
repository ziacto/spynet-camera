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

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;

import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Implements a video encoder that encodes incoming uncompressed video frames.
 */
@SuppressWarnings("deprecation")
public class VideoEncoder extends VideoCodec implements Closeable {

    private final MediaCodec mEncoder;              // The underlying encoder
    private ByteBuffer[] mInputBuffers;             // The encoder input buffers
    private ByteBuffer[] mOutputBuffers;            // The encoder output buffers
    private Thread mEncoderThread;                  // The encoding thread
    private Surface mInputSurface;                  // The input Surface
    private int mColorFormat;                       // The input buffer color format
    private int mBitrate;                           // The stream bitrate
    private volatile ByteBuffer mSPS;               // The Sequence Parameter Set
    private volatile ByteBuffer mPPS;               // The Picture Parameter Set

    /**
     * Creates a new VideoEncoder object.
     *
     * @param callback the callback to receive the encoded/decoded video data
     */
    public VideoEncoder(CodecCallback callback) throws IOException {
        super(callback);
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
    }

    /**
     * Initializes the video encoder.
     *
     * @param width     the input frames width (in pixels)
     * @param height    the input frames height (in pixels)
     * @param format    the input color format (from MediaCodecInfo.CodecCapabilities)
     * @param framerate the expected frame rate
     * @param bitrate   the desired bitrate in bps
     * @param distance  the reference frame distance in seconds
     */
    public void open(int width, int height, int format, int framerate, int bitrate, int distance) {

        // Configure the encoder
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, format);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, distance);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            mediaFormat.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 2000000 / framerate);
        mEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        if (format == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                mInputSurface = mEncoder.createInputSurface();
            else
                throw new IllegalArgumentException("COLOR_FormatSurface requires API " +
                        Build.VERSION_CODES.JELLY_BEAN_MR2);
        }

        // Start the encoder
        mEncoder.start();
        mInputBuffers = mEncoder.getInputBuffers();
        mOutputBuffers = mEncoder.getOutputBuffers();
        startEncoding();

        // Store properties
        mColorFormat = format;
        mBitrate = bitrate;
    }

    /**
     * Stops the video encoder.
     */
    @Override
    public void close() {
        stopEncoding();
        mEncoder.stop();
        mInputBuffers = null;
        mOutputBuffers = null;
        mColorFormat = 0;
        mBitrate = 0;
    }

    /**
     * Free up resources used by the codec instance.
     * Make sure you call this when you're done to free up any opened component instance
     * instead of relying on the garbage collector to do this for you at some point in the future.
     */
    public void release() {
        mEncoder.release();
        if (mInputSurface != null)
            mInputSurface.release();
    }

    /**
     * @return the {@link Surface} used as the input to an encoder, {@code null} if not used
     */
    public Surface getSurface() {
        return mInputSurface;
    }

    /**
     * @return the list of the supported color formats
     */
    public int[] colorFormats() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return mEncoder.getCodecInfo().getCapabilitiesForType(MIME_TYPE).colorFormats;
        } else {
            for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
                MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
                if (codecInfo.isEncoder()) {
                    for (String type : codecInfo.getSupportedTypes()) {
                        if (type.equals(MIME_TYPE)) {
                            return codecInfo.getCapabilitiesForType(MIME_TYPE).colorFormats;
                        }
                    }
                }
            }
            return new int[]{};
        }
    }

    /**
     * @param format the color format to check
     * @return true if the specified color format is supported, false otherwise
     */
    public boolean supportsColorFormat(int format) {
        int[] colorFormats = colorFormats();
        for (int c : colorFormats) {
            if (c == format)
                return true;
        }
        return false;
    }

    /**
     * @return the current color format, 0 if the encoder is not opened
     */
    public int getColorFormat() {
        return mColorFormat;
    }

    /**
     * @return the current bitrate
     */
    public int getBitrate() {
        return mBitrate;
    }

    /**
     * Requests a reference frame to be generate as soon as possible.
     */
    public void requestSyncFrame() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Bundle bundle = new Bundle();
            bundle.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
            mEncoder.setParameters(bundle);
        }
    }

    /**
     * @return the Sequence Parameter Set, {@code null} if not available
     */
    @Nullable
    public ByteBuffer getSPS() {
        return mSPS;
    }

    /**
     * @return the Picture Parameter Set, {@code null} if not available
     */
    @Nullable
    public ByteBuffer getPPS() {
        return mPPS;
    }

    /**
     * Helper to start the encoding thread.
     */
    private void startEncoding() {
        mEncoderThread = new Thread(new Runnable() {
            @Override
            public void run() {
                doEncode();
            }
        });
        mEncoderThread.start();
    }

    /**
     * Helper to stop the encoding thread.
     */
    private void stopEncoding() {
        if (mEncoderThread != null) {
            mEncoderThread.interrupt();
            try {
                mEncoderThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "stop encoding interrupted");
            }
            mEncoderThread = null;
        }
    }

    /**
     * Encodes the incoming video frames.
     */
    private void doEncode() {

        ByteBuffer outBuffer, inBuffer;     // In/out buffers
        VideoFrame videoFrame;              // Incoming uncompressed frame

        // Reset the input queue
        mQueue.clear();

        // Encoder loop
        Log.d(TAG, "encoder loop started");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Process input buffer
                if (mInputSurface == null) {
                    int inputBufferId = mEncoder.dequeueInputBuffer(BUFFER_TIMEOUT);
                    if (inputBufferId >= 0) {
                        // Fill input buffer with valid data from the queue
                        inBuffer = mInputBuffers[inputBufferId];
                        inBuffer.clear();
                        videoFrame = pop();
                        if (videoFrame != null) {
                            byte[] data = videoFrame.getData();
                            if (data != null) {
                                if (data.length > inBuffer.capacity()) {
                                    Log.e(TAG, "insufficient buffer size (" + inBuffer.capacity() + "), " +
                                            "data length is " + data.length);
                                    break;
                                }
                                inBuffer.put(data);
                                mEncoder.queueInputBuffer(inputBufferId, 0, data.length,
                                        videoFrame.getTimestamp(), 0);
                            } else {
                                Log.w(TAG, "null frame received");
                                mEncoder.queueInputBuffer(inputBufferId, 0, 0, 0, 0);
                            }
                        } else {
                            Log.e(TAG, "timeout reading from the queue");
                            mEncoder.queueInputBuffer(inputBufferId, 0, 0, 0, 0);
                        }
                    }
                }
                // Process output buffer
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                int outputBufferId = mEncoder.dequeueOutputBuffer(info, BUFFER_TIMEOUT);
                if (outputBufferId >= 0) {
                    // Output buffer is ready to be processed or rendered
                    outBuffer = mOutputBuffers[outputBufferId];
                    byte[] data = new byte[info.size];
                    outBuffer.position(info.offset);
                    outBuffer.limit(info.offset + info.size);
                    outBuffer.get(data, info.offset, info.size);
                    mEncoder.releaseOutputBuffer(outputBufferId, false);
                    if (mCodecCallback != null) {
                        mCodecCallback.onDataAvailable(this, data, info);
                    }
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    mOutputBuffers = mEncoder.getOutputBuffers();
                    Log.d(TAG, "output buffers changed");
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Subsequent data will conform to new format
                    MediaFormat newFormat = mEncoder.getOutputFormat();
                    mSPS = newFormat.getByteBuffer("csd-0");
                    mPPS = newFormat.getByteBuffer("csd-1");
                    Log.i(TAG, "output format changed to " + newFormat.toString());
                    if (mCodecCallback != null) {
                        mCodecCallback.onOutputFormatChanged(this, newFormat);
                    }
                }
            } catch (InterruptedException e) {
                Log.v(TAG, "encoder loop interrupted");
                break;
            } catch (Exception e) {
                Log.e(TAG, "unexpected exception while encoding", e);
                break;
            }
        }
        Log.d(TAG, "encoder loop stopped");
    }
}
