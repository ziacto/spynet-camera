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
 * Defines a Service record.
 */
public class ServiceRecord {

    // Arrow Control Protocol pseudo service
    public final static int TYPE_ARROW_CONTROL_PROTOCOL = 0x0000;
    // RTSP service
    public final static int TYPE_RTSP = 0x0001;
    // locked RTSP service with unknown path and credentials (usually
    // discovered using network scanner)
    public final static int TYPE_LOCKED_RTSP = 0x0002;
    // RTSP service with unknown path (usually discovered using
    // network scanner)
    public final static int TYPE_UNKNOWN_RTSP = 0x0003;
    // RTSP service with known path but unsupported streams (i.e. a
    // service without any H.264 or generic MPEG4 video streams)
    public final static int TYPE_UNSUPPORTED_RTSP = 0x0004;
    // HTTP service
    public final static int TYPE_HTTP = 0x0005;
    // HTTP service with a Motion JPEG endpoint
    public final static int TYPE_MJPEG = 0x0006;
    // locked HTTP service believed to have a Motion JPEG endpoint,
    // the path and the credentials are not known
    public final static int TYPE_LOCKED_MJPEG = 0x0007;
    // TCP service (used for manual forwarding)
    public final static int TYPE_TCP = 0xffff;

    private final int mServiceID;   // Service ID
    private final int mServiceType; // Service type
    private final byte[] mMAC;      // MAC address of the service host
    private final int mIP;          // IP address of the service host
    private final int mPort;        // Service port
    private final String mPath;     // URL path

    /**
     * Creates the end-of-table ServiceRecord.
     */
    public ServiceRecord() {
        mServiceID = 0x00;
        mServiceType = 0x00;
        mMAC = new byte[6];
        mIP = 0;
        mPort = 0;
        mPath = "";
    }

    /**
     * Creates a new ServiceRecord object.
     *
     * @param serviceID   the service ID
     * @param serviceType service type
     * @param mac         MAC address of the service host (00:00:00:00:00:00 in case it is
     *                    unknown)
     * @param ip          IP address of the service host
     * @param port        service port
     * @param path        URL path (e.g. "/some/path.sdp?param=value")
     * @throws IllegalArgumentException if parameters are not formatted correctly
     * @throws NullPointerException     if {@code mac} is null
     */
    public ServiceRecord(int serviceID, int serviceType, String mac, int ip, int port, String path)
            throws IllegalArgumentException, NullPointerException {
        mServiceID = serviceID;
        mServiceType = serviceType;
        if (mac == null)
            throw new NullPointerException("MAC cannot be null");
        String[] m = mac.split(":");
        if (m.length != 6)
            throw new IllegalArgumentException("invalid MAC address [" + mac + "]");
        mMAC = new byte[6];
        for (int i = 0; i < 6; i++)
            mMAC[i] = (byte) Integer.parseInt(m[i], 16);
        mIP = ip;
        mPort = port;
        mPath = path;
    }

    /**
     * @return this record's current contents as a byte array
     */
    public byte[] toByteArray() throws IOException {

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        // svc_id - unsigned integer
        buffer.write(mServiceID >> 8);
        buffer.write(mServiceID);
        // svc_type - unsigned integer
        buffer.write(mServiceType >> 8);
        buffer.write(mServiceType);
        // mac_addr - 6 octets
        buffer.write(mMAC[0]);
        buffer.write(mMAC[1]);
        buffer.write(mMAC[2]);
        buffer.write(mMAC[3]);
        buffer.write(mMAC[4]);
        buffer.write(mMAC[5]);
        // ip_version - unsigned integer (the only two valid options are 4 and 6)
        buffer.write(4);
        // ip_addr - 16 octets (left aligned)
        buffer.write(mIP);
        buffer.write(mIP >> 8);
        buffer.write(mIP >> 16);
        buffer.write(mIP >> 24);
        buffer.write(0);
        buffer.write(0);
        buffer.write(0);
        buffer.write(0);
        buffer.write(0);
        buffer.write(0);
        buffer.write(0);
        buffer.write(0);
        buffer.write(0);
        buffer.write(0);
        buffer.write(0);
        buffer.write(0);
        // port - unsigned integer
        buffer.write(mPort >> 8);
        buffer.write(mPort);
        // path - NULL terminated string
        buffer.write(mPath.getBytes());
        buffer.write(0);

        return buffer.toByteArray();
    }
}
