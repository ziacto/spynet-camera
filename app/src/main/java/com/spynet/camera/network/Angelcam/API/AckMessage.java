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

package com.spynet.camera.network.Angelcam.API;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;

/**
 * Defines the Arrow protocol ACK message.<br>
 * The message is a response to another Control Protocol message.<br>
 * It might be sent by both client and server.<br>
 * The message consists of 32bit unsigned integer used as an error code.
 */
public class AckMessage extends ControlMessage {

    // OK
    public final static int ERROR_NO_ERROR = 0x00000000;
    // Unsupported version of the Arrow protocol
    public final static int ERROR_UNSUPPORTED_PROTOCOL_VERSION = 0x00000001;
    // Access denied (client is not registered)
    public final static int ERROR_UNAUTHORIZED = 0x00000002;
    // Cannot connect to a given service
    public final static int ERROR_CONNECTION_ERROR = 0x00000003;
    // unsupported method
    public final static int ERROR_UNSUPPORTED_METHOD = 0x00000004;
    // internal server error
    public final static int ERROR_INTERNAL_SERVER_ERROR = 0xffffffff;

    private final int mErrorCode;           // Error code

    /**
     * Creates a new AckMessage object.
     *
     * @param messageID message ID, used for request/response pairing
     * @param errorCode error code
     */
    public AckMessage(int messageID, int errorCode) {
        super(messageID, TYPE_ACK);
        mErrorCode = errorCode;
    }

    /**
     * Wraps an AckMessage object around a ControlMessage object.
     *
     * @param message the original ControlMessage object
     * @throws InvalidObjectException if the {@code message} doesn't match
     */
    public AckMessage(ControlMessage message) throws InvalidObjectException {
        super(message.mMessageID, message.mType);
        if (mType != TYPE_ACK)
            throw new InvalidObjectException("the message is not an ACK message");
        if (message.mData == null)
            throw new InvalidObjectException("the message has no data");
        if (message.mData.length != 4)
            throw new InvalidObjectException("the message contains invalid data");
        mErrorCode = ((message.mData[0] & 0xff) << 24) | ((message.mData[1] & 0xff) << 16) |
                ((message.mData[2] & 0xff) << 8) | (message.mData[3] & 0xff);
    }

    @Override
    public byte[] toByteArray() throws IOException {

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        // error_code - 32bit unsigned integer
        buffer.write(mErrorCode >> 24);
        buffer.write(mErrorCode >> 16);
        buffer.write(mErrorCode >> 8);
        buffer.write(mErrorCode);

        mData = buffer.toByteArray();
        return super.toByteArray();
    }

    /**
     * @return the error code
     */
    public int getErrorCode() {
        return mErrorCode;
    }
}
