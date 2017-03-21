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

package com.spynet.camera.ui;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.net.Uri;

import android.os.Build;
import android.os.Bundle;

import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.SwitchPreferenceCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.github.stkent.amplify.prompt.DefaultLayoutPromptView;
import com.github.stkent.amplify.tracking.Amplify;
import com.spynet.camera.R;
import com.spynet.camera.common.Utils;
import com.spynet.camera.network.Angelcam.AngelcamAdapter;
import com.spynet.camera.media.CameraInfo;
import com.spynet.camera.services.IStreamService;
import com.spynet.camera.services.StreamService;
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompatDividers;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SettingsActivity
        extends AppCompatActivity
        implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

    // Video stream
    private static final String KEY_PREF_CAMERA_INDEX = "pref_camera_index";
    private static final String KEY_PREF_VIDEO_RESOLUTION = "pref_video_resolution";
    private static final String KEY_PREF_VIDEO_QUALITY_K = "pref_video_quality_k";
    private static final String KEY_PREF_VIDEO_FPS = "pref_video_fps";
    private static final String KEY_PREF_VIDEO_I_DISTANCE = "pref_video_i_distance";
    private static final String KEY_PREF_AUDIO_ENABLED = "pref_audio_enabled";
    private static final String KEY_PREF_AUDIO_QUALITY_K = "pref_audio_quality_k";
    private static final String KEY_PREF_AUDIO_GAIN = "pref_audio_gain";
    private static final String KEY_PREF_MJPEG_QUALITY = "pref_mjpeg_quality";
    private static final String KEY_PREF_MJPEG_FPS = "pref_mjpeg_fps";
    private static final String KEY_PREF_CAMERA_ZOOM = "pref_camera_zoom_2";
    // Stream server
    private static final String KEY_PREF_SERVER_PORT = "pref_server_port";
    private static final String KEY_PREF_SERVER_UPNP = "pref_server_upnp";
    private static final String KEY_PREF_SERVER_WIFIONLY = "pref_server_wifionly";
    private static final String KEY_PREF_SERVER_AUTHENTICATE = "pref_server_authenticate";
    private static final String KEY_PREF_SERVER_USERNAME = "pref_server_username";
    private static final String KEY_PREF_SERVER_PASSWORD = "pref_server_password";
    private static final String KEY_PREF_SERVER_UPDATE_DDNS = "pref_server_update_ddns";
    private static final String KEY_PREF_SERVER_DDNS_SERVICE = "pref_server_ddns_service";
    private static final String KEY_PREF_SERVER_DDNS_NETWORK = "pref_server_ddns_network";
    private static final String KEY_PREF_SERVER_DDNS_HOSTNAME = "pref_server_ddns_hostname";
    private static final String KEY_PREF_SERVER_DDNS_USERNAME = "pref_server_ddns_username";
    private static final String KEY_PREF_SERVER_DDNS_PASSWORD = "pref_server_ddns_password";
    private static final String KEY_PREF_SERVER_DDNS_LAST_UPDATE = "pref_server_ddns_last_update";
    // Angelcam Ready
    private static final String KEY_PREF_ANGEL_ENABLED = "pref_angel_enabled";
    private static final String KEY_PREF_ANGEL_HOST = "pref_angel_host";
    private static final String KEY_PREF_ANGEL_PORT = "pref_angel_port";
    private static final String KEY_PREF_ANGEL_LOG = "pref_angel_log";
    private static final String KEY_PREF_ANGEL_UUID = "pref_angel_uuid";
    private static final String KEY_PREF_ANGEL_PWD = "pref_angel_pwd";
    // Mangocam Connect
    private static final String KEY_PREF_MANGO_ENABLED = "pref_mango_enabled";
    private static final String KEY_PREF_MANGO_HOST = "pref_mango_host";
    private static final String KEY_PREF_MANGO_PORT = "pref_mango_port";
    private static final String KEY_PREF_MANGO_LOG = "pref_mango_log";
    private static final String KEY_PREF_MANGO_RESET = "pref_mango_reset";
    private static final String KEY_PREF_MANGO_UUID = "pref_mango_uuid";
    private static final String KEY_PREF_MANGO_SERVERS = "pref_mango_servers";
    // Device
    private static final String KEY_PREF_BATTERY_OPTIMIZATION = "pref_battery_optimization";
    private static final String KEY_PREF_KEEP_SCREEN_ON = "pref_keep_screen_on";
    private static final String KEY_PREF_DIM_SCREEN = "pref_dim_screen";
    private static final String KEY_PREF_SHOW_OVERLAY = "pref_show_overlay";
    private static final String KEY_PREF_SILENT_NOTIFICATIONS = "pref_silent_notifications";

    private static final String KEY_RESULT_INTENT = "result_intent";

    private ServiceFragment mServiceFragment;           // The retained Fragment where to store the service references
    private BroadcastReceiver mNotificationReceiver;    // BroadcastReceiver to handle notification actions
    private Intent mResultIntent;                       // The Intent used to store results

    /**
     * @return the SharedPreferences for the specified Context
     */
    private static SharedPreferences getSharedPreferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * @return the camera index preference
     */
    public static int getCameraIndex(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return Utils.tryParseInt(preferences.getString(KEY_PREF_CAMERA_INDEX, "0"), 0);
    }

    /**
     * @return the video resolution preference in pixels {width, height}
     */
    public static int[] getVideoResolution(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        String resolution = preferences.getString(KEY_PREF_VIDEO_RESOLUTION, "640x480");
        String[] dim = resolution.split("x");
        return new int[]{Utils.tryParseInt(dim[0], 640), Utils.tryParseInt(dim[1], 480)};
    }

    /**
     * Sets the video resolution preference in pixels.
     */
    public static void setVideoResolution(Context context, int width, int height) {
        SharedPreferences preferences = getSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_PREF_VIDEO_RESOLUTION, width + "x" + height);
        editor.apply();
    }

    /**
     * @return the H264 bitrate preference in bps
     */
    public static int getH264Bitrate(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        String bitrate = preferences.getString(KEY_PREF_VIDEO_QUALITY_K, "512");
        return Utils.tryParseInt(bitrate, 512) * 1000;
    }

    /**
     * Sets the H264 bitrate preference in bps.
     */
    public static void setH264Bitrate(Context context, int bitrate) {
        SharedPreferences preferences = getSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_PREF_VIDEO_QUALITY_K, String.valueOf(bitrate / 1000));
        editor.apply();
    }

    /**
     * @return the H264 frame speed preference in fps
     */
    public static int getH264FrameSpeed(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        String fps = preferences.getString(KEY_PREF_VIDEO_FPS, "30");
        return Utils.tryParseInt(fps, 30);
    }

    /**
     * @return the H264 I-frame distance preference in seconds
     */
    public static int getH264IDistance(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        String distance = preferences.getString(KEY_PREF_VIDEO_I_DISTANCE, "2");
        return Utils.tryParseInt(distance, 2);
    }

    /**
     * @return the AAC enabled flag preference
     */
    public static boolean getAACEnabled(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getBoolean(KEY_PREF_AUDIO_ENABLED, true);
    }

    /**
     * @return the AAC bitrate preference in bps
     */
    public static int getAACBitrate(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        String bitrate = preferences.getString(KEY_PREF_AUDIO_QUALITY_K, "64");
        return Utils.tryParseInt(bitrate, 64) * 1000;
    }

    /**
     * Sets the AAC bitrate preference in bps
     */
    public static void setAACBitrate(Context context, int bitrate) {
        SharedPreferences preferences = getSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_PREF_AUDIO_QUALITY_K, String.valueOf(bitrate / 1000));
        editor.apply();
    }

    /**
     * @return the AAC gain preference in dB
     */
    public static int getAACGain(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        String gain = preferences.getString(KEY_PREF_AUDIO_GAIN, "0");
        return Utils.tryParseInt(gain, 0);
    }

    /**
     * @return the MJPEG quality preference in percentage
     */
    public static int getMJPEGQuality(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        String quality = preferences.getString(KEY_PREF_MJPEG_QUALITY, "75");
        return Utils.tryParseInt(quality, 75);
    }

    /**
     * @return the MJPEG frame speed preference in fps
     */
    public static int getMJPEGFrameSpeed(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        String fps = preferences.getString(KEY_PREF_MJPEG_FPS, "25");
        return Utils.tryParseInt(fps, 25);
    }

    /**
     * @return the camera zoom preference
     */
    public static float getCameraZoom(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getFloat(KEY_PREF_CAMERA_ZOOM, 1);
    }

    /**
     * Sets the camera zoom preference.
     */
    public static void setCameraZoom(Context context, float zoom) {
        SharedPreferences preferences = getSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putFloat(KEY_PREF_CAMERA_ZOOM, zoom);
        editor.apply();
    }

    /**
     * @return the server listening port preference
     */
    public static int getServerPort(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        String port = preferences.getString(KEY_PREF_SERVER_PORT, "8080");
        return Utils.tryParseInt(port, 8080);
    }

    /**
     * @return the server UPnP flag preference
     */
    public static boolean getServerUPnP(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getBoolean(KEY_PREF_SERVER_UPNP, true);
    }

    /**
     * @return the server WiFi only flag preference
     */
    public static boolean getServerWiFiOnly(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getBoolean(KEY_PREF_SERVER_WIFIONLY, true);
    }

    /**
     * @return the server 'use authentication' flag preference
     */
    public static boolean getServerAuthentication(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getBoolean(KEY_PREF_SERVER_AUTHENTICATE, false);
    }

    /**
     * @return the server username preference
     */
    public static String getServerUsername(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getString(KEY_PREF_SERVER_USERNAME, "");
    }

    /**
     * @return the server password preference
     */
    public static String getServerPassword(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getString(KEY_PREF_SERVER_PASSWORD, "");
    }

    /**
     * @return the server 'update DDNS' flag preference
     */
    public static boolean getServerUpdateDDNS(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getBoolean(KEY_PREF_SERVER_UPDATE_DDNS, false);
    }

    /**
     * @return the DDNS service preference
     */
    public static String getServerDDNSService(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getString(KEY_PREF_SERVER_DDNS_SERVICE, "noip");
    }

    /**
     * @return the DDNS network preference
     */
    public static String getServerDDNSNetwork(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getString(KEY_PREF_SERVER_DDNS_NETWORK, "wifi");
    }

    /**
     * @return the DDNS hostname
     */
    public static String getServerDDNSHostname(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getString(KEY_PREF_SERVER_DDNS_HOSTNAME, "");
    }

    /**
     * @return the DDNS username preference
     */
    public static String getServerDDNSUsername(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getString(KEY_PREF_SERVER_DDNS_USERNAME, "");
    }

    /**
     * @return the DDNS password preference
     */
    public static String getServerDDNSPassword(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getString(KEY_PREF_SERVER_DDNS_PASSWORD, "");
    }

    /**
     * Sets the DDNS last update result.
     */
    public static void setServerDDNSLastUpdate(Context context, String result) {

        SharedPreferences preferences = getSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_PREF_SERVER_DDNS_LAST_UPDATE, result);
        editor.apply();
    }

    /**
     * @return the Angelcam Ready enabled flag preference
     */
    public static boolean getAngelEnabled(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getBoolean(KEY_PREF_ANGEL_ENABLED, false);
    }

    /**
     * @return the Angelcam Ready host preference
     */
    public static String getAngelHost(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getString(KEY_PREF_ANGEL_HOST, "arr-rs.angelcam.com");
    }

    /**
     * @return the Angelcam Ready port preference
     */
    public static int getAngelPort(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        String port = preferences.getString(KEY_PREF_ANGEL_PORT, "8900");
        return Utils.tryParseInt(port, 8900);
    }

    /**
     * @return the Angelcam Ready log flag preference
     */
    public static boolean getAngelLog(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getBoolean(KEY_PREF_ANGEL_LOG, false);
    }

    /**
     * @return the Angelcam Ready UUID preference
     */
    public static String getAngelUUID(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getString(KEY_PREF_ANGEL_UUID, "");
    }

    /**
     * Sets the Angelcam Ready UUID preference.
     */
    public static void setAngelUUID(Context context, String uuid) {
        SharedPreferences preferences = getSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_PREF_ANGEL_UUID, uuid);
        editor.apply();
    }

    /**
     * @return the Angelcam Ready passphrase preference
     */
    public static String getAngelPassphrase(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getString(KEY_PREF_ANGEL_PWD, "");
    }

    /**
     * Sets the Angelcam Ready passphrase preference.
     */
    public static void setAngelPassphrase(Context context, String uuid) {
        SharedPreferences preferences = getSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_PREF_ANGEL_PWD, uuid);
        editor.apply();
    }

    /**
     * @return the Mangocam Connect enabled flag preference
     */
    public static boolean getMangoEnabled(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getBoolean(KEY_PREF_MANGO_ENABLED, false);
    }

    /**
     * @return the Mangocam Connect host preference
     */
    public static String getMangoHost(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getString(KEY_PREF_MANGO_HOST, "www.mangocam.com");
    }

    /**
     * @return the Mangocam Connect port preference
     */
    public static int getMangoPort(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        String port = preferences.getString(KEY_PREF_MANGO_PORT, "8282");
        return Utils.tryParseInt(port, 8282);
    }

    /**
     * @return the Mangocam Connect log flag preference
     */
    public static boolean getMangoLog(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getBoolean(KEY_PREF_MANGO_LOG, false);
    }

    /**
     * @return the Mangocam Connect UUID preference
     */
    public static String getMangoUUID(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getString(KEY_PREF_MANGO_UUID, "");
    }

    /**
     * Sets the Mangocam Connect UUID preference.
     */
    public static void setMangoUUID(Context context, String uuid) {
        SharedPreferences preferences = getSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_PREF_MANGO_UUID, uuid);
        editor.apply();
    }

    /**
     * @return the Mangocam Connect servers list preference
     */
    @NonNull
    public static List<String> getMangoServers(Context context) {
        ArrayList<String> hosts = new ArrayList<>();
        SharedPreferences preferences = getSharedPreferences(context);
        String hostsList = preferences.getString(KEY_PREF_MANGO_SERVERS, "");
        if (!hostsList.isEmpty())
            Collections.addAll(hosts, hostsList.split(","));
        return hosts;
    }

    /**
     * Sets the Mangocam Connect servers list preference.
     */
    public static void setMangoServers(Context context, List<String> hosts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hosts.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(hosts.get(i));
        }
        SharedPreferences preferences = getSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_PREF_MANGO_SERVERS, sb.toString());
        editor.apply();
    }

    /**
     * @return the 'screen always on' flag preference
     */
    public static boolean getAlwaysOn(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getBoolean(KEY_PREF_KEEP_SCREEN_ON, true);
    }

    /**
     * @return the 'dim screen' flag preference
     */
    public static boolean getDimScreen(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getBoolean(KEY_PREF_DIM_SCREEN, true);
    }

    /**
     * @return the 'show overlay' flag preference
     */
    public static boolean getShowOverlay(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getBoolean(KEY_PREF_SHOW_OVERLAY, true);
    }

    /**
     * @return the 'silent notifications' flag preference
     */
    public static boolean getSilentNotifications(Context context) {
        SharedPreferences preferences = getSharedPreferences(context);
        return preferences.getBoolean(KEY_PREF_SILENT_NOTIFICATIONS, false);
    }

    /**
     * Factory method to creates the Intent to run this Activity.
     */
    @NotNull
    public static Intent MakeIntent(Context context) {
        return new Intent(context, SettingsActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Register the BroadcastReceiver to handle notification actions
        mNotificationReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(StreamService.ACTION_STOP_SERVICE)) {
                    // Terminate the Activity. This happens when the user select
                    // the stop action on the service notification
                    finish();
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter(StreamService.ACTION_STOP_SERVICE);
        registerReceiver(mNotificationReceiver, intentFilter);

        if (savedInstanceState == null) {
            // Create the Intent to return results to the main Activity
            mResultIntent = new Intent();
            // Bind the StreamService in the application context
            // so that it won't shutdown on activity recreation
            mServiceFragment = new ServiceFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(mServiceFragment, "service")
                    .commit();
            ServiceConnection connection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    mServiceFragment.setStreamService(IStreamService.Stub.asInterface(service));
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    mServiceFragment.setStreamService(null);
                }
            };
            mServiceFragment.setServiceConnection(connection);
            Intent intent = StreamService.MakeIntent(this);
            getApplicationContext().bindService(intent, connection, 0);
            // Show the settings root
            SettingsFragment fragment = new SettingsFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .add(R.id.fragment_container, fragment)
                    .commit();
            // Ask the user for rating
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    DefaultLayoutPromptView promptView = (DefaultLayoutPromptView) findViewById(R.id.prompt_view);
                    if (promptView != null)
                        Amplify.getSharedInstance().promptIfReady(promptView);
                }
            }, 100);
        } else {
            // Restore results
            if (savedInstanceState.containsKey(KEY_RESULT_INTENT)) {
                mResultIntent = savedInstanceState.getParcelable(KEY_RESULT_INTENT);
            } else {
                mResultIntent = new Intent();
            }
            // Restore the ServiceFragment
            mServiceFragment = (ServiceFragment) getSupportFragmentManager().findFragmentByTag("service");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister the notification BroadcastReceiver
        if (mNotificationReceiver != null) {
            unregisterReceiver(mNotificationReceiver);
        }
        // Unbind the StreamService
        if (!isChangingConfigurations()) {
            if (mServiceFragment.getServiceConnection() != null) {
                getApplicationContext().unbindService(mServiceFragment.getServiceConnection());
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Save results
        if (mResultIntent != null) {
            outState.putParcelable(KEY_RESULT_INTENT, mResultIntent);
        }
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat preferenceFragmentCompat, PreferenceScreen preferenceScreen) {
        // Show the settings screen
        SettingsFragment fragment = new SettingsFragment();
        Bundle args = new Bundle();
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, preferenceScreen.getKey());
        fragment.setArguments(args);
        getSupportFragmentManager()
                .beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .replace(R.id.fragment_container, fragment, preferenceScreen.getKey())
                .addToBackStack(preferenceScreen.getKey())
                .commit();
        return true;
    }

    /**
     * @return the IStreamService to access the StreamService, null if the service is not bound
     */
    @Nullable
    private IStreamService getStreamService() {
        // The ServiceFragment may be not yet retrieved when this is called by the SettingsFragment,
        // since fragments are recreated from onCreate() in the base class
        if (mServiceFragment == null)
            mServiceFragment = (ServiceFragment) getSupportFragmentManager().findFragmentByTag("service");
        return mServiceFragment != null ? mServiceFragment.getStreamService() : null;
    }

    /**
     * Sets a result for the application
     *
     * @param name  the name of the extra data
     * @param value the boolean data value
     */
    public void setResult(String name, boolean value) {
        mResultIntent.putExtra(name, value);
        setResult(RESULT_OK, mResultIntent);
    }

    /**
     * Implements the PreferenceFragment.
     */
    public static class SettingsFragment
            extends PreferenceFragmentCompatDividers
            implements SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey) {
            Preference preference;
            // Setup the layout
            setPreferencesFromResource(R.xml.preferences, rootKey);
            // Update lists
            if (rootKey != null && rootKey.equals("preference_screen_camera")) {
                SharedPreferences preferences = getPreferenceScreen().getSharedPreferences();
                String value = preferences.getString(KEY_PREF_CAMERA_INDEX, "0");
                int cameraId = Utils.tryParseInt(value, 0);
                updateCamerasList();
                updateResolutionsList(cameraId);
            }
            // Update device information
            if (rootKey != null && rootKey.equals("preference_screen_about")) {
                updateVersion();
                updateURLs();
            }
            // Setup the change listener to verify the server port range
            preference = findPreference(KEY_PREF_SERVER_PORT);
            if (preference != null) {
                preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        int value = Utils.tryParseInt((String) newValue, 0);
                        if (value < 1024 || value > 65535) {
                            Toast.makeText(getActivity(), String.format(
                                    getResources().getString(R.string.error_invalid_range),
                                    preference.getTitle(), 1024, 65535),
                                    Toast.LENGTH_LONG).show();
                            return false;
                        }
                        return true;
                    }
                });
            }
            // Setup the change listener to warn the user when the mobile data is enabled
            preference = findPreference(KEY_PREF_SERVER_WIFIONLY);
            if (preference != null) {
                preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(final Preference preference, Object newValue) {
                        final SwitchPreferenceCompat sw = (SwitchPreferenceCompat) preference;
                        final SettingsActivity activity = (SettingsActivity) getActivity();
                        if (sw.isChecked() && !(boolean) newValue) {
                            new AlertDialog.Builder(activity)
                                    .setIcon(android.R.drawable.ic_dialog_alert)
                                    .setTitle(R.string.dialog_warning)
                                    .setMessage(R.string.warning_data_usage)
                                    .setPositiveButton(R.string.dialog_accept_button, null)
                                    .setNegativeButton(R.string.dialog_cancel_button, new DialogInterface.OnClickListener() {
                                        public void onClick(final DialogInterface dialog, final int id) {
                                            dialog.cancel();
                                        }
                                    })
                                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                        public void onCancel(DialogInterface dialog) {
                                            sw.setChecked(true);
                                        }
                                    })
                                    .create()
                                    .show();
                        }
                        return true;
                    }
                });
            }
            // Setup the change listener to get help about Angelcam
            preference = findPreference("pref_angel_whatis");
            if (preference != null) {
                preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        Uri uri = Uri.parse("https://www.angelcam.com/");
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        try {
                            startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(getActivity(), R.string.error_show_url, Toast.LENGTH_LONG).show();
                        }
                        return true;
                    }
                });
            }
            preference = findPreference("pref_angel_howto");
            if (preference != null) {
                preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        Uri uri = Uri.parse("https://help.angelcam.com/hc/en-us/articles/212022629-Android-Mobile-Apps/");
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        try {
                            startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(getActivity(), R.string.error_show_url, Toast.LENGTH_LONG).show();
                        }
                        return true;
                    }
                });
            }
            // Setup the change listener to reset the Mangocam adapter
            preference = findPreference(KEY_PREF_MANGO_RESET);
            if (preference != null) {
                final SettingsActivity activity = (SettingsActivity) getActivity();
                if (SettingsActivity.getMangoUUID(activity).isEmpty()) {
                    preference.setEnabled(false);
                } else {
                    preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            new AlertDialog.Builder(activity)
                                    .setIcon(android.R.drawable.ic_dialog_alert)
                                    .setTitle(R.string.pref_mango_reset)
                                    .setMessage(R.string.pref_mango_reset_dlg)
                                    .setPositiveButton(R.string.dialog_yes_button, new DialogInterface.OnClickListener() {
                                        public void onClick(final DialogInterface dialog, final int id) {
                                            SettingsActivity.setMangoUUID(activity, "");
                                            activity.setResult("RESTART_MANGO_ADAPTER", true);
                                            findPreference(KEY_PREF_MANGO_RESET).setEnabled(false);
                                        }
                                    })
                                    .setNegativeButton(R.string.dialog_no_button, null)
                                    .create()
                                    .show();
                            return true;
                        }
                    });
                }
            }
            // Setup the change listener to get help about Mangocam
            preference = findPreference("pref_mango_whatis");
            if (preference != null) {
                preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        Uri uri = Uri.parse("https://www.mangocam.com/");
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        try {
                            startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(getActivity(), R.string.error_show_url, Toast.LENGTH_LONG).show();
                        }
                        return true;
                    }
                });
            }
            // Setup the change listener to set battery optimization
            preference = findPreference(KEY_PREF_BATTERY_OPTIMIZATION);
            if (preference != null) {
                preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @SuppressLint("BatteryLife")
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            Intent intent = new Intent();
                            String packageName = getActivity().getPackageName();
                            PowerManager pm = (PowerManager) getActivity().getSystemService(Context.POWER_SERVICE);
                            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                                intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                            } else {
                                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                                intent.setData(Uri.parse("package:" + packageName));
                            }
                            getActivity().startActivity(intent);
                        }
                        return true;
                    }
                });
            }
            // Setup the change listener to navigate to the community
            preference = findPreference("pref_about_community");
            if (preference != null) {
                preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        Uri uri = Uri.parse(getString(R.string.app_support_community));
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        try {
                            startActivity(intent);
                        } catch (android.content.ActivityNotFoundException ex) {
                            Toast.makeText(getActivity(), R.string.error_browser_app, Toast.LENGTH_LONG).show();
                        }
                        return true;
                    }
                });
            }
            // Setup the change listener to rate the app
            preference = findPreference("pref_about_rate");
            if (preference != null) {
                preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        Uri uri = Uri.parse("market://details?id=" + getActivity().getPackageName());
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        try {
                            startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(getActivity(), R.string.error_market_app, Toast.LENGTH_LONG).show();
                        }
                        return true;
                    }
                });
            }
            // Disable the Angelcam adapter if not supported
            preference = findPreference("pref_screen_angelcam");
            if (preference != null) {
                if (!AngelcamAdapter.isSupported()) {
                    preference.setEnabled(false);
                    preference.setSummary(R.string.pref_angel_unsupported);
                }
            }
            // Disable the battery opimization if not supported
            preference = findPreference(KEY_PREF_BATTERY_OPTIMIZATION);
            if (preference != null) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    preference.setEnabled(false);
                    preference.setSummary(R.string.pref_battery_optimization_unsupported);
                }
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            try {
                return super.onCreateView(inflater, container, savedInstanceState);
            } finally {
                // Change the dividers' style
                setDividerPreferences(DIVIDER_PADDING_CHILD | DIVIDER_CATEGORY_AFTER_LAST | DIVIDER_CATEGORY_BETWEEN);
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences().
                    registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences().
                    unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            // Recreate the camera resolutions list
            if (key.equals(KEY_PREF_CAMERA_INDEX)) {
                String value = sharedPreferences.getString(KEY_PREF_CAMERA_INDEX, "0");
                int cameraId = Utils.tryParseInt(value, 0);
                updateResolutionsList(cameraId);
            }
            // Set audio gain
            if (key.equals(KEY_PREF_AUDIO_GAIN)) {
                SettingsActivity activity = (SettingsActivity) getActivity();
                activity.setResult("SET_AUDIO_GAIN", true);
            }
            // Restart the Recorder
            if (key.equals(KEY_PREF_CAMERA_INDEX) ||
                    key.equals(KEY_PREF_VIDEO_RESOLUTION) ||
                    key.equals(KEY_PREF_VIDEO_QUALITY_K) ||
                    key.equals(KEY_PREF_VIDEO_FPS) ||
                    key.equals(KEY_PREF_VIDEO_I_DISTANCE) ||
                    key.equals(KEY_PREF_AUDIO_ENABLED) ||
                    key.equals(KEY_PREF_AUDIO_QUALITY_K)) {
                SettingsActivity activity = (SettingsActivity) getActivity();
                activity.setResult("RESTART_RECORDER", true);
            }
            // Restart the StreamServer
            if (key.equals(KEY_PREF_SERVER_PORT) ||
                    key.equals(KEY_PREF_SERVER_UPNP) ||
                    key.equals(KEY_PREF_SERVER_WIFIONLY) ||
                    key.equals(KEY_PREF_SERVER_AUTHENTICATE) ||
                    key.equals(KEY_PREF_SERVER_USERNAME) ||
                    key.equals(KEY_PREF_SERVER_PASSWORD) ||
                    key.equals(KEY_PREF_SERVER_UPDATE_DDNS) ||
                    key.equals(KEY_PREF_SERVER_DDNS_SERVICE) ||
                    key.equals(KEY_PREF_SERVER_DDNS_NETWORK) ||
                    key.equals(KEY_PREF_SERVER_DDNS_HOSTNAME) ||
                    key.equals(KEY_PREF_SERVER_DDNS_USERNAME) ||
                    key.equals(KEY_PREF_SERVER_DDNS_PASSWORD)) {
                SettingsActivity activity = (SettingsActivity) getActivity();
                activity.setResult("RESTART_STREAM_SERVER", true);
            }
            // Restart the MangocamAdapter
            if (key.equals(KEY_PREF_SERVER_WIFIONLY) ||
                    key.equals(KEY_PREF_MANGO_ENABLED) ||
                    key.equals(KEY_PREF_MANGO_HOST) ||
                    key.equals(KEY_PREF_MANGO_PORT)) {
                SettingsActivity activity = (SettingsActivity) getActivity();
                activity.setResult("RESTART_MANGO_ADAPTER", true);
            }
            // Restart the AngelcamAdapter
            if (key.equals(KEY_PREF_SERVER_WIFIONLY) ||
                    key.equals(KEY_PREF_ANGEL_ENABLED) ||
                    key.equals(KEY_PREF_ANGEL_HOST) ||
                    key.equals(KEY_PREF_ANGEL_PORT)) {
                SettingsActivity activity = (SettingsActivity) getActivity();
                activity.setResult("RESTART_ANGEL_ADAPTER", true);
            }
        }

        /**
         * Helper to update the list of available cameras.
         */
        private void updateCamerasList() {
            ListPreference preference = (ListPreference) findPreference(KEY_PREF_CAMERA_INDEX);
            if (preference != null) {
                SettingsActivity activity = (SettingsActivity) getActivity();
                IStreamService service = activity.getStreamService();
                if (service != null) {
                    try {
                        int numCameras = service.getNumberOfCameras();
                        String[] cameraEntries = new String[numCameras];
                        String[] cameraValues = new String[numCameras];
                        for (int i = 0; i < numCameras; i++) {
                            CameraInfo cameraInfo = service.getCameraInfo(i);
                            cameraEntries[i] = cameraInfo.getDescription();
                            cameraValues[i] = String.valueOf(cameraInfo.getCameraId());
                        }
                        preference.setEntries(cameraEntries);
                        preference.setEntryValues(cameraValues);
                    } catch (RemoteException e) {
                        preference.setEntries(new String[0]);
                        preference.setEntryValues(new String[0]);
                    }
                } else {
                    preference.setEntries(new String[0]);
                    preference.setEntryValues(new String[0]);
                }
            }
        }

        /**
         * Update the list of available resolutions for the specified camera.
         */
        private void updateResolutionsList(int cameraId) {
            ListPreference preference = (ListPreference) findPreference(KEY_PREF_VIDEO_RESOLUTION);
            if (preference != null) {
                SettingsActivity activity = (SettingsActivity) getActivity();
                IStreamService service = activity.getStreamService();
                if (service != null) {
                    try {
                        ArrayList<String> resEntries = new ArrayList<>();
                        ArrayList<String> resValues = new ArrayList<>();
                        for (int i = 0; i < service.getNumberOfCameras(); i++) {
                            if (service.getCameraInfo(i).getCameraId() == cameraId) {
                                List<Point> sizes = service.getCameraInfo(i).getSupportedPreviewSizes();
                                if (sizes != null) {
                                    Collections.sort(sizes, new Comparator<Point>() {
                                        @Override
                                        public int compare(Point lhs, Point rhs) {
                                            if (lhs.x > rhs.x)
                                                return 1;
                                            else if (lhs.x < rhs.x)
                                                return -1;
                                            else if (lhs.y > rhs.y)
                                                return 1;
                                            else if (lhs.y < rhs.y)
                                                return -1;
                                            else
                                                return 0;
                                        }
                                    });
                                    for (Point size : sizes) {
                                        resEntries.add(size.x + "x" + size.y);
                                        resValues.add(size.x + "x" + size.y);
                                    }
                                }
                                break;
                            }
                        }
                        preference.setEntries(resEntries.toArray(new String[resEntries.size()]));
                        preference.setEntryValues(resValues.toArray(new String[resValues.size()]));
                    } catch (RemoteException e) {
                        preference.setEntries(new String[0]);
                        preference.setEntryValues(new String[0]);
                    }
                } else {
                    preference.setEntries(new String[0]);
                    preference.setEntryValues(new String[0]);
                }
            }
        }

        /**
         * Helper to set the application version
         */
        private void updateVersion() {
            Preference preference = findPreference("pref_about_version");
            if (preference != null) {
                EditTextLockedPreference editText = (EditTextLockedPreference) preference;
                editText.setText(com.spynet.camera.BuildConfig.VERSION_NAME);
            }
        }

        /**
         * Helper to set the local URLs
         */
        private void updateURLs() {
            Preference preference = findPreference("pref_about_url");
            if (preference != null) {
                SettingsActivity activity = (SettingsActivity) getActivity();
                IStreamService service = activity.getStreamService();
                EditTextLockedPreference editText = (EditTextLockedPreference) preference;
                String ip = Utils.getIPAddress(activity);
                int port = SettingsActivity.getServerPort(activity);
                if (ip != null) {
                    String text = "";
                    if (service != null) {
                        try {
                            text += "http://" + ip + ":" + port + "/";
                            if (service.isMJPEGAvailable())
                                text += "\nhttp://" + ip + ":" + port + "/video/mjpeg";
                            if (service.isH264Available())
                                text += "\nrtsp://" + ip + ":" + port + "/video/h264";
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    editText.setText(text);
                } else {
                    editText.setText(activity.getResources().getString(R.string.pref_about_url_none));
                }
            }
        }
    }
}
