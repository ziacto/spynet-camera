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

import android.graphics.ImageFormat;

/**
 * Defines a video data frame.
 */
public class VideoFrame {

    private static final int FORMAT_COMPRESSED = -1;
    private static final int TYPE_UNCOMPRESSED_VIDEO = 1;
    private static final int TYPE_COMPRESSED_VIDEO = 2;
    private static final int TYPE_VIDEO_CONFIG = 3;

    private final byte[] data;                      // The video data
    private final int width, height;                // The frame dimensions
    private final int format;                       // The data format
    private final int type;                         // The data type
    private final String key;                       // The configuration key
    private final long timestamp;                   // The timestamp

    /**
     * Creates a new VideoFrame object that contains an uncompressed video frame.
     *
     * @param data      the raw frame data
     * @param width     the frame width
     * @param height    the frame height
     * @param format    the frame pixel format ({@link    ImageFormat})
     * @param timestamp the frame timestamp
     */
    public VideoFrame(byte[] data, int width, int height, int format, long timestamp) {
        this.data = (data != null ? data.clone() : null);
        this.width = width;
        this.height = height;
        this.format = format;
        this.timestamp = timestamp;
        this.type = TYPE_UNCOMPRESSED_VIDEO;
        this.key = null;
    }

    /**
     * Creates a new VideoFrame object that contains a compressed video slice.
     *
     * @param data      the raw frame data
     * @param timestamp the frame timestamp
     */
    public VideoFrame(byte[] data, long timestamp) {
        this.data = (data != null ? data.clone() : null);
        this.width = -1;
        this.height = -1;
        this.format = FORMAT_COMPRESSED;
        this.timestamp = timestamp;
        this.type = TYPE_COMPRESSED_VIDEO;
        this.key = null;
    }

    /**
     * Creates a new VideoFrame object that contains video configuration information
     *
     * @param data the raw frame data
     * @param key  the
     */
    public VideoFrame(byte[] data, String key) {
        this.data = (data != null ? data.clone() : null);
        this.width = -1;
        this.height = -1;
        this.format = FORMAT_COMPRESSED;
        this.timestamp = -1;
        this.type = TYPE_VIDEO_CONFIG;
        this.key = key;
    }

    /**
     * @return the raw frame data
     */
    public byte[] getData() {
        return data;
    }

    /**
     * @return the frame width
     */
    public int getWidth() {
        return width;
    }

    /**
     * @return the frame height
     */
    public int getHeight() {
        return height;
    }

    /**
     * @return the frame pixel format ({@link ImageFormat})
     */
    public int getFormat() {
        return format;
    }

    /**
     * @return the frame timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * @return {@code true} if the VideoFrame represents a compressed slice,
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
        return type == TYPE_VIDEO_CONFIG;
    }

    /**
     * @return the key that identifies the configuration type, {@code null} if the buffer
     * does not contains video configuration information.
     */
    public String getKey() {
        return key;
    }
}
