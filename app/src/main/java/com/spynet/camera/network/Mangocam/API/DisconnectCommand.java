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
import java.util.Random;

/**
 * Defines the Mangocam API DISCONNECT command.<br>
 */
public class DisconnectCommand implements MangocamCommand {

    private final Context mContext;
    private int mWaitSec = 0, mWaitSecMin = 0, mWaitSecMax = 0;
    private String mWaitReason = "";

    /**
     * Creates a new DisconnectCommand object.
     *
     * @param context the context where the object is used
     */
    public DisconnectCommand(@NotNull Context context) {
        mContext = context;
    }

    @Override
    public String get() throws JSONException {
        return "OK {\"cmd\":\"DISCONNECT\"}\r\n";
    }

    @Override
    public void parse(String command) throws IOException {
        if (!command.startsWith("DISCONNECT "))
            throw new IllegalArgumentException("wrong command");
        command = command.substring(11);
        JsonReader jr = new JsonReader(new StringReader(command));
        jr.beginObject();
        while (jr.hasNext()) {
            switch (jr.nextName()) {
                case "wait_time_secs":
                    mWaitSec = jr.nextInt();
                    break;
                case "wait_time_secs_min":
                    mWaitSecMin = jr.nextInt();
                    break;
                case "wait_time_secs_max":
                    mWaitSecMax = jr.nextInt();
                    break;
                case "wait_reason":
                    mWaitReason = jr.nextString();
                    break;
                default:
                    jr.skipValue();
            }
        }
        jr.endObject();
    }

    /**
     * @return the requested wait delay in seconds before to reconnect
     */
    public int getWaitDelay() {
        if (mWaitSecMax > mWaitSecMin) {
            Random random = new Random(System.currentTimeMillis());
            return mWaitSecMin + random.nextInt(mWaitSecMax - mWaitSecMin);
        } else {
            return mWaitSec;
        }
    }

    /**
     * @return the reason to wait before to reconnect
     */
    public String getWaitReason() {
        return mWaitReason;
    }
}
