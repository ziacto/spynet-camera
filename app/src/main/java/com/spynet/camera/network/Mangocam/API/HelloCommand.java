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

import com.spynet.camera.ui.SettingsActivity;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Defines the Mangocam API HELLO command.<br>
 * <br>
 * The client needs to issue a HELLO command with required parameters (version and uuid) and
 * wait for the server to reply.<br>
 * <br>
 * The initial connect (HELLO command) will fail with ERR and the server will respond with a 6 digit
 * PIN number, which is used to authenticate / bind the camera to a Mangocam user account / camera id
 * and is valid for only 5 minutes. After the binding process has completed, the PIN is not required
 * anymore.
 * If the camera (within Mangocam) or the Mangocam user account has been deleted, the UUID will also
 * be purged from the system and the client software will be required to be reset (and issued with
 * a new PIN).
 * Once the client has received the PIN from the server, the client should display the PIN on screen
 * and ask the user to create a new cloud camera on Mangocam, enter the PIN when requested and click
 * save.
 * The client should have a button to continue / re-connect to the server once the association is
 * complete, which should return OK this time together with the keep alive value and a list of
 * additional servers to connect to.<br>
 * <br>
 * In some cases a reset may be required for the app, which will create a new UUID and PIN and forget
 * the old details. The client does not need to remember the PIN, but does need to save and reuse
 * the UUID.<br>
 * After 6 months of inactivity (no HELLO connect using a certain UUID), the server can / will purge
 * the UUID and camera id / account assosication from the database and a new PIN / association
 * request is required.<br>
 * <br>
 * Each client uses a unique 128 byte alphanumeric ID (not visible to the user), the Unix epoch
 * timestamp and a globally unique identifier such as the hardware MAC address of the unit should be
 * part of the string.
 * The UUID of the client does not change until the software has been reinstalled or reset.
 * If possible, the UUID should be retained during client software upgrades.<br>
 * <br>
 * Response examples:<br>
 * <br>
 * OK {"cmd":"HELLO","servers":["www105.mangocam.com","www103.mangocam.com","www201.mangocam.com",
 * "www102.mangocam.com"],"keepalive":30,"version":0.091}<br>
 * <br>
 * ERR {"cmd":"HELLO", "message":"please add a new cloud camera in Mangocam using pin 491278",
 * "code": 2, "pin":"491278", "link":"https://www.mangocam.com/config/cameras/"}
 */
public class HelloCommand implements MangocamCommand {

    private final Context mContext;
    private final String mUUID;
    private int mPIN = 0;
    private int mKeepAlive = MangocamAPI.KEEPALIVE_INTERVAL;
    private ArrayList<String> mHosts = new ArrayList<>();
    private String mErrorMessage = "";
    private int mErrorCode = 0;
    private String mLink = "";

    /**
     * Creates a new HelloCommand object.
     *
     * @param context the context where the object is used
     * @param uuid    the UUID to use, if null or not valid a new UUI will be generated.
     */
    public HelloCommand(@NotNull Context context, String uuid) {
        mContext = context;
        mUUID = (uuid != null && uuid.length() == 128) ? uuid : generateUUID();
        mHosts.add(SettingsActivity.getMangoHost(mContext));
    }

    @Override
    public String get() throws JSONException {
        JSONObject jObject = new JSONObject();
        jObject.put("version", MangocamAPI.VERSION);
        jObject.put("uuid", mUUID);
        if (mPIN != 0) jObject.put("pin", mPIN);
        return "HELLO " + jObject.toString() + "\r\n";
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
                        if (!cmd.equals("HELLO"))
                            throw new IllegalStateException("wrong cmd field");
                        break;
                    case "keepalive":
                        mKeepAlive = jr.nextInt();
                        break;
                    case "servers":
                        jr.beginArray();
                        mHosts.clear();
                        while (jr.hasNext())
                            mHosts.add(jr.nextString());
                        mHosts.add(SettingsActivity.getMangoHost(mContext));
                        jr.endArray();
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
                        if (!cmd.equals("HELLO"))
                            throw new IllegalStateException("wrong cmd field");
                        break;
                    case "message":
                        mErrorMessage = jr.nextString();
                        break;
                    case "code":
                        mErrorCode = jr.nextInt();
                        break;
                    case "pin":
                        mPIN = jr.nextInt();
                        break;
                    case "link":
                        mLink = jr.nextString();
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
     * @return the current UUID
     */
    public String getUUID() {
        return mUUID;
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

    /**
     * @return the link from the negative response
     */
    public String getLink() {
        return mLink;
    }

    /**
     * @return the PIN from the negative response
     */
    public int getPIN() {
        return mPIN;
    }

    /**
     * @return the keep-alive interval from the positive response in seconds
     */
    public int getKeepAliveInterval() {
        return mKeepAlive;
    }

    /**
     * @return the list of available hosts from the positive response, including the default
     * server set by preferences
     */
    public List<String> getHosts() {
        return mHosts;
    }

    /**
     * Generates a random 128 character alphanumeric UUID.
     */
    private String generateUUID() {
        Random random = new Random(System.nanoTime());
        String characters = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String uuid = String.valueOf(System.nanoTime());
        for (int p = uuid.length(); p < 128; p++) {
            int pos = random.nextInt(characters.length());
            uuid += characters.substring(pos, pos + 1);
        }
        return uuid;
    }
}
