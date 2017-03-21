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

import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.Surface;
import android.view.SurfaceHolder;

/**
 * Implements a dummy SurfaceHolder that holds an already exiting Surface.
 */
public class DummySurfaceHolder implements SurfaceHolder {

    private final Surface mSurface;

    /**
     * Creates a new DummySurfaceHolder object.
     *
     * @param surface the Surface to hold
     */
    public DummySurfaceHolder(Surface surface) {
        mSurface = surface;
    }

    @Override
    public void addCallback(Callback callback) {
    }

    @Override
    public void removeCallback(Callback callback) {
    }

    @Override
    public boolean isCreating() {
        return false;
    }

    @Override
    public void setType(int type) {
    }

    @Override
    public void setFixedSize(int width, int height) {
    }

    @Override
    public void setSizeFromLayout() {
    }

    @Override
    public void setFormat(int format) {
    }

    @Override
    public void setKeepScreenOn(boolean screenOn) {
    }

    @Override
    public Canvas lockCanvas() {
        return null;
    }

    @Override
    public Canvas lockCanvas(Rect dirty) {
        return null;
    }

    @Override
    public void unlockCanvasAndPost(Canvas canvas) {
    }

    @Override
    public Rect getSurfaceFrame() {
        return null;
    }

    @Override
    public Surface getSurface() {
        return mSurface;
    }
}
