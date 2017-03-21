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

import android.opengl.GLES20;

/**
 * Allows to create an OpenGL program by compiling and linking a given vertex shader
 * and a fragment shader.
 */
public class Shader {

    /**
     * The program object.
     */
    public final int program;

    /**
     * Creates a new Shader object.
     *
     * @param vertexShaderSource   the vertex shader source code
     * @param fragmentShaderSource the fragment shader source code
     */
    public Shader(String vertexShaderSource, String fragmentShaderSource) {

        int[] result = new int[1];
        String infoLog;

        // Build and compile vertex shader
        int vertexShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vertexShader, vertexShaderSource);
        GLES20.glCompileShader(vertexShader);
        GLES20.glGetShaderiv(vertexShader, GLES20.GL_COMPILE_STATUS, result, 0);
        if (result[0] == 0) {
            infoLog = GLES20.glGetShaderInfoLog(vertexShader);
            throw new RuntimeException(infoLog);
        }

        // Build and compile fragment shader
        int fragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fragmentShader, fragmentShaderSource);
        GLES20.glCompileShader(fragmentShader);
        GLES20.glGetShaderiv(fragmentShader, GLES20.GL_COMPILE_STATUS, result, 0);
        if (result[0] == 0) {
            infoLog = GLES20.glGetShaderInfoLog(fragmentShader);
            throw new RuntimeException(infoLog);
        }

        // Link shaders
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, result, 0);
        if (result[0] == 0) {
            infoLog = GLES20.glGetProgramInfoLog(vertexShader);
            throw new RuntimeException(infoLog);
        }

        // Delete the shaders as they're linked into our program now and no longer necessary
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);
    }

    /**
     * Use the program by calling {@code glUseProgram(program)}.
     */
    public void use() {
        GLES20.glUseProgram(program);
    }
}
