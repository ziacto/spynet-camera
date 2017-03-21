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
 * Defines the Mangocam API SEND_MJPEG command.<br>
 * <br>
 * Command example:<br>
 * <br>
 * SEND_MJPEG {"host":"www107.mangocam.com","task":201007331,"node":107,"sess":9955409,
 * "time":1465247990,"split_secs":3600,"rate":"1","max_bw":500,"res_x":1280,"res_y":720}
 */
public class SendMJPEGCommand implements MangocamCommand {

    private final Context mContext;
    private String mHost;
    private int mNode;
    private int mTask;
    private int mSession;
    private long mTime;
    private int mSplitSecs = MangocamAPI.MJPEG_SPLIT_SECS;
    private double mRate = MangocamAPI.MJPEG_MAX_FPS;
    private int mMaxBW = MangocamAPI.MAX_BANDWIDTH;
    private int mResX, mResY;

    /**
     * Creates a new SendMJPEGCommand object.
     *
     * @param context the context where the object is used
     */
    public SendMJPEGCommand(@NotNull Context context) {
        mContext = context;
    }

    @Override
    public String get() throws JSONException {
        return "OK {\"cmd\":\"SEND_MJPEG\"}\r\n";
    }

    @Override
    public void parse(String command) throws IOException {
        if (!command.startsWith("SEND_MJPEG "))
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
                case "sess":
                    mSession = jr.nextInt();
                    break;
                case "time":
                    mTime = jr.nextLong();
                    break;
                case "split_secs":
                    mSplitSecs = jr.nextInt();
                    break;
                case "rate":
                    mRate = jr.nextDouble();
                    break;
                case "max_bw":
                    mMaxBW = jr.nextInt();
                    break;
                case "res_x":
                    mResX = jr.nextInt();
                    break;
                case "res_y":
                    mResY = jr.nextInt();
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

    /**
     * @return the session ID, further identify the client, used by the upload server,
     * the client will just relay
     */
    public int getSession() {
        return mSession;
    }

    /**
     * @return the Mangocam time, used by the upload server, the client will just relay
     */
    public long getTime() {
        return mTime;
    }

    /**
     * @return number of seconds after which to stop sending and start again autonomously
     */
    public int getSplitSec() {
        return mSplitSecs;
    }

    /**
     * @return required frame rate
     */
    public double getRate() {
        return mRate;
    }

    /**
     * @return maximum allowed bandwidth
     */
    public int getMaxBandwidth() {
        return mMaxBW;
    }

    /**
     * @return the required resolution X
     */
    public int getResolutionX() {
        return mResX;
    }

    /**
     * @return the required resolution Y
     */
    public int getResolutionY() {
        return mResY;
    }

    /**
     * @return the POST query to send to the server to initiate MJPEG upload
     */
    public String getQuery() {
        String path = MangocamAPI.BASE_URL +
                "?mode=mjpeg" +
                "&task=" + mTask +
                "&time=" + mTime +
                "&sess=" + mSession;
        return "POST " + path + " HTTP/1.1\r\n" +
                "Host: " + mHost + "\r\n" +
                "Content-type: application/x-www-form-urlencoded\r\n" +
                "Content-length: 1000000000000\r\n" +
                "Connection: close\r\n" +
                "\r\n";
    }
}
