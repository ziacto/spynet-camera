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

import java.io.InvalidObjectException;

/**
 * Defines the Arrow protocol PING message.<br>
 * Ping request used for connection health checks.<br>
 * It is periodically sent by both server and client.<br>
 * The message contains no data.
 */
public class PingMessage extends ControlMessage {

    /**
     * Creates a new PingMessage object.
     *
     * @param messageID message ID, used for request/response pairing
     */
    public PingMessage(int messageID, int errorCode) {
        super(messageID, TYPE_PING);
    }

    /**
     * Wraps a PingMessage object around a ControlMessage object.
     *
     * @param message the original ControlMessage object
     * @throws InvalidObjectException if the {@code message} doesn't match
     */
    public PingMessage(ControlMessage message) throws InvalidObjectException {
        super(message.mMessageID, message.mType);
        if (mType != TYPE_PING)
            throw new InvalidObjectException("The message is not a PING message");
    }
}
