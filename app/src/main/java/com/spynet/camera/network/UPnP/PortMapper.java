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

package com.spynet.camera.network.UPnP;

import android.util.Log;

import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;
import org.bitlet.weupnp.PortMappingEntry;

import java.io.Closeable;

/**
 * Implements a UPnP client to add/remove port mapping to/from the default gateway.
 */
public class PortMapper implements Closeable {

    private static final String PORT_DESCRIPTION = "spyNet Camera";

    protected final String TAG = getClass().getSimpleName();

    private Thread mMappingThread;          // The thread use to process port mapping
    private GatewayDevice mGateway;         // The default gateway
    private int mPort;                      // The port number

    /**
     * Creates a new PortMapper object.<br>
     * Tries to find the default gateway on the network and to add a port forwarding rule for the
     * specified port. Internal and external ports will use the same number, the protoclo is TCP.
     *
     * @param port the number of the port to forward
     */
    public PortMapper(final int port) {
        mPort = port;
        System.setProperty("org.xml.sax.driver", "org.xmlpull.v1.sax2.Driver");
        mMappingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    GatewayDiscover discover = new GatewayDiscover();
                    discover.discover();
                    GatewayDevice d = discover.getValidGateway();
                    if (d != null) {
                        PortMappingEntry portMapping = new PortMappingEntry();
                        if (d.getSpecificPortMappingEntry(port, "TCP", portMapping)) {
                            if (!portMapping.getPortMappingDescription().equals(PORT_DESCRIPTION)) {
                                Log.w(TAG, "port was already mapped");
                            } else if (!d.deletePortMapping(port, "TCP")) {
                                Log.e(TAG, "failed to remove current port mapping");
                            }
                        }
                        if (d.addPortMapping(port, port,
                                d.getLocalAddress().getHostAddress(), "TCP", PORT_DESCRIPTION)) {
                            synchronized (this) {
                                mGateway = d;
                                mPort = port;
                            }
                        } else {
                            Log.e(TAG, "failed to add port mapping");
                        }
                    } else {
                        Log.w(TAG, "no valid gateway device found");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "unexpected exception while adding port mapping", e);
                }
            }
        });
        mMappingThread.start();
    }

    /**
     * Delete the port forwarding entry from the default gateway, added by the constructor.
     */
    @Override
    public void close() {

        final GatewayDevice d;
        final int port;

        try {
            mMappingThread.join();
            synchronized (this) {
                if (mGateway == null)
                    return;
                d = mGateway;
                port = mPort;
            }
            mMappingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (!d.deletePortMapping(port, "TCP")) {
                            Log.e(TAG, "failed to remove port mapping");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "unexpected exception while removing port mapping", e);
                    }
                }
            });
            mMappingThread.start();
            mMappingThread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "close operation interrupted");
        }
    }
}
