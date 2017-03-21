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

import android.graphics.Point;
import android.view.Surface;

import java.io.Closeable;
import java.io.IOException;

/**
 * Defines an input Camera device.
 */
public interface Camera extends Closeable {

    /**
     * A client may implement this interface to receive video frames as they are available.
     */
    interface FrameCallback {
        /**
         * Called when a new video frame is available.<br>
         * To know about the frame size and its pixel format use {@link #getFrameSize()} and
         * {@link #getFrameFormat()}.
         *
         * @param data      frame data
         * @param timestamp the timestamp in microseconds
         */
        void onFrameAvailable(byte[] data, long timestamp);
    }

    /**
     * Opens the camera.<br>
     *
     * @param width  the desired width for pictures, in pixels
     * @param height the desired height for pictures, in pixels
     * @param format the desired color format (ImageFormat)
     * @throws IOException if opening the camera fails
     */
    void open(int width, int height, int format) throws IOException;

    /**
     * Disconnects and releases the resources.
     * You must call this as soon as you're done.
     */
    void release();

    /**
     * Closes the camera
     */
    @Override
    void close();

    /**
     * Starts the preview on a different {@link Surface}.<br>
     * If {@code surface} is {@code null}, the stream will be rendered offscreen.
     *
     * @param surface the {@link Surface} on which to render the stream,
     *                {@code null} to render the stream offscreen
     * @throws IOException if setting the surface fails
     */
    void setSurface(Surface surface) throws IOException;

    /**
     * @return the current preview {@link Surface}
     */
    Surface getSurface();

    /**
     * @return {@code true} if this is front camera, {@code false} otherwise
     */
    boolean isFront();

    /**
     * @return the current frame size
     */
    Point getFrameSize();

    /**
     * @return the current frame pixel format
     */
    int getFrameFormat();

    /**
     * @return the current zoom factor
     */
    float getZoom();

    /**
     * Sets the camera zoom.
     *
     * @param value zoom factor
     */
    void setZoom(float value);

    /**
     * Starts camera auto-focus.
     *
     * @param x the horizontal coordinate of the focus area, in the range -1000 to 1000.
     * @param y the vertical coordinate of the focus area, in the range -1000 to 1000.
     */
    void autoFocus(int x, int y);

    /**
     * @return {@code true} if the torch is on, {@code false} if it is off
     */
    boolean getTorch();

    /**
     * Sets the torch on or off.
     *
     * @param state {@code true} to turn the torch on, {@code false} to turn it off
     */
    void setTorch(boolean state);

    /**
     * @return the average frames speed
     */
    float getAverageFps();
}
