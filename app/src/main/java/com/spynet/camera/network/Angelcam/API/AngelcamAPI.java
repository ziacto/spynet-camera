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

package com.spynet.camera.network.Angelcam.API;

/**
 * Defines some Angelcam Ready constants.
 */
public final class AngelcamAPI {

    /**
     * Hidden constructor, the class cannot be instantiated.
     */
    private AngelcamAPI() {
    }

    // Client protocol version
    public static final int VERSION = 0;

    // Connection timeout in seconds
    public static final int CONNECT_TIMEOUT = 10;

    // Default keepalive interval in seconds
    public static final int KEEPALIVE_INTERVAL = 60;
}
