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
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Defines the Mangocam API INFO command.<br>
 * <br>
 * Command example:<br>
 * <br>
 * INFO []
 */
public class InfoCommand implements MangocamCommand {

    private final Context mContext;
    private final int mImageWidth, mImageHeight;

    /**
     * Creates a new InfoCommand object.
     *
     * @param context the context where the object is used
     * @param width   image width
     * @param height  image height
     */
    public InfoCommand(@NotNull Context context, int width, int height) {
        mContext = context;
        mImageWidth = width;
        mImageHeight = height;
    }

    @Override
    public String get() throws JSONException {
        JSONObject jObject = new JSONObject();
        jObject.put("cmd", "INFO");
        jObject.put("vendor", "spyNet");
        jObject.put("s_ver", com.spynet.camera.BuildConfig.VERSION_NAME);
        jObject.put("os_name", "Android");
        jObject.put("os_ver", android.os.Build.VERSION.RELEASE);
        Calendar cal = Calendar.getInstance();
        TimeZone tz = cal.getTimeZone();
        jObject.put("tz", tz.getID());
        jObject.put("max_res_x", mImageWidth);
        jObject.put("max_res_y", mImageHeight);
        jObject.put("aspect", Math.floor((double) mImageWidth / (double) mImageHeight * 100.0) / 100.0);
        return "OK " + jObject.toString() + "\r\n";
    }

    @Override
    public void parse(String command) throws IOException {
        // Nothing to do
    }
}
