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
 * Defines either an audio encoder or decoder.
 */
public abstract class AudioCodec {

    // AAC audio MIME type
    protected static final String MIME_TYPE = "audio/mp4a-latm";

    protected final String TAG = getClass().getSimpleName();

    protected final int QUEUE_CAPACITY = 10;            // Queue capacity
    protected final int QUEUE_WRITE_TIMEOUT = 5;        // Timeout to write to the queue in ms
    protected final int QUEUE_READ_TIMEOUT = 1000;      // Timeout to read from the queue in ms
    protected final int BUFFER_TIMEOUT = 1000;          // Timeout to access buffers in us

    protected final BlockingQueue<AudioData> mQueue;    // The queue used to pass audio data to the AudioCodec
    protected final CodecCallback mCodecCallback;       // The CodecCallback implemented by mContext

    /**
     * A client may implement this interface to receive data buffers as they are available.
     */
    public interface CodecCallback {
        /**
         * Called when new data is available.<br>
         * This callback is invoked on the codec thread.
         *
         * @param codec the AudioCodec that called this callback
         * @param data  encoded or decoded audio data
         * @param info  buffer metadata that describe its content
         */
        void onDataAvailable(AudioCodec codec, byte[] data, MediaCodec.BufferInfo info);

        /**
         * Called when the output format changes.<br>
         * This callback is invoked on the codec thread.
         *
         * @param codec     the AudioCodec that called this callback
         * @param newFormat audio data format
         */
        void onOutputFormatChanged(AudioCodec codec, MediaFormat newFormat);
    }

    /**
     * @return the supported MIME type.
     */
    static public String getMIME() {
        return MIME_TYPE;
    }

    /**
     * Creates a new AudioCodec object.
     *
     * @param callback the callback to receive the encoded/decoded audio data
     */
    protected AudioCodec(CodecCallback callback) {
        mQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        mCodecCallback = callback;
    }

    /**
     * Pushes new data to the codec queue.<br>
     * If the queue is full, the data is silently dropped.
     *
     * @param data the audio data to be processed
     * @return true if the data has been added successfully, false if it was dropped
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean push(AudioData data) throws InterruptedException {
        if (mQueue.offer(data, QUEUE_WRITE_TIMEOUT, TimeUnit.MILLISECONDS))
            return true;
        Log.v(TAG, "cannot add the data, the queue is full");
        return false;
    }

    /**
     * Pops some data from the coded queue.
     *
     * @return the retrieved data, null if the operation timed out
     * @throws InterruptedException if interrupted while waiting
     */
    protected AudioData pop() throws InterruptedException {
        AudioData data = mQueue.poll(QUEUE_READ_TIMEOUT, TimeUnit.MILLISECONDS);
        if (data == null)
            Log.v(TAG, "cannot get the data, the queue is empty");
        return data;
    }
}
