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

package com.spynet.camera.network.Mangocam.API;

import android.content.Context;
import android.util.JsonReader;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;

import java.io.IOException;
import java.io.StringReader;

/**
 * Defines the Mangocam API RECONNECT command.<br>
 * Forces connection to a new host.
 */
public class ReconnectCommand implements MangocamCommand {

    private final Context mContext;
    private String mHost = null;

    /**
     * Creates a new ReconnectCommand object.
     *
     * @param context the context where the object is used
     */
    public ReconnectCommand(@NotNull Context context) {
        mContext = context;
    }

    @Override
    public String get() throws JSONException {
        return "OK {\"cmd\":\"RECONNECT\"}\r\n";
    }

    @Override
    public void parse(String command) throws IOException {
        if (!command.startsWith("RECONNECT "))
            throw new IllegalArgumentException("wrong command");
        command = command.substring(10);
        JsonReader jr = new JsonReader(new StringReader(command));
        jr.beginObject();
        while (jr.hasNext()) {
            switch (jr.nextName()) {
                case "host":
                    mHost = jr.nextString();
                    break;
                default:
                    jr.skipValue();
            }
        }
        jr.endObject();
    }

    /**
     * @return the host to send MJPEG to
     */
    public String getHost() {
        return mHost;
    }
}
