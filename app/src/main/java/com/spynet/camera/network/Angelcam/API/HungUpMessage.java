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
 * Defines the Arrow protocol HUP message.<br>
 * Hangup notification. It might be sent by both client and server to notify the other side of the
 * communication that a session with a given ID has been terminated.
 */
public class HungUpMessage extends ControlMessage {

    private final int mSessionID;     // Session ID identifying a connection to a local service
    private final int mErrorCode;     // Error code

    /**
     * Creates a new HungUpMessage object.
     *
     * @param messageID message ID, used for request/response pairing
     * @param sessionID ID of the session that has been terminated
     * @param errorCode error code
     */
    public HungUpMessage(int messageID, int sessionID, int errorCode) {
        super(messageID, TYPE_HUP);
        mSessionID = sessionID;
        mErrorCode = errorCode;
    }

    /**
     * Wraps an HungUpMessage object around a ControlMessage object.
     *
     * @param message the original ControlMessage object
     * @throws InvalidObjectException if the {@code message} doesn't match
     */
    public HungUpMessage(ControlMessage message) throws InvalidObjectException {
        super(message.mMessageID, message.mType);
        if (mType != TYPE_HUP)
            throw new InvalidObjectException("the message is not an HUP message");
        if (message.mData == null)
            throw new InvalidObjectException("the message has no data");
        if (message.mData.length != 8)
            throw new InvalidObjectException("the message contains invalid data");
        mSessionID = ((message.mData[1] & 0xff) << 16) |
                ((message.mData[2] & 0xff) << 8) | (message.mData[3] & 0xff);
        mErrorCode = ((message.mData[4] & 0xff) << 24) | ((message.mData[5] & 0xff) << 16) |
                ((message.mData[6] & 0xff) << 8) | (message.mData[7] & 0xff);
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

    @Override
    public int getSessionID() {
        return mSessionID;
    }

    /**
     * @return the error code
     */
    public int getErrorCode() {
        return mErrorCode;
    }
}
