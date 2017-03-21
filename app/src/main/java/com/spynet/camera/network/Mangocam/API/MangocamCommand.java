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

import org.json.JSONException;

import java.io.IOException;

/**
 * Defines the interface implemented by the Mangocam Connect protocol commands.
 */
public interface MangocamCommand {
    /**
     * Formats the command / reply string to send to the server.
     *
     * @return the string representing the command / reply
     * @throws JSONException on JSON error
     */
    String get() throws JSONException;

    /**
     * Parses the command /reply received from the server.
     *
     * @param command the string representing the command / reply
     * @throws IOException on JSON error
     */
    void parse(String command) throws IOException;
}
