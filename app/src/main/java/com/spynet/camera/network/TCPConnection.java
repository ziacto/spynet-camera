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

import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Defines a generic TCP connection that can be handled in its own thread.
 */
public class TCPConnection implements Closeable {

    // Max number of active connections
    protected final static int MAX_CONNECTIONS = 10;

    // Used to run the connections in a thread pool
    protected final static ExecutorService mExecutor =
            Executors.newFixedThreadPool(MAX_CONNECTIONS);

    protected final String TAG = getClass().getSimpleName();

    protected final Socket mSocket;                     // The socket that represents the connection
    protected final ConnectionCallback mCallback;       // The callback to notify the client
    protected final Object mData;                       // Additional data attached to this object

    private final BufferedInputStream mInputStream;     // The input stream
    private final OutputStream mOutputStream;           // The output stream
    private final Future<?> mFuture;                    // Future that represents the task

    /**
     * Defines the interface that the client has to implement to handle the connection.
     */
    public interface ConnectionCallback {
        /**
         * Notifies the client that the connection has opened.
         *
         * @param connection the opened connection
         */
        void onConnectionOpened(TCPConnection connection);

        /**
         * Implement this method to handle client requests.
         *
         * @param connection the TCPConnection wrapped around the connection
         * @throws IOException if errors occur
         */
        void handleConnection(TCPConnection connection) throws IOException;

        /**
         * Notifies the client that the connection has closed.
         *
         * @param connection the closed connection
         */
        void onConnectionClosed(TCPConnection connection);
    }

    /**
     * Creates a new TCPConnection object.
     *
     * @param socket   the connection representing socket
     * @param callback the callback implemented by the client
     * @param data     additional data that can be attached to the object
     * @throws IOException if an error occurs while creating the input/output streams
     *                     or the socket is in an invalid state
     */
    public TCPConnection(Socket socket, ConnectionCallback callback, Object data) throws IOException {
        mSocket = socket;
        mInputStream = new BufferedInputStream(mSocket.getInputStream());
        mOutputStream = mSocket.getOutputStream();
        mCallback = callback;
        mData = data;
        mFuture = (mCallback == null) ? null :
                mExecutor.submit(new Runnable() {
                    @Override
                    public void run() {
                        handle();
                    }
                });
    }

    /**
     * @return the additional data attached to this object
     */
    public Object getBundledData() {
        return mData;
    }

    /**
     * Sets this socket's read timeout in milliseconds.
     *
     * @param timeout socket read timeout, use 0 for no timeout
     * @throws SocketException if there is an error in the underlying protocol
     */
    public void setTimeout(int timeout) throws SocketException {
        mSocket.setSoTimeout(timeout);
    }

    /**
     * @return true if the socket was successfully connected to a server
     */
    public boolean isConnected() {
        return mSocket.isConnected();
    }

    /**
     * @return the IP address of the target host this socket is connected to,
     * or null if this socket is not yet connected.
     */
    @Nullable
    public InetAddress getInetAddress() {
        return mSocket.getInetAddress();
    }

    /**
     * @return the local IP address this socket is bound to
     */
    public InetAddress getLocalAddress() {
        return mSocket.getLocalAddress();
    }

    /**
     * Writes a string to the output stream.
     *
     * @param str the string to write
     * @throws IOException if an error occurs while writing to the stream
     */
    public void write(String str) throws IOException {
        write(str.getBytes());
    }

    /**
     * Writes data to the output stream.
     *
     * @param buffer the buffer that contains the bytes to write
     * @throws IOException if an error occurs while writing to the stream
     */
    public void write(byte[] buffer) throws IOException {
        write(buffer, 0, buffer.length);
    }

    /**
     * Writes data to the output stream.
     *
     * @param buffer the buffer that contains the bytes to write
     * @param offset the start position from where to get bytes
     * @param count  the number of bytes to write
     * @throws IOException if an error occurs while writing to the stream
     */
    public void write(byte[] buffer, int offset, int count) throws IOException {
        synchronized (mOutputStream) {
            mOutputStream.write(buffer, offset, count);
        }
    }

    /**
     * Reads the next line of text available from the input stream.
     * A line is represented by zero or more characters followed by "\r\n" or the end of the reader.
     * The string does not include the newline sequence.
     *
     * @return the read string
     * @throws IOException if an error occurs while reading from the stream
     */
    public String readLine() throws IOException {
        synchronized (mInputStream) {
            StringBuilder sb = new StringBuilder();
            int c1 = 0, c = 0;
            while (!(c1 == '\r' && c == '\n')) {
                c1 = c;
                if ((c = mInputStream.read()) == -1)
                    break;
                if (c != '\r' && c != '\n')
                    sb.append((char) c);
            }
            return sb.toString();
        }
    }

    /**
     * Reads some bytes from the input stream.
     *
     * @param buffer the buffer where to store the read bytes
     * @return the number of characters actually read or -1 if the end of the reader has been
     * reached.
     * @throws IOException if an error occurs while reading from the stream
     */
    public int read(byte[] buffer) throws IOException {
        return read(buffer, 0, buffer.length);
    }

    /**
     * Reads some bytes from the input stream.
     *
     * @param buffer the buffer where to store the read bytes
     * @param offset the offset in the buffer where to start saving the data
     * @param count  the maximum number of bytes to read
     * @return the number of characters actually read or -1 if the end of the reader has been
     * reached.
     * @throws IOException if an error occurs while reading from the stream
     */
    public int read(byte[] buffer, int offset, int count) throws IOException {
        synchronized (mInputStream) {
            return mInputStream.read(buffer, offset, count);
        }
    }

    /**
     * Closes the connection.
     */
    @Override
    public void close() {
        try {
            if (mFuture != null)
                mFuture.cancel(true);
            mSocket.close();
        } catch (Exception e) {
            Log.e(TAG, "unexpected exception while closing the socket", e);
        }
    }

    /**
     * Handle the connections.
     */
    private void handle() {
        Log.d(TAG, "start handling " + mSocket.toString());
        try {
            mCallback.onConnectionOpened(this);
            mCallback.handleConnection(this);
        } catch (SocketException e) {
            Log.v(TAG, "socket closed");
        } catch (Exception e) {
            Log.e(TAG, "unexpected exception while handling " + mSocket.toString(), e);
        } finally {
            Log.d(TAG, "stop handling " + mSocket.toString());
            close();
            mCallback.onConnectionClosed(this);
        }
    }
}
