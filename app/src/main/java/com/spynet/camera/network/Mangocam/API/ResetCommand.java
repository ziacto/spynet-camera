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
 * Defines the Mangocam API RESET command.
 */
public class ResetCommand implements MangocamCommand {

    private final Context mContext;
    private String mErrorMessage = "";
    private int mErrorCode = 0;

    /**
     * Creates a new ResetCommand object.
     *
     * @param context the context where the object is used
     */
    public ResetCommand(@NotNull Context context) {
        mContext = context;
    }

    @Override
    public String get() throws JSONException {
        return "RESET\r\n";
    }

    @Override
    public void parse(String command) throws IOException, IllegalStateException {
        if (command.startsWith("OK ")) {
            String json = command.substring(3);
            JsonReader jr = new JsonReader(new StringReader(json));
            jr.beginObject();
            while (jr.hasNext()) {
                switch (jr.nextName()) {
                    case "cmd":
                        String cmd = jr.nextString();
                        if (!cmd.equals("RESET"))
                            throw new IllegalStateException("wrong cmd field");
                        break;
                    default:
                        jr.skipValue();
                }
            }
            jr.endObject();
        } else if (command.startsWith("ERR ")) {
            String json = command.substring(4);
            JsonReader jr = new JsonReader(new StringReader(json));
            jr.beginObject();
            while (jr.hasNext()) {
                switch (jr.nextName()) {
                    case "cmd":
                        String cmd = jr.nextString();
                        if (!cmd.equals("RESET"))
                            throw new IllegalStateException("wrong cmd field");
                        break;
                    case "message":
                        mErrorMessage = jr.nextString();
                        break;
                    case "code":
                        mErrorCode = jr.nextInt();
                        break;
                    default:
                        jr.skipValue();
                }
            }
            jr.endObject();
        } else {
            throw new IllegalStateException("wrong response");
        }
    }

    /**
     * @return the error message from the negative response
     */
    public String getErrorMessge() {
        return mErrorMessage;
    }

    /**
     * @return the error code from the negative response
     */
    public int getErrorCode() {
        return mErrorCode;
    }
}
