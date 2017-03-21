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
 * Defines the Mangocam API STOP_MJPEG command.<br>
 * <br>
 * Command example:<br>
 * <br>
 * STOP_MJPEG {"host":"www107.mangocam.com","task":"201007331","node":"107"}
 */
public class StopMJPEGCommand implements MangocamCommand {

    private final Context mContext;
    private String mHost;
    private int mNode;
    private int mTask;

    /**
     * Creates a new StatsCommand object.
     *
     * @param context the context where the object is used
     */
    public StopMJPEGCommand(@NotNull Context context) {
        mContext = context;
    }

    @Override
    public String get() throws JSONException {
        return "OK {\"cmd\":\"STOP_MJPEG\"}\r\n";
    }

    @Override
    public void parse(String command) throws IOException {
        if (!command.startsWith("STOP_MJPEG "))
            throw new IllegalArgumentException("wrong command");
        command = command.substring(11);
        JsonReader jr = new JsonReader(new StringReader(command));
        jr.beginObject();
        while (jr.hasNext()) {
            switch (jr.nextName()) {
                case "host":
                    mHost = jr.nextString();
                    break;
                case "node":
                    mNode = jr.nextInt();
                    break;
                case "task":
                    mTask = jr.nextInt();
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

    /**
     * @return the node
     */
    public int getNode() {
        return mNode;
    }

    /**
     * @return the task ID, used by the upload server, the client will just relay
     */
    public int getTask() {
        return mTask;
    }
}
