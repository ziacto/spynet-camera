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

import android.util.Log;

import com.spynet.camera.common.TimeStamp;
import com.spynet.camera.common.Utils;
import com.spynet.camera.media.AudioData;

import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketException;
import java.util.Random;

/**
 * Defines the RTP packetizer to stream AAC audio.<br>
 * To be subclassed to implement the transmission protocol.
 */
public abstract class RTPAudioPacketizer implements Closeable {

    protected final String TAG = getClass().getSimpleName();

    protected final int CLOSE_TIMEOUT = 1000;       // Close timeout in ms
    protected final int RTCP_INTERVAL = 2500;       // RTCP interval in ms

    protected final StreamConnection mConnection;   // The connection that owns the packetizer

    private final int mPacketSize;                  // Maximum RTP packet size
    private final int mClockRate;                   // Clock rate in Hz
    private final int mSSRC;                        // The Synchronization source (SSRC)
    private int mSeq;                               // First packet sequence number
    private Thread mStreamThread;                   // The streaming thread

    /**
     * Creates a new RTPAudioPacketizer object
     *
     * @param connection the StreamConnection that owns the packetizer
     * @param clock      the clock rate in Hz
     * @param packetSize maximum RTP packets size
     * @param seq        the sequence number of the first packet
     */
    public RTPAudioPacketizer(@NotNull StreamConnection connection, int clock, int packetSize, int seq) {
        mConnection = connection;
        mClockRate = clock;
        mPacketSize = packetSize;
        mSSRC = new Random().nextInt();
        mSeq = seq;
    }

    /**
     * Starts to send packets.<br>
     * RTPAudioPacketizer is intended to be started only once.
     */
    public synchronized void start() {
        if (mStreamThread != null)
            throw new IllegalStateException("already started");
        mStreamThread = new Thread(new Runnable() {
            @Override
            public void run() {
                doSend();
            }
        });
        mStreamThread.start();
    }

    /**
     * Sends an RTP packet.
     *
     * @param data   RTP data to send
     * @param length number of bytes to send
     * @throws IOException if sending the data fails
     */
    protected abstract void rtpSend(byte[] data, int length) throws IOException;

    /**
     * Sends an RTCP packet.
     *
     * @param data   RTCP data to send
     * @param length number of bytes to send
     * @throws IOException if sending the data fails
     */
    protected abstract void rtcpSend(byte[] data, int length) throws IOException;

    /**
     * Closes the packetizer.<br>
     * In the case of TCP transmission, the thread may be stuck in sending data that are not
     * read by the client. In this situation, the thread will not terminate now, but will be
     * terminated later when the socket will be closed.
     */
    @Override
    public synchronized void close() {
        try {
            if (mStreamThread == null)
                return;
            mStreamThread.interrupt();
            mStreamThread.join(CLOSE_TIMEOUT);
            if (mStreamThread.isAlive())
                Log.w(TAG, "cannot close the packetizer now");
        } catch (Exception e) {
            Log.e(TAG, "unexpected exception while closing the packetizer", e);
        }
    }

    /**
     * @return the Synchronization source (SSRC)
     */
    public int getSSRC() {
        return mSSRC;
    }

    /**
     * Streams the slices using RTP protocol.
     */
    private void doSend() {

        final long id = Utils.getUniqueID();
        long ntp, timestamp;
        long lastRTCP = 0;
        byte[] data, rtp, rtcp, au_header;
        int packets = 0;
        int octets = 0;

        // Prepare the RTP packet header
        rtp = new byte[mPacketSize];
        rtp[0] = (byte) 0x80;   // V=2, P=0, X=0, CC=0
        rtp[1] = (byte) 96;     // M=0, PT=96
        rtp[8] = (byte) (mSSRC >> 24);
        rtp[9] = (byte) (mSSRC >> 16);
        rtp[10] = (byte) (mSSRC >> 8);
        rtp[11] = (byte) (mSSRC);

        // Prepare the RTCP packet header
        rtcp = new byte[28];
        rtcp[0] = (byte) 0x80;  // V=2, P=0, RC=0
        rtcp[1] = (byte) 200;   // PT=SR=200
        rtcp[2] = (byte) 0;     // Length=6 (32-bit words minus one)
        rtcp[3] = (byte) 6;     //
        rtcp[4] = (byte) (mSSRC >> 24);
        rtcp[5] = (byte) (mSSRC >> 16);
        rtcp[6] = (byte) (mSSRC >> 8);
        rtcp[7] = (byte) (mSSRC);

        // Prepare the AU Header Section
        au_header = new byte[4];
        au_header[0] = 0x00;    // AU-headers-length (length in bits of AU-headers)
        au_header[1] = 0x10;    // 16 bits -> sizelength=13;indexlength=3;indexdeltalength=3

        // Streaming loop
        Log.d(TAG, "packetizer started");
        mConnection.clearAudio();
        mConnection.notifyStreamStarted(StreamConnection.TYPE_AAC, id);
        try {
            while (!Thread.currentThread().isInterrupted()) {
                // Handle RTCP
                timestamp = TimeStamp.getTimeStamp();
                if (timestamp - lastRTCP > RTCP_INTERVAL * 1000L) {
                    lastRTCP = timestamp;
                    ntp = TimeStamp.getNTPTimeStamp();
                    timestamp = timestamp * mClockRate / 1000000L;
                    // Compose the message
                    rtcp[8] = (byte) (ntp >> 56);           // NTP timestamp
                    rtcp[9] = (byte) (ntp >> 48);
                    rtcp[10] = (byte) (ntp >> 40);
                    rtcp[11] = (byte) (ntp >> 32);
                    rtcp[12] = (byte) (ntp >> 24);
                    rtcp[13] = (byte) (ntp >> 16);
                    rtcp[14] = (byte) (ntp >> 8);
                    rtcp[15] = (byte) (ntp);
                    rtcp[16] = (byte) (timestamp >> 24);    // RTP timestamp
                    rtcp[17] = (byte) (timestamp >> 16);
                    rtcp[18] = (byte) (timestamp >> 8);
                    rtcp[19] = (byte) (timestamp);
                    rtcp[20] = (byte) (packets >> 24);      // Sender's packet count
                    rtcp[21] = (byte) (packets >> 16);
                    rtcp[22] = (byte) (packets >> 8);
                    rtcp[23] = (byte) (packets);
                    rtcp[24] = (byte) (octets >> 24);       // Sender's octet count
                    rtcp[25] = (byte) (octets >> 16);
                    rtcp[26] = (byte) (octets >> 8);
                    rtcp[27] = (byte) (octets);
                    // Send RTCP SR message
                    rtcpSend(rtcp, rtcp.length);
                }
                // Get audio data from the queue
                AudioData audio = mConnection.popAudio();
                if (audio == null)
                    return;
                data = audio.getData();
                if (data.length == 0)
                    continue;
                // Set the timestamp
                timestamp = audio.getTimestamp() * mClockRate / 1000000L;
                rtp[4] = (byte) (timestamp >> 24);
                rtp[5] = (byte) (timestamp >> 16);
                rtp[6] = (byte) (timestamp >> 8);
                rtp[7] = (byte) (timestamp);
                // Set the AU-header
                au_header[2] = (byte) (data.length >> 5);   // sizelength=13;indexlength=3;indexdeltalength=3
                au_header[3] = (byte) (data.length << 3);   // AU-Index = 0
                // Send the NAL
                if (data.length + 12 <= mPacketSize) {
                    sendAudioMuxElement(rtp, au_header, data);
                } else {
                    // RFC 6416, session 16.3: It is RECOMMENDED to put one audioMuxElement in each RTP packet.
                    throw new UnsupportedOperationException("fragmentation of MPEG-4 audio bitstream is not supported");
                }
                packets++;
                octets += data.length;
            }
        } catch (InterruptedException e) {
            Log.v(TAG, "stream interrupted");
        } catch (SocketException e) {
            Log.v(TAG, "socket closed");
        } catch (Exception e) {
            Log.e(TAG, "unexpected exception", e);
        } finally {
            mConnection.notifyStreamStopped(StreamConnection.TYPE_AAC, id);
            Log.d(TAG, "packetizer stopped");
        }
    }

    /**
     * Sends an single audioMuxElement.
     */
    private void sendAudioMuxElement(
            byte[] rtp, byte[] au_header, byte[] slice)
            throws IOException {
        rtp[1] |= 0x80; // M=1
        rtp[2] = (byte) (mSeq >> 8);
        rtp[3] = (byte) (mSeq);
        System.arraycopy(au_header, 0, rtp, 12, au_header.length);
        System.arraycopy(slice, 0, rtp, 12 + au_header.length, slice.length);
        rtpSend(rtp, 12 + au_header.length + slice.length);
        ++mSeq;
    }
}
