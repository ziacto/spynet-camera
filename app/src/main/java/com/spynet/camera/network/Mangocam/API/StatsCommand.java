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

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Defines the Mangocam API STATS command.<br>
 * <br>
 * Command example:<br>
 * <br>
 * STATS []
 */
public class StatsCommand implements MangocamCommand {

    private final Context mContext;
    private final long mUptime;
    private final boolean mUploading;

    /**
     * Creates a new InfoCommand object.
     *
     * @param context   the context where the object is used
     * @param uptime    how long the client is on in seconds
     * @param uploading whether the client is uploading images to the server
     */
    public StatsCommand(@NotNull Context context, long uptime, boolean uploading) {
        mContext = context;
        mUptime = uptime;
        mUploading = uploading;
    }

    @Override
    public String get() throws JSONException {
        JSONObject jObject = new JSONObject();
        jObject.put("cmd", "STATS");
        jObject.put("uptime", mUptime);
        jObject.put("uploading", mUploading ? 1 : 0);
        return "OK " + jObject.toString() + "\r\n";
    }

    @Override
    public void parse(String command) throws IOException {
        // Nothing to do
    }
}
