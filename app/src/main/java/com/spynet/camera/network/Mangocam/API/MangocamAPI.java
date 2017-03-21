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

/**
 * Defines some Mangocam Connect constants.
 */
public final class MangocamAPI {

    /**
     * Hidden constructor, the class cannot be instantiated.
     */
    private MangocamAPI() {
    }

    // Client protocol version
    public static final double VERSION = 1.0;

    // Base URL to access API
    public static final String BASE_URL = "/api/v2/upload/";

    // Connection timeout in seconds
    public static final int CONNECT_TIMEOUT = 10;

    // Default keepalive interval in seconds
    public static final int KEEPALIVE_INTERVAL = 30;

    // Maximum allowed bandwidth in kbps
    public static final int MAX_BANDWIDTH = 1000;

    // Maximum allowed MJPEG fps
    public static final int MJPEG_MAX_FPS = 5;

    // Default delay, in seconds, after which to stop sending and start again autonomously
    public static final int MJPEG_SPLIT_SECS = 5;
}
