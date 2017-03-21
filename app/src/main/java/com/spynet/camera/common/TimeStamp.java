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

package com.spynet.camera.common;

/**
 * A collection of functions to handle the timestamp.
 */
public final class TimeStamp {

    // Number of milliseconds between Jan 1, 1900 and Jan 1, 1970 (70 years plus 17 leap days)
    private static final long OFFSET_1900_TO_1970 = ((365L * 70L) + 17L) * 24L * 60L * 60L * 1000L;

    /**
     * Hidden constructor, the class cannot be instantiated.
     */
    private TimeStamp() {
    }

    /**
     * Returns the current high-resolution timestamp, in microseconds.<br>
     * This method can only be used to measure elapsed time and is not related to any other notion
     * of system or wall-clock time. The value returned represents microseconds since some fixed but
     * arbitrary origin time (perhaps in the future, so values may be negative).
     * The same origin is used by all invocations of this method.
     *
     * @return the current timestamp in microseconds
     */
    public static long getTimeStamp() {
        return System.nanoTime() / 1000L;
    }

    /**
     * Returns the current timestamp in NTP format (RFC-1305).<br>
     * NTP timestamps are represented as a 64-bit unsigned fixed-point number, in seconds relative to
     * 0h on 1 January 1900. The integer part is in the first 32 bits and the fraction part in the
     * last 32 bits. In the fraction part, the non-significant low-order bits should be set to 0.
     *
     * @return the current NTP timestamp
     */
    public static long getNTPTimeStamp() {
        long timeMillis = System.currentTimeMillis() + OFFSET_1900_TO_1970;
        long timeSeconds = timeMillis / 1000L;
        long timeFraction = timeMillis - timeSeconds * 1000L;
        return (timeSeconds << 32) | (((timeFraction << 32) / 1000L) & 0xFFFFFFFFL);
    }
}
