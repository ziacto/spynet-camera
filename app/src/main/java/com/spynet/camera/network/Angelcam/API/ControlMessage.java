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
 * Defines an Arrow Control protocol message.<br>
 * Arrow Control Protocol is a sub-protocol of the Arrow Protocol. It is used mainly for checking the
 * connection status, session handling and various notifications. All Control Protocol messages are
 * sent as an Arrow Message body with service ID 0x00 (session ID is ignored).
 */
public class ControlMessage extends Message {

    public final static int TYPE_ACK = 0x0000;
    public final static int TYPE_PING = 0x0001;
    public final static int TYPE_REGISTER = 0x0002;
    public final static int TYPE_REDIRECT = 0x0003;
    public final static int TYPE_UPDATE = 0x0004;
    public final static int TYPE_HUP = 0x0005;
    public final static int TYPE_RESET_SVC_TABLE = 0x0006;
    public final static int TYPE_SCAN_NETWORK = 0x0007;
    public final static int TYPE_GET_STATUS = 0x0008;
    public final static int TYPE_STATUS = 0x0009;
    public final static int TYPE_GET_SCAN_REPORT = 0x000a;
    public final static int TYPE_SCAN_REPORT = 0x000b;

    protected final int mMessageID;     // Message ID, used for request/response pairing
    protected final int mType;          // Message type
    protected byte[] mData;             // Message data

    /**
     * Creates a new ControlMessage object.
     *
     * @param messageID message ID, used for request/response pairing
     * @param type      message type
     */
    public ControlMessage(int messageID, int type) {
        super(ServiceRecord.TYPE_ARROW_CONTROL_PROTOCOL, 0);
        mMessageID = messageID;
        mType = type;
    }

    /**
     * Creates a new ControlMessage object.
     *
     * @param message the original Message object
     * @throws InvalidObjectException if the {@code message} doesn't match
     */
    public ControlMessage(Message message) throws InvalidObjectException {
        super(message.mServiceID, message.mSessionID);
        if (mServiceID != 0x00)
            throw new InvalidObjectException("the message is not an Arrow Control Protocol message");
        if (message.mBody == null)
            throw new InvalidObjectException("the message has an empty body");
        mMessageID = ((message.mBody[0] & 0xff) << 8) | (message.mBody[1] & 0xff);
        mType = ((message.mBody[2] & 0xff) << 8) | (message.mBody[3] & 0xff);
        mData = new byte[message.mBody.length - 4];
        System.arraycopy(message.mBody, 4, mData, 0, mData.length);
    }

    @Override
    public byte[] toByteArray() throws IOException {

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        // msg_id - unsigned integer
        buffer.write(mMessageID >> 8);
        buffer.write(mMessageID);
        // type - unsigned integer
        buffer.write(mType >> 8);
        buffer.write(mType);
        // data
        if (mData != null) buffer.write(mData);

        mBody = buffer.toByteArray();
        return super.toByteArray();
    }

    /**
     * @return the message ID
     */
    public int getMessageID() {
        return mMessageID;
    }

    /**
     * @return the message type
     */
    public int getType() {
        return mType;
    }
}
