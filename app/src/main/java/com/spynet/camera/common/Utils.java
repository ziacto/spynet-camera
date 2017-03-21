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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Looper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;

/**
 * A collection of static utilities.
 */
public final class Utils {

    private static long mUniqueID = new Random(System.nanoTime()).nextLong();

    /**
     * Hidden constructor, the class cannot be instantiated.
     */
    private Utils() {
    }

    /**
     * @return a unique ID
     */
    public static long getUniqueID() {
        return mUniqueID++;
    }

    /**
     * Parses an integer from a string.
     *
     * @param string       the string to parse
     * @param defaultValue the default value
     * @return the integer represented by the string or the default value if the conversion
     * is not possible
     */
    public static int tryParseInt(String string, int defaultValue) {
        try {
            if (string != null)
                return Integer.parseInt(string);
        } catch (NumberFormatException e) {
            // error while parsing
        }
        return defaultValue;
    }

    /**
     * Parses a float from a string.
     *
     * @param string       the string to parse
     * @param defaultValue the default value
     * @return the float represented by the string or the default value if the conversion
     * is not possible
     */
    public static float tryParseFloat(String string, float defaultValue) {
        try {
            if (string != null)
                return Float.parseFloat(string);
        } catch (NumberFormatException e) {
            // error while parsing
        }
        return defaultValue;
    }

    /**
     * Parses a double from a string.
     *
     * @param string       the string to parse
     * @param defaultValue the default value
     * @return the double represented by the string or the default value if the conversion
     * is not possible
     */
    public static double tryParseDouble(String string, double defaultValue) {
        try {
            if (string != null)
                return Double.parseDouble(string);
        } catch (NumberFormatException e) {
            // error while parsing
        }
        return defaultValue;
    }

    /**
     * Coerces an integer in the specified range.
     */
    public static int coerce(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    /**
     * Coerces a float in the specified range.
     */
    public static float coerce(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    /**
     * Coerces a double in the specified range.
     */
    public static double coerce(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    /**
     * @return {@code true} if the current thread is the UI thread, {@code false} otherwise
     */
    public static boolean isUIThread() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }

    /**
     * Determines whether the WiFi connection is available.<br>
     * Bluetooth tethering is also considered a WiFi connection.
     *
     * @param context the calling Context
     * @return {@code true} if the WiFi is available, {@code false} otherwise.
     */
    public static boolean isWiFiAvailable(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null
                && networkInfo.isConnected()
                && (networkInfo.getType() == ConnectivityManager.TYPE_WIFI ||
                networkInfo.getType() == ConnectivityManager.TYPE_BLUETOOTH);
    }

    /**
     * Determines whether the WiFi connection is enabled.<br>
     * Bluetooth tethering is also considered a WiFi connection.
     *
     * @param context the calling Context
     * @return {@code true} if the WiFi is enabled, {@code false} otherwise.
     */
    public static boolean isWiFiEnabled(Context context) {
        WifiManager wm =
                (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        int wifiState = wm.getWifiState();
        BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
        int panState = ba.getProfileConnectionState(5 /* BluetoothProfile.PAN */);
        return wifiState == WifiManager.WIFI_STATE_ENABLED
                || wifiState == WifiManager.WIFI_STATE_ENABLING
                || panState == BluetoothProfile.STATE_CONNECTED
                || panState == BluetoothProfile.STATE_CONNECTING;
    }

    /**
     * Returns the IP address of the device.<br>
     * It is either the address of the WiFi interface or the Bluetooth Personal Area Network.
     *
     * @param context the calling Context
     * @return ip address or null
     */
    public static String getIPAddress(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI)
                return getIPAddress("wlan0");
            else if (networkInfo.getType() == ConnectivityManager.TYPE_BLUETOOTH)
                return getIPAddress("bt-pan");
        }
        return null;
    }

    /**
     * Returns the IPV4 address of the given interface name.
     *
     * @param interfaceName the name of the interface (eth0, wlan0, ...)
     * @return IP address or null
     */
    public static String getIPAddress(String interfaceName) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface i : interfaces) {
                if (i.getName().equalsIgnoreCase(interfaceName)) {
                    Enumeration<InetAddress> addresses = i.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (addr instanceof Inet4Address) {
                            return addr.getHostAddress().replace("/", "");
                        }
                    }
                    break;
                }
            }
        } catch (Exception ex) {
            // ignore exception
        }
        return null;
    }

    /**
     * Returns the MAC address of the given interface name.
     *
     * @param interfaceName the name of the interface (eth0, wlan0, ...)
     * @return mac address or null
     */
    public static String getMACAddress(String interfaceName) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface i : interfaces) {
                if (i.getName().equalsIgnoreCase(interfaceName)) {
                    byte[] mac = i.getHardwareAddress();
                    if (mac != null) {
                        StringBuilder buf = new StringBuilder();
                        for (byte b : mac)
                            buf.append(String.format("%02X:", b));
                        if (buf.length() > 0) buf.deleteCharAt(buf.length() - 1);
                        return buf.toString();
                    }
                    break;
                }
            }
        } catch (Exception ex) {
            // ignore exception
        }
        return null;
    }

    /**
     * Determines whether the mobile connection is available.
     *
     * @param context the calling Context
     * @return {@code true} if the mobile data connection is available, {@code false} otherwise.
     */
    public static boolean isMobileAvailable(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null
                && activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE
                && activeNetwork.isConnected();
    }

    /**
     * Sends the content of an asset file to an output stream
     *
     * @param context  the context that holds the asset
     * @param fileName the name of the file to read
     * @param out      the output stream to write data to
     * @return {@code true} if the operation succeeds, {@code false} on error
     */
    public static boolean readAssetFile(Context context, String fileName, OutputStream out) {

        AssetManager am = context.getAssets();
        byte[] buffer = new byte[1024];
        InputStream is = null;
        int read;

        try {
            is = am.open(fileName, AssetManager.ACCESS_BUFFER);
            while ((read = is.read(buffer)) != -1)
                out.write(buffer, 0, read);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            try {
                if (is != null) is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
