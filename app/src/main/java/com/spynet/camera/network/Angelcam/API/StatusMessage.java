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

/**
 * Defines the Arrow protocol STATUS message.<br>
 * Response for the GET_STATUS request. It is sent by client.
 */
public class StatusMessage extends ControlMessage {

    private final int mFlags;               // Client status flags
    private final int mActiveSessions;      // Active sessions

    /**
     * Creates a new StatusMessage  object.
     *
     * @param messageID message ID, used for request/response pairing
     * @param flags     client status flags
     * @param sessions  number of active sessions
     */
    public StatusMessage(int messageID, int flags, int sessions) {
        super(messageID, TYPE_STATUS);
        mFlags = flags;
        mActiveSessions = sessions;
    }

    @Override
    public byte[] toByteArray() throws IOException {

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        // request_id - unsigned integer
        buffer.write(mMessageID >> 8);
        buffer.write(mMessageID);
        // status_flags
        buffer.write(mFlags >> 24);
        buffer.write(mFlags >> 16);
        buffer.write(mFlags >> 8);
        buffer.write(mFlags);
        // active_sessions - unsigned integer
        buffer.write(mActiveSessions >> 24);
        buffer.write(mActiveSessions >> 16);
        buffer.write(mActiveSessions >> 8);
        buffer.write(mActiveSessions);

        mData = buffer.toByteArray();
        return super.toByteArray();
    }
}
