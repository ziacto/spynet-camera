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

import android.graphics.Rect;
import android.graphics.YuvImage;

import java.io.OutputStream;

/**
 * Common utilities to handle images.
 */
public final class Image {

    /**
     * Hidden constructor, the class cannot be instantiated.
     */
    private Image() {
    }

    /**
     * Converts a NV12 image to a YUV420SemiPlanar image.<br>
     * We assume that there's no padding (stride = {@code width}) and that the {@code data}
     * buffer contains exactly one image, i.e. its size equals {@code width * height * 3 / 2}.
     *
     * @param data   the image data
     * @param width  the image width
     * @param height the image height
     */
    public static void convertNV12ToYUV420SemiPlanar(byte[] data, int width, int height) {
        for (int i = width * height; i < data.length; ) {
            byte tmp = data[i];
            data[i] = data[++i];
            data[i++] = tmp;
        }
    }

    /**
     * Converts a NV12 image to a YUV420Planar image.<br>
     * We assume that there's no padding (stride = {@code width}) and that the {@code data}
     * buffer contains exactly one image, i.e. its size equals {@code width * height * 3 / 2}.
     *
     * @param data   the image data
     * @param width  the image width
     * @param height the image height
     */
    public static void convertNV12ToYUV420Planar(byte[] data, int width, int height) {

        final int c_stride = width / 2;
        final int c_size = c_stride * height / 2;
        final int cr_offset = width * height;

        byte[] tmp = new byte[c_size * 2];
        for (int src = cr_offset, cr_dst = 0, cb_dst = c_size; src < data.length; src++) {
            if (src % 2 == 0) {
                tmp[cb_dst++] = data[src];
            } else {
                tmp[cr_dst++] = data[src];
            }
        }
        System.arraycopy(tmp, 0, data, cr_offset, c_size * 2);
    }

    /**
     * Compress an YUV image to a JPEG image.
     * Only ImageFormat.NV21 and ImageFormat.YUY2 are supported for now.
     *
     * @param data    the YUV data
     * @param width   the width of the image
     * @param height  the height of the image
     * @param format  the YUV data format as defined in ImageFormat
     * @param quality hint to the compressor, 0-100.
     *                0 meaning compress for small size, 100 meaning compress for max quality.
     * @param out     OutputStream to write the compressed data.
     */
    public static void compressToJpeg(byte[] data, int width, int height, int format,
                                      int quality, OutputStream out) {
        YuvImage img = new YuvImage(data, format, width, height, null);
        img.compressToJpeg(new Rect(0, 0, width - 1, height - 1), quality, out);
    }
}
