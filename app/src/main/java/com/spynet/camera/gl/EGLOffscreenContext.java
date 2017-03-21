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

package com.spynet.camera.gl;

import android.util.Log;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

/**
 * Represents an EGL context associated with a pbuffer surface, used for offscreen rendering.<br>
 * The OpenGL ES specification is intentionally vague on how a rendering context
 * (an abstract OpenGL ES state machine) is created. One of the purposes of EGL is
 * to provide a means to create an OpenGL ES context and associate it with a surface.
 */
public class EGLOffscreenContext {

    private static final int EGL_OPENGL_ES2_BIT = 4;
    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

    private final String TAG = getClass().getSimpleName();

    private final EGL10 mEgl;                   // The EGL implementation
    private final EGLDisplay mDisplay;          // The EGL display
    private final EGLConfig mConfig;            // The EGL configuration
    private final EGLSurface mSurface;          // The EGL surface
    private final EGLContext mContext;          // The EGL context

    /**
     * Attribute list to create a context that supports RGB pbuffers
     * that can be rendered using OpenGL ES 2.0.
     */
    public static final int[] CONFIG_PIXEL_BUFFER = {
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
            EGL10.EGL_NONE
    };

    /**
     * Attribute list to create a context that supports RGBA pbuffers
     * that can be rendered using OpenGL ES 2.0.
     */
    public static final int[] CONFIG_PIXEL_RGBA_BUFFER = {
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_ALPHA_SIZE, 8,
            EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
            EGL10.EGL_NONE
    };

    /**
     * Creates a new EGLOffscreenContext object.
     *
     * @param attribList the list of attributes that determines the EGL configuration to choose
     *                   (one of {@code CONFIG_PIXEL_BUFFER} or {@code CONFIG_PIXEL_RGBA_BUFFER})
     * @param width      the width in pixel of the offscreen surface
     * @param height     the height in pixel of the offscreen surface
     */
    public EGLOffscreenContext(int[] attribList, int width, int height) {

        mEgl = (EGL10) javax.microedition.khronos.egl.EGLContext.getEGL();

        // Initialization (must be performed once for each display)
        mDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (mDisplay == EGL10.EGL_NO_DISPLAY)
            throw new RuntimeException("can't get the default display");
        int[] version = new int[2];
        if (!mEgl.eglInitialize(mDisplay, version))
            throw new RuntimeException("can't initialize EGL");
        Log.d(TAG, "EGL version " + version[0] + "." + version[1]);

        // Choose the configuration that better matches the specified attributes
        int[] numConfigs = new int[1];
        EGLConfig[] eglConfig = new EGLConfig[1];
        if (!mEgl.eglChooseConfig(mDisplay, attribList, eglConfig, 1, numConfigs))
            throw new RuntimeException("can't get a valid configuration");
        mConfig = eglConfig[0];

        // Create the off-screen rendering surface
        int[] surfaceAttribList = new int[]{
                EGL10.EGL_WIDTH, width,
                EGL10.EGL_HEIGHT, height,
                EGL10.EGL_NONE
        };
        mSurface = mEgl.eglCreatePbufferSurface(mDisplay, mConfig, surfaceAttribList);
        if (mSurface == EGL10.EGL_NO_SURFACE)
            throw new RuntimeException("can't create the surface");

        // Create the context
        int[] contextAttribList = new int[]{
                EGL_CONTEXT_CLIENT_VERSION, 2,  // OpenGL ES 2.x context
                EGL10.EGL_NONE
        };
        mContext = mEgl.eglCreateContext(mDisplay, mConfig, EGL10.EGL_NO_CONTEXT, contextAttribList);
        if (mContext == EGL10.EGL_NO_CONTEXT)
            throw new RuntimeException("can't create the context");
    }

    /**
     * Releases all the resources previously allocated.
     */
    public void release() {
        releaseCurrent();
        mEgl.eglDestroyContext(mDisplay, mContext);
        mEgl.eglDestroySurface(mDisplay, mSurface);
        mEgl.eglTerminate(mDisplay);
    }

    /**
     * Binds the context to the current rendering thread and to the surface.
     */
    public void makeCurrent() {
        if (!mEgl.eglMakeCurrent(mDisplay, mSurface, mSurface, mContext))
            throw new RuntimeException("can't bind the context");
    }

    /**
     * Releases the current context.
     */
    public void releaseCurrent() {
        if (!mEgl.eglMakeCurrent(mDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT))
            throw new RuntimeException("can't release the context");
    }
}
