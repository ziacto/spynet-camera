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

package com.spynet.camera.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.util.Log;

import com.spynet.camera.common.Utils;

import org.jetbrains.annotations.NotNull;

import java.io.Closeable;

/**
 * Defines a monitor that will notify the client upon connectivity status changes.
 */
public class ConnectivityMonitor implements Closeable {

    protected final String TAG = getClass().getSimpleName();

    private final Context mContext;                 // The context that uses the ConnectivityMonitor
    private ConnectivityCallback mCallback;         // The ConnectivityCallback implemented by mContext
    private ConnectivityBroadcastReceiver           // BroadcastReceiver used to monitor
            mConnectivityBroadcastReceiver;         // connectivity changes

    /**
     * A client may implement this interface to receive connectivity changes information.
     */
    public interface ConnectivityCallback {
        /**
         * Called when the WiFi availability changes.<br>
         * This method is always called within the main thread of its process.
         *
         * @param available specify whether the WiFi is available
         */
        void onWiFiAvailableChange(boolean available);

        /**
         * Called when the mobile data connection availability changes.<br>
         * This method is always called within the main thread of its process.
         *
         * @param available specify whether the mobile data connection is available
         */
        void onMobileAvailableChange(boolean available);
    }

    /**
     * Creates a new ConnectivityMonitor object.
     *
     * @param context the context where the ConnectivityMonitor is used; it should implement
     *                {@link ConnectivityCallback}
     */
    public ConnectivityMonitor(@NotNull Context context) {

        // Store the Context
        mContext = context;
        if (mContext instanceof ConnectivityCallback) {
            mCallback = (ConnectivityCallback) mContext;
        } else {
            Log.w(TAG, "ConnectivityCallback is not implemented by the specified context");
        }

        // Register the ConnectivityBroadcastReceiver
        mConnectivityBroadcastReceiver = new ConnectivityBroadcastReceiver();
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        mContext.registerReceiver(mConnectivityBroadcastReceiver, filter);

        // Notify the initial state
        if (mCallback != null) {
            mCallback.onWiFiAvailableChange(Utils.isWiFiAvailable(context));
            mCallback.onMobileAvailableChange(Utils.isMobileAvailable(context));
        }
    }

    /**
     * Closes the ConnectivityMonitor
     */
    @Override
    public void close() {
        // Unregister the ConnectivityBroadcastReceiver
        if (mConnectivityBroadcastReceiver != null)
            mContext.unregisterReceiver(mConnectivityBroadcastReceiver);
    }

    /**
     * BroadcastReceiver to monitor connectivity changes.
     */
    private class ConnectivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mCallback != null) {
                mCallback.onWiFiAvailableChange(Utils.isWiFiAvailable(context));
                mCallback.onMobileAvailableChange(Utils.isMobileAvailable(context));
            }
        }
    }
}
