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
import android.media.MediaFormat;
import android.util.Log;

import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Implements an audio encoder that encodes incoming uncompressed audio.
 */
@SuppressWarnings("deprecation")
public class AudioEncoder extends AudioCodec implements Closeable {

    private final MediaCodec mEncoder;              // The underlying encoder
    private ByteBuffer[] mInputBuffers;             // The encoder input buffers
    private ByteBuffer[] mOutputBuffers;            // The encoder output buffers
    private Thread mEncoderThread;                  // The encoding thread
    private int mBitrate;                           // The stream bitrate
    private volatile ByteBuffer mAudioCfg;          // The current audio configuration

    /**
     * Creates a new AudioEncoder object.
     *
     * @param callback the callback to receive the encoded/decoded audio data
     */
    public AudioEncoder(CodecCallback callback) throws IOException {
        super(callback);
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
    }

    /**
     * Initializes the audio encoder.
     *
     * @param bitrate         the desired bitrate in bps
     * @param sampleRate      the sampling rate of the content
     * @param channelCount    the number of audio channels in the content
     * @param inputBufferSize the size of the input buffer in byes
     */
    public void open(int bitrate, int sampleRate, int channelCount, int inputBufferSize) {

        // Configure the encoder
        MediaFormat mediaFormat = MediaFormat.createAudioFormat(MIME_TYPE, sampleRate, channelCount);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, inputBufferSize);
        mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectMain);
        mEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        // Start the encoder
        mEncoder.start();
        mInputBuffers = mEncoder.getInputBuffers();
        mOutputBuffers = mEncoder.getOutputBuffers();
        startEncoding();

        // Store properties
        mBitrate = bitrate;
    }

    /**
     * Stops the audio encoder.
     */
    @Override
    public void close() {
        stopEncoding();
        mEncoder.stop();
        mInputBuffers = null;
        mOutputBuffers = null;
        mBitrate = 0;
    }

    /**
     * Free up resources used by the codec instance.
     * Make sure you call this when you're done to free up any opened component instance
     * instead of relying on the garbage collector to do this for you at some point in the future.
     */
    public void release() {
        mEncoder.release();
    }

    /**
     * @return the current bitrate
     */
    public int getBitrate() {
        return mBitrate;
    }

    /**
     * @return the audio specific configuration, {@code null} if not available
     */
    @Nullable
    public ByteBuffer getAudioConfig() {
        return mAudioCfg;
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
     * Encodes the incoming audio.
     */
    private void doEncode() {

        ByteBuffer outBuffer, inBuffer;     // In/out buffers
        AudioData audioData;                // Incoming uncompressed audio data

        // Reset the input queue
        mQueue.clear();

        // Encoder loop
        Log.d(TAG, "encoder loop started");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Process input buffer
                int inputBufferId = mEncoder.dequeueInputBuffer(BUFFER_TIMEOUT);
                if (inputBufferId >= 0) {
                    // Fill input buffer with valid data from the queue
                    inBuffer = mInputBuffers[inputBufferId];
                    inBuffer.clear();
                    audioData = pop();
                    if (audioData != null) {
                        byte[] data = audioData.getData();
                        if (data != null) {
                            if (data.length > inBuffer.capacity()) {
                                Log.e(TAG, "insufficient buffer size (" + inBuffer.capacity() + "), " +
                                        "data length is " + data.length);
                                break;
                            }
                            inBuffer.put(data);
                            mEncoder.queueInputBuffer(inputBufferId, 0, data.length,
                                    audioData.getTimestamp(), 0);
                        } else {
                            Log.w(TAG, "null data received");
                            mEncoder.queueInputBuffer(inputBufferId, 0, 0, 0, 0);
                        }
                    } else {
                        Log.e(TAG, "timeout reading from the queue");
                        mEncoder.queueInputBuffer(inputBufferId, 0, 0, 0, 0);
                    }
                }
                // Process output buffer
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                int outputBufferId = mEncoder.dequeueOutputBuffer(info, BUFFER_TIMEOUT);
                if (outputBufferId >= 0) {
                    // Output buffer is ready to be processed
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
                    mAudioCfg = newFormat.getByteBuffer("csd-0");
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
