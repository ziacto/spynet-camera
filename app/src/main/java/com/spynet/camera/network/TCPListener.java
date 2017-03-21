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

package com.spynet.camera.network;

import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/**
 * Defines a generic TCP listener that runs in its own thread.
 */
public class TCPListener implements Closeable {

    protected final String TAG = getClass().getSimpleName();

    private final ServerSocket mSocket;             // The listening socket
    private final ListenerCallback mCallback;       // The callback to notify the client
    private final Thread mThread;                   // The listening thread

    /**
     * Defines the interface that the client has to implement to receive notifications
     * about incoming connections.
     */
    public interface ListenerCallback {
        /**
         * Notifies a new incoming connection.
         *
         * @param listener the TCPListener that accepted the connection
         * @param socket   the socket to handle the connection
         * @throws IOException if an error occurs while handling the new connection
         */
        void onNewConnection(TCPListener listener, Socket socket) throws IOException;
    }

    /**
     * Creates a new TCPListener object.
     *
     * @param port     the server port
     * @param callback the callback implemented by the client
     * @throws IOException if an error occurs while creating the socket
     */
    public TCPListener(int port, @NotNull ListenerCallback callback) throws IOException {
        mSocket = new ServerSocket(port);
        mCallback = callback;
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                listen();
            }
        });
        mThread.start();
    }

    /**
     * Closes the listener.
     */
    @Override
    public void close() {
        try {
            mThread.interrupt();
            mSocket.close();
        } catch (Exception e) {
            Log.e(TAG, "unexpected exception while closing the socket", e);
        }
    }

    /**
     * Listen for incoming connections and notify the client.
     */
    private void listen() {
        Log.d(TAG, "listener started");
        try {
            while (true) {
                try {
                    Log.v(TAG, "listening for a new connection");
                    Socket socket = mSocket.accept();
                    Log.v(TAG, "new connection from " + socket.toString());
                    mCallback.onNewConnection(this, socket);
                } catch (SocketException e) {
                    Log.v(TAG, "socket closed");
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "unexpected exception while listening, continue", e);
                }
            }
        } finally {
            close();
            Log.d(TAG, "listener stopped");
        }
    }
}
