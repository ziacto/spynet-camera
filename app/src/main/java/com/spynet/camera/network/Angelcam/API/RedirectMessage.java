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
 * Defines the Arrow protocol REDIRECT message.<br>
 * The message can be sent by server to redirect a client to another server. No ACK is expected, the
 * server closes connection immediately after sending the redirect command. Message data consist
 * of a "host:port" NULL terminated string.
 */
public class RedirectMessage extends ControlMessage {

    private final String mHost;             // Host
    private final int mPort;                // Port

    /**
     * Wraps a RedirectMessage object around a ControlMessage object.
     *
     * @param message the original ControlMessage object
     * @throws InvalidObjectException if the {@code message} doesn't match
     */
    public RedirectMessage(ControlMessage message) throws InvalidObjectException {
        super(message.mMessageID, message.mType);
        if (mType != TYPE_REDIRECT)
            throw new InvalidObjectException("the message is not a REDIRECT message");
        if (message.mData == null)
            throw new InvalidObjectException("the message has no data");
        if (message.mData.length < 2 || message.mData[message.mData.length - 1] != 0)
            throw new InvalidObjectException("the message contains invalid data");
        String host = new String(message.mData, 0, message.mData.length - 1);
        String[] c = host.split(":");
        if (c.length != 2)
            throw new InvalidObjectException("the message contains invalid data");
        mHost = c[0];
        mPort = Integer.parseInt(c[1]);
    }

    /**
     * @return the host name
     */
    public String getHost() {
        return mHost;
    }

    /**
     * @return the port number
     */
    public int getPort() {
        return mPort;
    }
}
