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

package com.spynet.camera.media;

import android.media.AudioFormat;

/**
 * Defines a buffer containing audio data.
 */
public class AudioData {

    private static final int FORMAT_COMPRESSED = -1;
    private static final int TYPE_UNCOMPRESSED_AUDIO = 1;
    private static final int TYPE_COMPRESSED_AUDIO = 2;
    private static final int TYPE_AUDIO_CONFIG = 3;

    private final byte[] data;                      // The audio data
    private final int format;                       // The data format
    private final int type;                         // The data type
    private final long timestamp;                   // The timestamp

    /**
     * Creates a new AudioData object that contains uncompressed audio.
     *
     * @param data      the raw data
     * @param format    the data format ({@link AudioFormat})
     * @param timestamp the timestamp
     */
    public AudioData(byte[] data, int format, long timestamp) {
        this.data = (data != null ? data.clone() : null);
        this.format = format;
        this.timestamp = timestamp;
        this.type = TYPE_UNCOMPRESSED_AUDIO;
    }

    /**
     * Creates a new AudioData object that contains compressed audio.
     *
     * @param data      the raw data
     * @param timestamp the timestamp
     */
    public AudioData(byte[] data, long timestamp) {
        this.data = (data != null ? data.clone() : null);
        this.format = FORMAT_COMPRESSED;
        this.timestamp = timestamp;
        this.type = TYPE_COMPRESSED_AUDIO;
    }

    /**
     * Creates a new AudioData object that contains audio configuration information.
     *
     * @param data the raw data
     */
    public AudioData(byte[] data) {
        this.data = (data != null ? data.clone() : null);
        this.format = FORMAT_COMPRESSED;
        this.timestamp = 0;
        this.type = TYPE_AUDIO_CONFIG;
    }

    /**
     * @return the raw data
     */
    public byte[] getData() {
        return data;
    }

    /**
     * @return the data format ({@link AudioFormat})
     */
    public int getFormat() {
        return format;
    }

    /**
     * @return the timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * @return {@code true} if the data represents compressed audio,
     * {@code false} otherwise
     */
    public boolean isCompressed() {
        return format == FORMAT_COMPRESSED;
    }

    /**
     * @return {@code true} if the data contains audio configuration information,
     * {@code false} otherwise
     */
    public boolean isConfig() {
        return type == TYPE_AUDIO_CONFIG;
    }
}
