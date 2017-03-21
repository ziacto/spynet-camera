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

import android.support.annotation.Nullable;
import android.util.Base64;
import android.util.Log;

import com.spynet.camera.media.AudioData;
import com.spynet.camera.media.VideoFrame;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Defines a TCP connection that handles requests to the StreamServer.
 */
public class StreamConnection extends TCPConnection {

    public final static String TYPE_MJPEG = "mjpeg";    // MJPEG video
    public final static String TYPE_H264 = "h264";      // H264 video
    public final static String TYPE_AAC = "aac";        // AAC audio

    protected final String TAG = getClass().getSimpleName();

    protected final int QUEUE_CAPACITY = 10;            // Frame queue capacity
    protected final int QUEUE_WRITE_TIMEOUT = 1;        // Timeout to write to the queue in ms
    protected final int QUEUE_READ_TIMEOUT = 5000;      // Timeout to read from the queue in ms

    private final BlockingQueue<VideoFrame> mFrameQueue // The queue used to send uncompressed video frames
            = new ArrayBlockingQueue<>(QUEUE_CAPACITY); //
    private final BlockingQueue<VideoFrame> mSliceQueue // The queue used to send compressed slices
            = new ArrayBlockingQueue<>(QUEUE_CAPACITY); //
    private final BlockingQueue<AudioData> mAudioQueue  // The queue used to send compressed audio
            = new ArrayBlockingQueue<>(QUEUE_CAPACITY); //
    private int mRTPSeq;                                // First RTP packet sequential number
    private String mRTSPSession;                        // RTSP session ID
    private UDPVideoPacketizer mUDPVideoPacketizer;     // UDP video packetizer
    private UDPAudioPacketizer mUDPAudioPacketizer;     // UDP audio packetizer
    private TCPVideoPacketizer mTCPVideoPacketizer;     // TCP video packetizer
    private TCPAudioPacketizer mTCPAudioPacketizer;     // TCP audio packetizer
    private volatile boolean mStreamingMJPEG;           // Indicates whether this connection is streaming MJPEG
    private volatile boolean mStreamingH264;            // Indicates whether this connection is streaming H264
    private volatile boolean mStreamingAAC;             // Indicates whether this connection is streaming AAC

    /**
     * Extends the ConnectionCallback to add StreamConnection specific notifications.
     */
    public interface ConnectionCallback extends TCPConnection.ConnectionCallback {
        /**
         * Notifies that the streaming has started on the specified connection.
         *
         * @param connection the connection that started the streaming
         * @param type       the stream type
         * @param id         the stream id
         */
        void onStreamStarted(StreamConnection connection, String type, long id);

        /**
         * Notifies that the streaming has stopped on the specified connection.
         *
         * @param connection the connection that stopped the streaming
         * @param type       the stream type
         * @param id         the stream id
         */
        void onStreamStopped(StreamConnection connection, String type, long id);

        /**
         * Notifies that an action has been requested by a client.
         *
         * @param connection the connection that stopped the streaming
         * @param action     the action that has to be handled
         * @param params     the action parameters
         */
        void onControlRequest(StreamConnection connection, String action, String params);
    }

    /**
     * Creates a new StreamConnection object.
     *
     * @param socket   the connection representing socket
     * @param callback the callback implemented by the client
     * @param data     additional data that can be attached to the object
     * @throws IOException if an error occurs while creating the input/output streams
     *                     or the socket is in an invalid state
     */
    public StreamConnection(Socket socket, @NotNull ConnectionCallback callback, Object data)
            throws IOException {
        super(socket, callback, data);
    }

    @Override
    public void close() {
        synchronized (this) {
            stopRTP(1);
            stopRTP(2);
        }
        super.close();
    }

    /**
     * Pushes a video data buffer to the queue.
     *
     * @param frame the video data
     * @return true if the data was added successfully, false otherwise
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean push(VideoFrame frame) throws InterruptedException {
        if (frame.isCompressed()) {
            if (!isStreamingH264())
                return false;
            if (mSliceQueue.offer(frame, QUEUE_WRITE_TIMEOUT, TimeUnit.MILLISECONDS))
                return true;
            Log.v(TAG, "cannot add the slice, the queue is full");
        } else {
            if (!isStreamingMJPEG())
                return false;
            if (mFrameQueue.offer(frame, QUEUE_WRITE_TIMEOUT, TimeUnit.MILLISECONDS))
                return true;
            Log.v(TAG, "cannot add the frame, the queue is full");
        }
        return false;
    }

    /**
     * Removes all frames from the queue.
     */
    public void clearFrames() {
        mFrameQueue.clear();
    }

    /**
     * Pops an uncompressed video frame from the queue.
     *
     * @return the frame data buffer, null on error or if the timeout expires
     * @throws InterruptedException if interrupted while waiting
     */
    public VideoFrame popFrame() throws InterruptedException {
        VideoFrame frame = mFrameQueue.poll(QUEUE_READ_TIMEOUT, TimeUnit.MILLISECONDS);
        if (frame == null)
            Log.v(TAG, "cannot get the frame, the queue is empty");
        return frame;
    }

    /**
     * Removes all slices from the queue.
     */
    public void clearSlices() {
        mSliceQueue.clear();
    }

    /**
     * Pops a compressed slice from the queue.
     *
     * @return the slice data buffer, null on error or if the timeout expires
     * @throws InterruptedException if interrupted while waiting
     */
    public VideoFrame popSlice() throws InterruptedException {
        VideoFrame slice = mSliceQueue.poll(QUEUE_READ_TIMEOUT, TimeUnit.MILLISECONDS);
        if (slice == null)
            Log.v(TAG, "cannot get the slice, the queue is empty");
        return slice;
    }

    /**
     * Pushes an audio data buffer to the queue.
     *
     * @param data the audio data
     * @return true if the data was added successfully, false otherwise
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean push(AudioData data) throws InterruptedException {
        if (data.isCompressed()) {
            if (!isStreamingAAC())
                return false;
            if (mAudioQueue.offer(data, QUEUE_WRITE_TIMEOUT, TimeUnit.MILLISECONDS))
                return true;
            Log.v(TAG, "cannot add the audio, the queue is full");
        }
        return false;
    }

    /**
     * Removes all audio buffers from the queue.
     */
    public void clearAudio() {
        mAudioQueue.clear();
    }

    /**
     * Pops an audio buffer from the queue.
     *
     * @return the audio data buffer, null on error or if the timeout expires
     * @throws InterruptedException if interrupted while waiting
     */
    public AudioData popAudio() throws InterruptedException {
        AudioData data = mAudioQueue.poll(QUEUE_READ_TIMEOUT, TimeUnit.MILLISECONDS);
        if (data == null)
            Log.v(TAG, "cannot get the audio, the queue is empty");
        return data;
    }

    /**
     * Notify the client that the stream has started.
     *
     * @param type the stream type
     * @param id   the stream id
     */
    public void notifyStreamStarted(String type, long id) {
        switch (type) {
            case TYPE_MJPEG:
                mStreamingMJPEG = true;
                break;
            case TYPE_H264:
                mStreamingH264 = true;
                break;
            case TYPE_AAC:
                mStreamingAAC = true;
                break;
        }
        ((ConnectionCallback) mCallback).onStreamStarted(this, type, id);
    }

    /**
     * Notify the client that the stream has stopped.
     *
     * @param type the stream type
     * @param id   the stream id
     */
    public void notifyStreamStopped(String type, long id) {
        switch (type) {
            case TYPE_MJPEG:
                mStreamingMJPEG = false;
                break;
            case TYPE_H264:
                mStreamingH264 = false;
                break;
            case TYPE_AAC:
                mStreamingAAC = false;
                break;
        }
        ((ConnectionCallback) mCallback).onStreamStopped(this, type, id);
    }

    /**
     * Request an action to be executed.
     *
     * @param action the action that has to be handled
     * @param params the action parameters
     */
    public void requestControl(String action, String params) {
        ((ConnectionCallback) mCallback).onControlRequest(this, action, params);
    }

    /**
     * @return true when the MJPEG stream is playing, false otherwise
     */
    public boolean isStreamingMJPEG() {
        return mStreamingMJPEG;
    }

    /**
     * @return true when the H264 stream is playing, false otherwise
     */
    public boolean isStreamingH264() {
        return mStreamingH264;
    }

    /**
     * @return true when the AAC stream is playing, false otherwise
     */
    public boolean isStreamingAAC() {
        return mStreamingAAC;
    }

    /**
     * Creates a new RTSP session.
     *
     * @throws IllegalStateException if the session is already opened
     */
    public synchronized void openRTSPSession() throws IllegalStateException {
        if (mRTSPSession != null)
            throw new IllegalStateException("RTSP session already opened");
        mRTSPSession = Base64.encodeToString(
                String.valueOf(System.currentTimeMillis() + new Random().nextLong()).getBytes(),
                Base64.NO_WRAP | Base64.NO_PADDING);
        mRTPSeq = (short) new Random().nextInt();
    }

    /**
     * @return the RTSP session ID, null if the session is not opened
     */
    @Nullable
    public synchronized String getRTSPSessionID() {
        return mRTSPSession;
    }

    /**
     * @return the sequence number of the first RTP packet.
     */
    public synchronized int getRTPSeq() {
        return mRTPSeq & 0xFFFF;
    }

    /**
     * Closes the current RTSP session.
     */
    public synchronized void closeRTSPSession() {
        mRTSPSession = null;
    }

    /**
     * Prepares the video UDP streaming.
     *
     * @param clockRate the video clock rate in Hz.
     * @param rtpPort   the UDP client port to send the RTP video packets to
     * @param rtcpPort  the UDP client port to send the RTCP video packets to
     * @throws IllegalStateException if the session is not opened or the stream is already playing
     * @throws SocketException       if the video packetizer can't be created
     */
    public synchronized void setupVideoUDP(int clockRate, int rtpPort, int rtcpPort) throws IllegalStateException, SocketException {
        if (mRTSPSession == null)
            throw new IllegalStateException("RTSP session non opened");
        if (mUDPVideoPacketizer != null || mTCPVideoPacketizer != null)
            throw new IllegalStateException("already configured");
        mUDPVideoPacketizer = new UDPVideoPacketizer(this,
                getInetAddress(), rtpPort, rtcpPort,
                clockRate, mRTPSeq);
    }

    /**
     * @return the RTP video local port, 0 if the UDPVideoPacketizer has not started
     */
    public synchronized int getRTPVideoLocalPort() {
        return mUDPVideoPacketizer != null ? mUDPVideoPacketizer.getRTPLocalPort() : 0;
    }

    /**
     * @return the RTCP video local port, 0 if the UDPVideoPacketizer has not started
     */
    public synchronized int getRTCPVideoLocalPort() {
        return mUDPVideoPacketizer != null ? mUDPVideoPacketizer.getRTCPLocalPort() : 0;
    }

    /**
     * Prepares the video TCP streaming.
     *
     * @param clockRate   the video clock rate in Hz.
     * @param rtpChannel  the channel to send the RTP over TCP video packets to
     * @param rtcpChannel the channel to send the RTCP over TCP video packets to
     * @throws IllegalStateException if the session is not opened or the stream is already playing
     * @throws SocketException       if the video packetizer can't be created
     */
    public synchronized void setupVideoTCP(int clockRate, int rtpChannel, int rtcpChannel) throws IllegalStateException, SocketException {
        if (mRTSPSession == null)
            throw new IllegalStateException("RTSP session non opened");
        if (mUDPVideoPacketizer != null || mTCPVideoPacketizer != null)
            throw new IllegalStateException("already configured");
        mTCPVideoPacketizer = new TCPVideoPacketizer(this,
                rtpChannel, rtcpChannel,
                clockRate, mRTPSeq);
    }

    /**
     * Prepares the audio UDP streaming.
     *
     * @param clockRate the audio clock rate in Hz.
     * @param rtpPort   the UDP client port to send the RTP audio packets to
     * @param rtcpPort  the UDP client port to send the RTCP audio packets to
     * @throws IllegalStateException if the session is not opened or the stream is already playing
     * @throws SocketException       if the audio packetizer can't be created
     */
    public synchronized void setupAudioUDP(int clockRate, int rtpPort, int rtcpPort) throws IllegalStateException, SocketException {
        if (mRTSPSession == null)
            throw new IllegalStateException("RTSP session non opened");
        if (mUDPAudioPacketizer != null || mTCPAudioPacketizer != null)
            throw new IllegalStateException("already configured");
        mUDPAudioPacketizer = new UDPAudioPacketizer(this,
                getInetAddress(), rtpPort, rtcpPort,
                clockRate, mRTPSeq);
    }

    /**
     * @return the RTP audio local port, 0 if the UDPAudioPacketizer has not started
     */
    public synchronized int getRTPAudioLocalPort() {
        return mUDPAudioPacketizer != null ? mUDPAudioPacketizer.getRTPLocalPort() : 0;
    }

    /**
     * @return the RTCP audio local port, 0 if the UDPAudioPacketizer has not started
     */
    public synchronized int getRTCPAudioLocalPort() {
        return mUDPAudioPacketizer != null ? mUDPAudioPacketizer.getRTCPLocalPort() : 0;
    }

    /**
     * Prepares the audio TCP streaming.
     *
     * @param clockRate   the audio clock rate in Hz.
     * @param rtpChannel  the channel to send the RTP over TCP audio packets to
     * @param rtcpChannel the channel to send the RTCP over TCP audio packets to
     * @throws IllegalStateException if the session is not opened or the stream is already playing
     * @throws SocketException       if the audio packetizer can't be created
     */
    public synchronized void setupAudioTCP(int clockRate, int rtpChannel, int rtcpChannel) throws IllegalStateException, SocketException {
        if (mRTSPSession == null)
            throw new IllegalStateException("RTSP session non opened");
        if (mUDPAudioPacketizer != null || mTCPAudioPacketizer != null)
            throw new IllegalStateException("already configured");
        mTCPAudioPacketizer = new TCPAudioPacketizer(this,
                rtpChannel, rtcpChannel,
                clockRate, mRTPSeq);
    }

    /**
     * Starts playing the stream.
     *
     * @param stream the stream to start
     * @throws IllegalArgumentException if the specified {@code stream} is not valid
     * @throws IllegalStateException    if the session is not opened
     */
    public synchronized void playRTP(int stream)
            throws IllegalArgumentException, IllegalStateException {
        if (stream != 1 && stream != 2)
            throw new IllegalArgumentException("Invalid stream index");
        if (mRTSPSession == null)
            throw new IllegalStateException("RTSP session non opened");
        switch (stream) {
            case 1:
                if (mTCPVideoPacketizer != null)
                    mTCPVideoPacketizer.start();
                if (mUDPVideoPacketizer != null)
                    mUDPVideoPacketizer.start();
                break;
            case 2:
                if (mTCPAudioPacketizer != null)
                    mTCPAudioPacketizer.start();
                if (mUDPAudioPacketizer != null)
                    mUDPAudioPacketizer.start();
                break;
        }
    }

    /**
     * Stops playing the stream.<br>
     * Also close the RTSP session when done with all the streams.
     *
     * @param stream the stream to stop
     * @throws IllegalArgumentException if the specified {@code stream} is not valid
     */
    public synchronized void stopRTP(int stream) throws IllegalArgumentException {
        if (stream != 1 && stream != 2)
            throw new IllegalArgumentException("Invalid stream index");
        switch (stream) {
            case 1:
                if (mUDPVideoPacketizer != null) {
                    mUDPVideoPacketizer.close();
                    mUDPVideoPacketizer = null;
                }
                if (mTCPVideoPacketizer != null) {
                    mTCPVideoPacketizer.close();
                    mTCPVideoPacketizer = null;
                }
                break;
            case 2:
                if (mUDPAudioPacketizer != null) {
                    mUDPAudioPacketizer.close();
                    mUDPAudioPacketizer = null;
                }
                if (mTCPAudioPacketizer != null) {
                    mTCPAudioPacketizer.close();
                    mTCPAudioPacketizer = null;
                }
                break;
        }
        if (mUDPVideoPacketizer == null && mTCPVideoPacketizer == null &&
                mUDPAudioPacketizer == null && mTCPAudioPacketizer == null) {
            closeRTSPSession();
        }
    }
}
