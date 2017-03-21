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

import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.spynet.camera.common.TimeStamp;

import java.io.Closeable;
import java.io.IOException;

import static android.media.AudioFormat.CHANNEL_IN_MONO;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;

/**
 * Implements a wrapper to the underlying audio resources.<br>
 * The acquired audio data will be delivered to the client Context trough the AudioCallback.
 */

public class AudioRecorder implements Closeable {

    protected final String TAG = getClass().getSimpleName();

    // The interval in which the recorded samples are sent to the client, in milliseconds
    private static final int TIMER_INTERVAL = 120;

    private final AudioRecord mRecorder;            // The AudioRecord used to manage the audio resources
    private final byte[] mBuffer;                   // The acquisition buffer
    private AudioRecorder.AudioCallback mCallback;  // The AudioCallback implemented by mContext
    private Thread mRecorderThread;                 // The recorder thread
    private int mTimeInterval;                      // The effective time interval in milliseconds

    private volatile int mGain;                     // The audio gain (power of 2)
    private volatile boolean mMute;                 // Mute the mic

    /**
     * A client may implement this interface to receive audio data as it is available.
     */
    public interface AudioCallback {
        /**
         * Called when new audio data is available.<br>
         * This callback is invoked on a separate thread.
         *
         * @param data      frame data
         * @param timestamp the timestamp in microseconds
         */
        void onDataAvailable(byte[] data, long timestamp);
    }

    /**
     * Creates a new AudioRecorder object.
     *
     * @param callback    the callback to receive audio data
     * @param sampleRate  the sample rate expressed in Hertz
     * @param audioSource the recording source. See {@link MediaRecorder.AudioSource} for the
     *                    recording source definitions.
     */
    public AudioRecorder(AudioCallback callback, int sampleRate, int audioSource) throws IOException {

        int channelConfig, audioFormat;
        int channels, bitsPerSample, periodInFrames;
        int bufferSize, minBufferSize;

        // Store the callback
        mCallback = callback;

        // Setup the audio format
        channelConfig = CHANNEL_IN_MONO;    // Guaranteed to work on all devices
        channels = 1;
        audioFormat = ENCODING_PCM_16BIT;   // Guaranteed to be supported by devices
        bitsPerSample = 16;

        // Determine the buffer size
        mTimeInterval = TIMER_INTERVAL;
        periodInFrames = sampleRate * mTimeInterval / 1000; // frame rate = sample rate
        bufferSize = periodInFrames * channels * bitsPerSample / 8;
        minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        if (bufferSize < minBufferSize) {
            bufferSize = minBufferSize;
            periodInFrames = bufferSize * 8 / channels / bitsPerSample;
            mTimeInterval = periodInFrames * 1000 / sampleRate;
            Log.w(TAG, "increasing buffer size to " + bufferSize);
        }

        // Create and initialize the AudioRecord
        mRecorder = new AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize * 2);
        if (mRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
            throw new IOException("cannot initialize the recorder");
        }
        mBuffer = new byte[bufferSize];
    }

    /**
     * Starts the audio recorder.
     */
    public void open() {
        mRecorder.startRecording();
        mRecorderThread = new Thread(new Runnable() {
            @Override
            public void run() {
                doRecord();
            }
        });
        mRecorderThread.start();
    }

    /**
     * Stops the audio recorder.
     */
    @Override
    public void close() {
        if (mRecorderThread != null) {
            mRecorderThread.interrupt();
            try {
                mRecorderThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "stop recording interrupted");
            }
            mRecorderThread = null;
        }
        mRecorder.stop();
    }

    /**
     * Releases the native AudioRecord resources.<br>
     */
    public void release() {
        mRecorder.release();
    }

    /**
     * Read and process audio data.
     */
    private void doRecord() {

        long timestamp;

        // Recorder loop
        Log.d(TAG, "recorder loop started");
        while (!Thread.interrupted()) {
            try {
                // Read audio data
                int size = mRecorder.read(mBuffer, 0, mBuffer.length);
                timestamp = TimeStamp.getTimeStamp() - mTimeInterval * 1000L;
                if (size == mBuffer.length) {
                    // Force mute or apply the gain
                    if (mMute) {
                        for (int i = 0; i < size; i++) {
                            mBuffer[i] = 0;
                        }
                    } else if (mGain != 0) {
                        if (mRecorder.getAudioFormat() == ENCODING_PCM_16BIT) {
                            for (int i = 0; i < size; i += 2) {
                                int pcm = (mBuffer[i + 1] << 8) | (mBuffer[i] & 0xFF);
                                if (mGain > 0)
                                    pcm <<= mGain;
                                else
                                    pcm >>= -mGain;
                                if (pcm > Short.MAX_VALUE)
                                    pcm = Short.MAX_VALUE;
                                else if (pcm < Short.MIN_VALUE)
                                    pcm = Short.MIN_VALUE;
                                mBuffer[i] = (byte) (pcm & 0xFF);
                                mBuffer[i + 1] = (byte) ((pcm & 0xFFFF) >> 8);
                            }
                        } else {
                            // not implemented
                        }
                    }
                    // Call the client callback
                    if (mCallback != null)
                        mCallback.onDataAvailable(mBuffer, timestamp);
                }
            } catch (Exception e) {
                Log.e(TAG, "unexpected exception while recording", e);
                break;
            }
        }
        Log.d(TAG, "recorder loop stopped");
    }

    /**
     * @return the configured audio sample rate in Hz
     */
    public int getSampleRate() {
        return mRecorder.getSampleRate();
    }

    /**
     * @return the configured number of channels
     */
    public int getChannelCount() {
        return mRecorder.getChannelCount();
    }

    /**
     * @return the configured audio data encoding
     */
    public int getAudioFormat() {
        return mRecorder.getAudioFormat();
    }

    /**
     * @return the buffer size
     */
    public int getBufferSize() {
        return mBuffer.length;
    }

    /**
     * Sets the audio gain.
     *
     * @param gain desired audio gain in dB
     */
    public void setGain(double gain) {
        double dB = Math.pow(10, gain / 20);
        double g = Math.log(dB) / Math.log(2);
        mGain = (int) Math.round(g);
    }

    /**
     * Gets the audio gain.
     *
     * @return the current audio gain in dB
     */
    public double getGain() {
        return 20 * Math.log10(Math.pow(2, mGain));
    }

    /**
     * Sets the mute state.
     *
     * @param mute {@code true} to mute the MIC, {@code false} to enable the audio recording
     */
    public void setMute(boolean mute) {
        mMute = mute;
    }

    /**
     * @return the current mute state.
     */
    public boolean getMute() {
        return mMute;
    }
}
