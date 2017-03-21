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
import java.util.UUID;

/**
 * Defines the Arrow protocol REGISTER message.<br>
 * Registration request sent by client when a new connection is established.<br>
 * The message contains client credentials and service table.<br>
 * ACK is expected.
 */
public class RegisterMessage extends ControlMessage {

    private final UUID mUUID;                   // UUID identifying the Arrow Client
    private final UUID mPassphrase;             // Key for identity verification
    private final byte[] mMAC;                  // MAC address of a client's network interface
    private final ServiceRecord[] mSvcTable;    // Client's service table

    /**
     * Creates a new RegisterMessage object.
     *
     * @param messageID  message ID, used for request/response pairing
     * @param uuid       UUID identifying the Arrow Client
     * @param passphrase key for identity verification
     * @param mac        MAC address of a client's network interface
     * @param svc        client's service table
     * @throws IllegalArgumentException if parameters are not formatted correctly
     * @throws NullPointerException     if {@code uuid}, {@code passphrase} or {@code mac} are null
     */
    public RegisterMessage(int messageID, String uuid, String passphrase, String mac, ServiceRecord[] svc)
            throws IllegalArgumentException, NullPointerException {
        super(messageID, TYPE_REGISTER);
        if (uuid == null)
            throw new NullPointerException("UUID cannot be null");
        if (passphrase == null)
            throw new NullPointerException("passphrase cannot be null");
        if (mac == null)
            throw new NullPointerException("MAC cannot be null");
        mUUID = UUID.fromString(uuid);
        mPassphrase = UUID.fromString(passphrase);
        String[] m = mac.split(":");
        if (m.length != 6)
            throw new IllegalArgumentException("invalid MAC address [" + mac + "]");
        mMAC = new byte[6];
        for (int i = 0; i < 6; i++)
            mMAC[i] = (byte) Integer.parseInt(m[i], 16);
        mSvcTable = svc;
    }

    @Override
    public byte[] toByteArray() throws IOException {

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        long msb, lsb;

        // uuid - 16 octets
        msb = mUUID.getMostSignificantBits();
        lsb = mUUID.getLeastSignificantBits();
        buffer.write((int) (msb >> 56));
        buffer.write((int) (msb >> 48));
        buffer.write((int) (msb >> 40));
        buffer.write((int) (msb >> 32));
        buffer.write((int) (msb >> 24));
        buffer.write((int) (msb >> 16));
        buffer.write((int) (msb >> 8));
        buffer.write((int) (msb));
        buffer.write((int) (lsb >> 56));
        buffer.write((int) (lsb >> 48));
        buffer.write((int) (lsb >> 40));
        buffer.write((int) (lsb >> 32));
        buffer.write((int) (lsb >> 24));
        buffer.write((int) (lsb >> 16));
        buffer.write((int) (lsb >> 8));
        buffer.write((int) (lsb));
        // mac_addr - 6 octets
        buffer.write(mMAC[0]);
        buffer.write(mMAC[1]);
        buffer.write(mMAC[2]);
        buffer.write(mMAC[3]);
        buffer.write(mMAC[4]);
        buffer.write(mMAC[5]);
        // passphrase - 16 octets
        msb = mPassphrase.getMostSignificantBits();
        lsb = mPassphrase.getLeastSignificantBits();
        buffer.write((int) (msb >> 56));
        buffer.write((int) (msb >> 48));
        buffer.write((int) (msb >> 40));
        buffer.write((int) (msb >> 32));
        buffer.write((int) (msb >> 24));
        buffer.write((int) (msb >> 16));
        buffer.write((int) (msb >> 8));
        buffer.write((int) (msb));
        buffer.write((int) (lsb >> 56));
        buffer.write((int) (lsb >> 48));
        buffer.write((int) (lsb >> 40));
        buffer.write((int) (lsb >> 32));
        buffer.write((int) (lsb >> 24));
        buffer.write((int) (lsb >> 16));
        buffer.write((int) (lsb >> 8));
        buffer.write((int) (lsb));
        // svc_table
        // The table ends with a record with service ID 0x00 and service type 0x00
        // (the other fields are ignored)
        if (mSvcTable != null) {
            for (ServiceRecord svc : mSvcTable) {
                buffer.write(svc.toByteArray());
            }
        }
        buffer.write((new ServiceRecord()).toByteArray());

        mData = buffer.toByteArray();
        return super.toByteArray();
    }
}
