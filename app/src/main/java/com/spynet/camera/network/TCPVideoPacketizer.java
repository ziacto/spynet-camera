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

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Defines the RTP packetizer to stream AVC video slices over a TCP channel.<br>
 * It implements RTP/RTCP over RTSP as defined in RFC 2326, session 10.12.<br>
 * Stream data such as RTP packets is encapsulated by an ASCII dollar
 * sign (24 hexadecimal), followed by a one-byte channel identifier,
 * followed by the length of the encapsulated binary data as a binary,
 * two-byte integer in network byte order. The stream data follows
 * immediately afterwards, without a CRLF, but including the upper-layer
 * protocol headers. Each $ block contains exactly one upper-layer
 * protocol data unit, e.g., one RTP packet.
 */
public class TCPVideoPacketizer extends RTPVideoPacketizer {

    private static final int RTP_PACKET_SIZE = 65000;

    private final byte[] mPacket;                   // The RTP packet
    private final int mRTPChannel;                  // The channel to use to send RTP packets
    private final int mRTCPChannel;                 // The channel to use to send RTCP packets

    /**
     * Creates a new TCPVideoPacketizer object.
     *
     * @param connection  the StreamConnection that owns the packetizer
     * @param rtpChannel  the channel used to send RTP packets
     * @param rtcpChannel the channel used to send RTCP packets
     * @param clock       the clock rate in Hz
     * @param seq         the sequence number of the first packet
     */
    public TCPVideoPacketizer(@NotNull StreamConnection connection,
                              int rtpChannel, int rtcpChannel,
                              int clock, int seq) {
        super(connection, clock, RTP_PACKET_SIZE, seq);
        mPacket = new byte[4 + RTP_PACKET_SIZE];
        mRTPChannel = rtpChannel;
        mRTCPChannel = rtcpChannel;
        mPacket[0] = '$';
    }

    @Override
    protected void rtpSend(byte[] data, int length) throws IOException {
        mPacket[1] = (byte) mRTPChannel;
        mPacket[2] = (byte) (length >> 8);
        mPacket[3] = (byte) length;
        System.arraycopy(data, 0, mPacket, 4, length);
        mConnection.write(mPacket, 0, 4 + length);
    }

    @Override
    protected void rtcpSend(byte[] data, int length) throws IOException {
        mPacket[1] = (byte) (mRTCPChannel);
        mPacket[2] = (byte) (length >> 8);
        mPacket[3] = (byte) length;
        System.arraycopy(data, 0, mPacket, 4, length);
        mConnection.write(mPacket, 0, 4 + length);
    }
}
