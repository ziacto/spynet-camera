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
import android.media.MediaFormat;
import android.util.Log;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Defines either a video encoder or decoder.
 */
public abstract class VideoCodec {

    // H.264 Advanced Video Coding MIME type
    protected static final String MIME_TYPE = "video/avc";

    protected final String TAG = getClass().getSimpleName();

    protected final int QUEUE_CAPACITY = 10;            // Frame queue capacity
    protected final int QUEUE_WRITE_TIMEOUT = 5;        // Timeout to write to the queue in ms
    protected final int QUEUE_READ_TIMEOUT = 1000;      // Timeout to read from the queue in ms
    protected final int BUFFER_TIMEOUT = 1000;          // Timeout to access buffers in us

    protected final BlockingQueue<VideoFrame> mQueue;   // The queue used to pass video data to the VideoCodec
    protected final CodecCallback mCodecCallback;       // The CodecCallback implemented by mContext

    /**
     * A client may implement this interface to receive data buffers as they are available.
     */
    public interface CodecCallback {
        /**
         * Called when new data is available.<br>
         * This callback is invoked on the codec thread.
         *
         * @param codec the VideoCodec that called this callback
         * @param data  encoded or decoded video data
         * @param info  buffer metadata that describe its content
         */
        void onDataAvailable(VideoCodec codec, byte[] data, MediaCodec.BufferInfo info);

        /**
         * Called when the output format changes.<br>
         * This callback is invoked on the codec thread.
         *
         * @param codec     the VideoCodec that called this callback
         * @param newFormat video data format
         */
        void onOutputFormatChanged(VideoCodec codec, MediaFormat newFormat);
    }

    /**
     * @return the supported MIME type.
     */
    static public String getMIME() {
        return MIME_TYPE;
    }

    /**
     * Creates a new VideoCodec object.
     *
     * @param callback the callback to receive the encoded/decoded video data
     */
    protected VideoCodec(CodecCallback callback) {
        mQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        mCodecCallback = callback;
    }

    /**
     * Pushes a new frame to the codec queue.<br>
     * If the queue is full, the frame is silently dropped.
     *
     * @param frame the frame to be processed
     * @return true if the frame has been added successfully, false if it was dropped
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean push(VideoFrame frame) throws InterruptedException {
        if (mQueue.offer(frame, QUEUE_WRITE_TIMEOUT, TimeUnit.MILLISECONDS))
            return true;
        Log.v(TAG, "cannot add the frame, the queue is full");
        return false;
    }

    /**
     * Pops a frame from the coded queue.
     *
     * @return the retrieved Frame, null if the operation timed out
     * @throws InterruptedException if interrupted while waiting
     */
    protected VideoFrame pop() throws InterruptedException {
        VideoFrame frame = mQueue.poll(QUEUE_READ_TIMEOUT, TimeUnit.MILLISECONDS);
        if (frame == null)
            Log.v(TAG, "cannot get the frame, the queue is empty");
        return frame;
    }
}
