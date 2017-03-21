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

import java.io.ByteArrayInputStream;

/**
 * A specialized {@link ByteArrayInputStream} for reading the contents of a byte array
 * as a bitstream.
 */
public class ByteArrayInputBitStream extends ByteArrayInputStream {

    private int mAvail = 0;             // Number of available bits in mData
    private int mData;                  // Next bits to consume (from a previous call)

    /**
     * Constructs a new ByteArrayInputBitStream on the byte array buf.
     *
     * @param buf the byte array to stream over.
     */
    public ByteArrayInputBitStream(byte[] buf) {
        super(buf);
    }

    /**
     * Reads some bits from the stream, starting from the MSB.
     *
     * @param bits number of bits to get, in the range 1-32
     * @return the bits read or -1 if the end of this stream has been reached.
     */
    public synchronized int read(int bits) {

        long res = 0;
        int left = bits;
        int pos = 32;

        if (bits < 1 || bits > 32)
            throw new IllegalArgumentException("bits not in range 1 to 32");

        while (true) {
            if (mAvail > 0) {
                pos -= mAvail;
                res |= (mData << pos) & 0xFFFFFFFFL;
                if (left > mAvail) {
                    left -= mAvail;
                } else {
                    mAvail -= left;
                    return (int) (res >> (32 - bits));
                }
            }
            if ((mData = read()) == -1)
                return -1;
            mAvail = 8;
        }
    }
}
