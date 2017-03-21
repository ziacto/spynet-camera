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

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.github.stkent.amplify.prompt.DefaultLayoutPromptView;
import com.github.stkent.amplify.tracking.Amplify;
import com.github.stkent.amplify.tracking.PromptViewEvent;
import com.github.stkent.amplify.tracking.interfaces.IEvent;
import com.github.stkent.amplify.tracking.interfaces.IEventListener;
import com.spynet.camera.common.Utils;
import com.spynet.camera.services.ConnectivityMonitor;
import com.spynet.camera.services.IStreamService;
import com.spynet.camera.R;
import com.spynet.camera.services.IStreamServiceCallBack;
import com.spynet.camera.network.Angelcam.AngelcamNotification;
import com.spynet.camera.network.Mangocam.MangocamNotification;
import com.spynet.camera.services.StreamService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity
        extends
        ImmersiveModeActivity
        implements
        SurfaceHolder.Callback,
        ConnectivityMonitor.ConnectivityCallback,
        StatusFragment.Callback,
        DimmerFragment.Callback {

    private final String TAG = getClass().getSimpleName();

    // ID used to start the SettingsActivity and then get the results
    private final static int CHANGE_SETTINGS = 123;
    // ID to ask for multiple permission
    private final static int ASK_MULTIPLE_PERMISSIONS = 42;

    private StreamServiceCallBack mServiceCallback;     // The StreamService callback
    private ServiceFragment mServiceFragment;           // The retained Fragment where to store the service references
    private ImageView mImageScreenCapture;              // Screen capture message View
    private SurfaceView mPreviewView;                   // Preview View
    private OverlayFragment mOverlayFragment;           // Fragment to show the overlay information
    private StatusFragment mStatusFragment;             // Fragment to show the streaming status
    private DimmerFragment mDimmerFragment;             // Fragment to dim the screen
    private DefaultLayoutPromptView mPromptView;        // DefaultLayoutPromptView to rate the app
    private BroadcastReceiver mNotificationReceiver;    // BroadcastReceiver to handle notification actions
    private ConnectivityMonitor mConnectivityMonitor;   // ConnectivityMonitor to monitor WiFi availability
    private boolean mIsFirstExecution;                  // First execution after creation
    private boolean mIsResumed;                         // The Activity is in the resumed state
    private boolean mMessageShown;                      // Whether a notification message is shown
    private boolean mPromptShown;                       // Whether the rating prompt is shown
    private volatile boolean mSetPreviewNeeded;         // Indicates the need to set the preview later on

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mIsFirstExecution = (savedInstanceState == null);

        // Set preferences defaults
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        // Setup the layout
        setContentView(R.layout.activity_main);
        mImageScreenCapture = (ImageView) findViewById(R.id.screencast);
        mPromptView = (DefaultLayoutPromptView) findViewById(R.id.prompt_view);
        mPreviewView = new SurfaceView(this);
        mPreviewView.getHolder().addCallback(this);
        RelativeLayout previewLayout = (RelativeLayout) findViewById(R.id.preview);
        previewLayout.addView(mPreviewView);

        // Add fragments
        if (savedInstanceState == null) {
            mOverlayFragment = new OverlayFragment();
            mStatusFragment = new StatusFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.overlay, mOverlayFragment, "overlay")
                    .add(R.id.status, mStatusFragment, "status")
                    .commit();
        } else {
            mOverlayFragment = (OverlayFragment) getSupportFragmentManager().findFragmentByTag("overlay");
            mStatusFragment = (StatusFragment) getSupportFragmentManager().findFragmentByTag("status");
        }

        // Setup the preview View to handle touch events
        final CameraZoomGestureListener cameraZoomGestureListener =
                new CameraZoomGestureListener();
        final ScaleGestureDetector scaleGestureDetector =
                new ScaleGestureDetector(this, cameraZoomGestureListener);
        final GestureDetector gestureDetector =
                new GestureDetector(this, cameraZoomGestureListener);
        mPreviewView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getPointerCount() > 1)
                    return scaleGestureDetector.onTouchEvent(event);
                else
                    return gestureDetector.onTouchEvent(event);
            }
        });

        // Set the rating prompt events listener
        mPromptView.addPromptEventListener(new IEventListener() {
            @Override
            public void notifyEventTriggered(@NonNull final IEvent event) {
                if (event == PromptViewEvent.PROMPT_SHOWN) {
                    // Prevent the dimmer to start
                    mPromptShown = true;
                    exitFullScreen();
                } else if (event == PromptViewEvent.PROMPT_DISMISSED) {
                    // Trigger the dimmer
                    mPromptShown = false;
                    enterFullScreen();
                }
            }
        });

        // Start the service or retrieve the references from the retained fragment
        // The service is bound in the application context so that it won't shutdown
        // on activity recreation
        // The service is also started to keep it running when the activity will terminate
        // Note that calls to startService() are not counted: no matter how many times you
        // call startService(), a single call to stopService(Intent) will stop it
        mServiceCallback = new StreamServiceCallBack();
        startService(StreamService.MakeIntent(this));
        FragmentManager fm = getSupportFragmentManager();
        mServiceFragment = (ServiceFragment) fm.findFragmentByTag("service");
        if (mServiceFragment == null) {
            mServiceFragment = new ServiceFragment();
            fm.beginTransaction().add(mServiceFragment, "service").commit();
            ServiceConnection connection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    IStreamService streamService = IStreamService.Stub.asInterface(service);
                    // Store the IStreamService interface
                    mServiceFragment.setStreamService(streamService);
                    // Retrieve the current activity instance
                    MainActivity activity = (MainActivity) mServiceFragment.getContext();
                    // If the ServiceFragment is attached to a MainActivity instance
                    if (activity != null) {
                        try {
                            // Register the IStreamServiceCallBack callback
                            streamService.registerCallBack(activity.mServiceCallback);
                            // Set the preview if not already done in surfaceCreated
                            if (activity.mSetPreviewNeeded) {
                                Point size = streamService.getFrameSize();
                                if (size != null) {
                                    activity.setPreview(size);
                                    activity.mSetPreviewNeeded = false;
                                }
                            }
                            // Update UI information
                            activity.updateUIStatus();
                        } catch (RemoteException e) {
                            Log.e(TAG, "cannot setup the service", e);
                        }
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    mServiceFragment.setStreamService(null);
                }
            };
            mServiceFragment.setServiceConnection(connection);
            Intent intent = StreamService.MakeIntent(this);
            getApplicationContext().bindService(intent, connection, 0);
        } else {
            // Register the callback
            try {
                IStreamService service = mServiceFragment.getStreamService();
                if (service != null)
                    service.registerCallBack(mServiceCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "cannot setup the service", e);
            }
        }

        // Ask for runtime permissions
        final List<String> permissionsNeeded = new ArrayList<>();
        final List<String> permissionsList = new ArrayList<>();
        if (addPermission(permissionsList, Manifest.permission.CAMERA))
            permissionsNeeded.add(getString(R.string.dialog_permissions_camera));
        if (addPermission(permissionsList, Manifest.permission.RECORD_AUDIO))
            permissionsNeeded.add(getString(R.string.dialog_permissions_audio));
        if (addPermission(permissionsList, Manifest.permission.READ_EXTERNAL_STORAGE))
            permissionsNeeded.add(getString(R.string.dialog_permissions_storage));
        if (addPermission(permissionsList, Manifest.permission.ACCESS_FINE_LOCATION))
            permissionsNeeded.add(getString(R.string.dialog_permissions_location));
        if (permissionsList.size() > 0) { // permissions needed
            if (permissionsNeeded.size() > 0) { // rationale needed
                String message = permissionsNeeded.get(0);
                for (int i = 1; i < permissionsNeeded.size(); i++)
                    message = message + "\n" + permissionsNeeded.get(i);
                new AlertDialog.Builder(MainActivity.this)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(getString(R.string.dialog_permissions_title))
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton(R.string.dialog_now_button, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(MainActivity.this,
                                        permissionsList.toArray(new String[permissionsList.size()]),
                                        ASK_MULTIPLE_PERMISSIONS);
                            }
                        })
                        .setNegativeButton(R.string.dialog_later_button, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (permissionsList.contains(Manifest.permission.CAMERA)) {
                                    Toast.makeText(MainActivity.this, R.string.warning_no_camera_permission, Toast.LENGTH_LONG).show();
                                    finish(true);
                                }
                            }
                        })
                        .create()
                        .show();
            } else { // rationale not needed
                ActivityCompat.requestPermissions(MainActivity.this,
                        permissionsList.toArray(new String[permissionsList.size()]),
                        ASK_MULTIPLE_PERMISSIONS);
            }
        }

        // Register the BroadcastReceiver to handle notification actions
        mNotificationReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (intent.getAction()) {
                    case AngelcamNotification.ACTION_SHOW_DIALOG:
                    case MangocamNotification.ACTION_SHOW_DIALOG:
                        // Show the messages coming from the AngelcamNotification
                        // and MangocamNotification notifications. This happens
                        // when the user clicks on a notification and there's an
                        // extended message to be shown
                        if (!mMessageShown) {
                            mMessageShown = true;
                            new AlertDialog.Builder(MainActivity.this)
                                    .setIcon(android.R.drawable.ic_dialog_info)
                                    .setTitle(intent.getStringExtra("title"))
                                    .setMessage(intent.getStringExtra("message"))
                                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                        public void onCancel(DialogInterface dialog) {
                                            mMessageShown = false;
                                        }
                                    })
                                    .create()
                                    .show();
                        }
                        break;
                    case StreamService.ACTION_STOP_SERVICE:
                        // Terminate the app so that the service can terminate as well.
                        // This happens when the user select the stop action on the service
                        // notification.
                        finish(true);
                        break;
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MangocamNotification.ACTION_SHOW_DIALOG);
        intentFilter.addAction(AngelcamNotification.ACTION_SHOW_DIALOG);
        intentFilter.addAction(StreamService.ACTION_STOP_SERVICE);
        registerReceiver(mNotificationReceiver, intentFilter);
    }

    /**
     * Call this when your activity is done and should be closed.
     *
     * @param stopService whether to also shutdown the StreamService
     */
    private void finish(boolean stopService) {
        if (stopService)
            stopService(StreamService.MakeIntent(MainActivity.this));
        finish();
    }

    /**
     * Adds {@code permission} to {@code permissionsList} if not granted, returns {@code true}
     * if a rationale should be displayed for the specified permission.
     */
    private boolean addPermission(List<String> permissionsList, String permission) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(permission);
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission))
                return true;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == ASK_MULTIPLE_PERMISSIONS) {
            boolean needRestart = false;
            // If the camera permission has not been granted, shutdown the app
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.permission.CAMERA) &&
                        grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.warning_no_camera_permission, Toast.LENGTH_LONG).show();
                    finish(true);
                    break;
                }
            }
            // Check other permission granted/denied
            for (int i = 0; i < permissions.length; i++) {
                // If the camera permission has been granted, we need to restart the app
                if (permissions[i].equals(Manifest.permission.RECORD_AUDIO) &&
                        grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    needRestart = true;
                }
                // If the recorder permission has been granted, we need to restart the app
                if (permissions[i].equals(Manifest.permission.CAMERA) &&
                        grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    needRestart = true;
                }
                // If the location permission has been granted, we need to restart the app
                if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION) &&
                        grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    needRestart = true;
                }
            }
            // Restart the app if needed
            // To ensure that the service will also restart, we need to stop and unbind it now
            if (needRestart) {
                stopService(StreamService.MakeIntent(MainActivity.this));
                if (mServiceFragment.getServiceConnection() != null) {
                    getApplicationContext().unbindService(mServiceFragment.getServiceConnection());
                    mServiceFragment.setServiceConnection(null);
                }
                Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // If the service is ready, we can setup the preview now
        // If not, we will have to set it later on
        IStreamService service = mServiceFragment.getStreamService();
        if (service != null) {
            try {
                Point size = service.getFrameSize();
                if (size != null)
                    setPreview(size);
                else
                    mSetPreviewNeeded = true;
            } catch (RemoteException e) {
                Log.e(TAG, "cannot set the preview surface", e);
            }
        } else {
            mSetPreviewNeeded = true;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        try {
            cancelPreview();
        } catch (RemoteException e) {
            Log.e(TAG, "cannot cancel the preview surface", e);
        }
    }

    /**
     * Prepare and set the preview Surface where the camera will render the video.
     */
    private void setPreview(@NonNull Point size) throws RemoteException {
        // Setup the preview view size to fit the screen
        Display display = getWindowManager().getDefaultDisplay();
        Point displaySize = new Point();
        display.getRealSize(displaySize);
        float scale = Math.min(
                (float) displaySize.x / (float) size.x,
                (float) displaySize.y / (float) size.y);
        mPreviewView.setLayoutParams(new RelativeLayout.LayoutParams(
                (int) (size.x * scale),
                (int) (size.y * scale)));
        // Setup the camera so that the preview will be shown on this surface
        IStreamService service = mServiceFragment.getStreamService();
        if (service != null) {
            SurfaceHolder holder = mPreviewView.getHolder();
            if (holder != null)
                service.setSurface(holder.getSurface());
        }
    }

    /**
     * Cancel the preview so that the camera will render the video offscreen.
     */
    private void cancelPreview() throws RemoteException {
        IStreamService service = mServiceFragment.getStreamService();
        if (service != null)
            service.setSurface(null);
    }

    /**
     * Helper to reload the UI status from the service.
     */
    private void updateUIStatus() throws RemoteException {
        IStreamService service = mServiceFragment.getStreamService();
        if (service != null) {
            // Preview
            mPreviewView.setVisibility(service.isScreenCaptureAvailable() ? View.INVISIBLE : View.VISIBLE);
            mImageScreenCapture.setVisibility(service.isScreenCaptureAvailable() ? View.VISIBLE : View.GONE);
            // Overlay
            mOverlayFragment.setFrameSize(service.getFrameSize());
            mOverlayFragment.setBitrate(service.getVideoBitrate(), service.getAudioBitrate());
            mOverlayFragment.setZoom(service.getZoom());
            mOverlayFragment.setAverageFrameRate(service.getAverageFrameRate());
            // Status
            mStatusFragment.setStreaming(service.getNumberOfStreams() > 0);
            mStatusFragment.setAudioStreaming(service.getNumberOfAudioStreams() > 0);
            mStatusFragment.setMute(service.getMute());
            mStatusFragment.setMangocamConnected(service.isMangocamConnected());
            mStatusFragment.setAngelcamConnected(service.isAngelcamConnected());
        }
    }

    /**
     * Start the screen dimmer.
     */
    private void startDimmer() {
        if (mDimmerFragment == null) {
            IStreamService service = mServiceFragment.getStreamService();
            if (service != null) {
                try {
                    if (service.isFrontCamera())
                        mDimmerFragment = new SlideshowFragment();
                    else
                        mDimmerFragment = new LogoFragment();
                    mDimmerFragment.setStreaming(service.getNumberOfStreams() > 0);
                } catch (RemoteException e) {
                    mDimmerFragment = new LogoFragment();
                }
            } else {
                mDimmerFragment = new LogoFragment();
            }
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.dimmer, mDimmerFragment)
                    .commit();
        }
    }

    /**
     * Stop the screen dimmer
     */
    private void stopDimmer() {
        if (mDimmerFragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .remove(mDimmerFragment)
                    .commit();
            mDimmerFragment = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsResumed = true;

        // Hide the dimmer
        stopDimmer();

        // Update overlay information
        mOverlayFragment.setAddress(null, 0);
        mOverlayFragment.setLog(null);
        mOverlayFragment.setVisible(SettingsActivity.getShowOverlay(this));

        // Update other UI information
        try {
            updateUIStatus();
        } catch (RemoteException e) {
            Log.e(TAG, "cannot update the UI", e);
        }

        // Start the ConnectivityMonitor to monitor WiFi availability
        mConnectivityMonitor = new ConnectivityMonitor(this);

        // Setup the window to keep the screen always on according to the user setting
        if (SettingsActivity.getAlwaysOn(this)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        // Allow the user to enable the WiFi if needed or notify that mobile data could be consumed
        if (SettingsActivity.getServerWiFiOnly(this)) {
            if (!Utils.isWiFiEnabled(this)) {
                if (mIsFirstExecution) {
                    new AlertDialog.Builder(this)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setTitle(R.string.dialog_warning)
                            .setMessage(R.string.warning_wifi_not_available)
                            .setPositiveButton(R.string.dialog_yes_button, new DialogInterface.OnClickListener() {
                                public void onClick(final DialogInterface dialog, final int id) {
                                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                                }
                            })
                            .setNegativeButton(R.string.dialog_no_button, null)
                            .create()
                            .show();
                }
            }
        } else {
            Toast.makeText(this, R.string.warning_data_enabled, Toast.LENGTH_LONG).show();
        }

        // Reset first execution flag
        mIsFirstExecution = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsResumed = false;
        // Hide the dimmer
        stopDimmer();
        // Close the ConnectivityMonitor
        if (mConnectivityMonitor != null) {
            mConnectivityMonitor.close();
            mConnectivityMonitor = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister the notification BroadcastReceiver
        if (mNotificationReceiver != null) {
            unregisterReceiver(mNotificationReceiver);
        }
        // Stop the StreamService
        mServiceCallback = null;
        if (!isChangingConfigurations()) {
            if (mServiceFragment.getServiceConnection() != null) {
                getApplicationContext().unbindService(mServiceFragment.getServiceConnection());
            }
        }
    }

    @Override
    public void onBackPressed() {
        // Do not allow to exit when the slideshow is playing
        if (mDimmerFragment != null && mDimmerFragment.isStarted() && mDimmerFragment instanceof SlideshowFragment) {
            Log.w(TAG, "it is not allowed to exit the app when the slideshow is playing");
            return;
        }
        // If the screen capture is configured but not authorized, there's no active streaming to keep
        IStreamService service = mServiceFragment.getStreamService();
        if (service != null) {
            try {
                if (service.isScreenCaptureCamera() && !service.isScreenCaptureAvailable()) {
                    finish(true);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "can't verify the screen capture camera", e);
            }
        }
        // The user is asked to choose between terminate or keep the service running in the background,
        // after that the app is closed
        new AlertDialog.Builder(MainActivity.this)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle(getString(R.string.app_service_name))
                .setMessage(getString(R.string.msg_run_in_background))
                .setPositiveButton(R.string.dialog_yes_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish(false);
                    }
                })
                .setNegativeButton(R.string.dialog_no_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish(true);
                    }
                })
                .create()
                .show();
    }

    @Override
    protected void onEnterFullScreen() {
        // Start the dimmer
        if (SettingsActivity.getDimScreen(this) && mIsResumed && !mPromptShown)
            startDimmer();
    }

    @Override
    protected void onExitFullScreen() {
        // Hide the dimmer
        stopDimmer();
        // Ask the user for rating
        if (!mPromptShown)
            Amplify.getSharedInstance().promptIfReady(mPromptView);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            // Show the SettingsActivity
            case R.id.preferences:
                intent = SettingsActivity.MakeIntent(this);
                startActivityForResult(intent, CHANGE_SETTINGS);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Handle results from SettingsActivity
        if (requestCode == CHANGE_SETTINGS && resultCode == RESULT_OK) {
            IStreamService service = mServiceFragment.getStreamService();
            // Set the audio gain
            if (data.getBooleanExtra("SET_AUDIO_GAIN", false) && service != null) {
                try {
                    service.setGain(SettingsActivity.getAACGain(this));
                } catch (RemoteException e) {
                    Log.e(TAG, "cannot set the gain", e);
                }
            }
            // Restart the StreamServer
            if (data.getBooleanExtra("RESTART_STREAM_SERVER", false) && service != null) {
                try {
                    service.restart("StreamServer");
                } catch (RemoteException e) {
                    Log.e(TAG, "cannot restart the StreamServer", e);
                }
            }
            // Restart the Recorder
            if (data.getBooleanExtra("RESTART_RECORDER", false) && service != null) {
                try {
                    service.restart("Recorder");
                } catch (RemoteException e) {
                    Log.e(TAG, "cannot restart the Recorder", e);
                }
            }
            // Restart the MangocamAdapter
            if (data.getBooleanExtra("RESTART_MANGO_ADAPTER", false) && service != null) {
                try {
                    service.restart("MangocamAdapter");
                } catch (RemoteException e) {
                    Log.e(TAG, "cannot restart the MangocamAdapter", e);
                }
            }
            // Restart the AngelcamAdapter
            if (data.getBooleanExtra("RESTART_ANGEL_ADAPTER", false) && service != null) {
                try {
                    service.restart("AngelcamAdapter");
                } catch (RemoteException e) {
                    Log.e(TAG, "cannot restart the AngelcamAdapter", e);
                }
            }
        }
    }

    @Override
    public void onWiFiAvailableChange(boolean available) {
        if (available) {
            mOverlayFragment.setAddress(
                    Utils.getIPAddress(this),
                    SettingsActivity.getServerPort(this));
        } else {
            mOverlayFragment.setAddress(null, 0);
        }
    }

    @Override
    public void onMobileAvailableChange(boolean available) {
    }

    @Override
    public void onMute(boolean mute) {
        IStreamService service = mServiceFragment.getStreamService();
        if (service != null) {
            try {
                service.setMute(mute);
            } catch (RemoteException e) {
                Log.e(TAG, "failed to set mute", e);
            }
        }
    }

    @Override
    public void onDimmerStarted(DimmerFragment dimmer) {
        // When the dimmer is shown, ensure that we are in fullscreen
        enterFullScreen();
    }

    @Override
    public void onDimmerStopped(DimmerFragment dimmer) {
        // When the dimmer stops, also exit fullscreen
        // This way, when we will enter the fullscreen again,
        // a new dimmer will be started
        exitFullScreen();
    }

    /**
     * Callback to handle StreamService notifications.
     */
    private class StreamServiceCallBack extends IStreamServiceCallBack.Stub {
        @Override
        public void onStreamingStarted() throws RemoteException {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mStatusFragment.setStreaming(true);
                    if (mDimmerFragment != null)
                        mDimmerFragment.setStreaming(true);
                }
            });
        }

        @Override
        public void onStreamingStopped() throws RemoteException {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mStatusFragment.setStreaming(false);
                    if (mDimmerFragment != null)
                        mDimmerFragment.setStreaming(false);
                }
            });
        }

        @Override
        public void onAudioStarted() throws RemoteException {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mStatusFragment.setAudioStreaming(true);
                }
            });
        }

        @Override
        public void onAudioStopped() throws RemoteException {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mStatusFragment.setAudioStreaming(false);
                }
            });
        }

        @Override
        public void onAdapterConnected(String adapter) throws RemoteException {
            if (adapter.equals("Mangocam")) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mStatusFragment.setMangocamConnected(true);
                    }
                });
            } else if (adapter.equals("Angelcam")) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mStatusFragment.setAngelcamConnected(true);
                    }
                });
            }
        }

        @Override
        public void onAdapterDisconnected(String adapter) throws RemoteException {
            if (adapter.equals("Mangocam")) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mStatusFragment.setMangocamConnected(false);
                    }
                });
            } else if (adapter.equals("Angelcam")) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mStatusFragment.setAngelcamConnected(false);
                    }
                });
            }
        }

        @Override
        public void onLog(final int code, final String message) throws RemoteException {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mOverlayFragment.setLog(message);
                }
            });
        }

        @Override
        public void onFrameSizeChanged(final Point size) throws RemoteException {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mOverlayFragment.setFrameSize(size);
                    // Set the preview if not already done in surfaceCreated
                    if (mSetPreviewNeeded) {
                        try {
                            setPreview(size);
                            mSetPreviewNeeded = false;
                        } catch (RemoteException e) {
                            Log.e(TAG, "cannot set the preview surface", e);
                        }
                    }
                }
            });
        }

        @Override
        public void onBitrateChanged(final int video, final int audio) throws RemoteException {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mOverlayFragment.setBitrate(video, audio);
                }
            });
        }

        @Override
        public void onZoom(final float zoom) throws RemoteException {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mOverlayFragment.setZoom(zoom);
                }
            });
        }

        @Override
        public void onTorch(boolean state) throws RemoteException {
        }

        @Override
        public void onMute(final boolean mute) throws RemoteException {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mStatusFragment.setMute(mute);
                }
            });
        }

        @Override
        public void onFrameRate(final float fps) throws RemoteException {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mOverlayFragment.setAverageFrameRate(fps);
                }
            });
        }

        @Override
        public void onScreenCapture(final boolean authorized) throws RemoteException {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (authorized) {
                        new AlertDialog.Builder(MainActivity.this)
                                .setIcon(android.R.drawable.ic_dialog_info)
                                .setTitle(R.string.dialog_screen_capture_title)
                                .setMessage(R.string.dialog_screen_capture_authorized)
                                .setPositiveButton(R.string.dialog_yes_button, new DialogInterface.OnClickListener() {
                                    public void onClick(final DialogInterface dialog, final int id) {
                                        finish(false);
                                    }
                                })
                                .setNegativeButton(R.string.dialog_no_button, null)
                                .create()
                                .show();
                    } else {
                        new AlertDialog.Builder(MainActivity.this)
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setTitle(R.string.dialog_screen_capture_title)
                                .setCancelable(false)
                                .setMessage(R.string.dialog_screen_capture_not_authorized)
                                .setPositiveButton(R.string.dialog_yes_button, new DialogInterface.OnClickListener() {
                                    public void onClick(final DialogInterface dialog, final int id) {
                                        finish(true);
                                    }
                                })
                                .setNegativeButton(R.string.dialog_no_button, null)
                                .create()
                                .show();

                    }
                }
            });
        }
    }

    /**
     * Gesture listener to handle user interaction.
     */
    private class CameraZoomGestureListener
            implements
            ScaleGestureDetector.OnScaleGestureListener,
            GestureDetector.OnGestureListener {

        float mStartZoom;

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public void onShowPress(MotionEvent e) {
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            // Start autofocus
            if (!isFullScreen()) {
                IStreamService service = mServiceFragment.getStreamService();
                if (service != null) {
                    RelativeLayout.LayoutParams params =
                            (RelativeLayout.LayoutParams) mPreviewView.getLayoutParams();
                    float w = params.width;
                    float h = params.height;
                    float x = e.getX();
                    float y = e.getY();
                    float cx = (x - w / 2f) / w * 2000f;
                    float cy = (y - h / 2f) / h * 2000f;
                    try {
                        service.autoFocus((int) cx, (int) cy);
                    } catch (RemoteException e1) {
                        Log.e(TAG, "cannot start autofocus", e1);
                    }
                }
            }
            // Exit the fullscreen
            exitFullScreen();
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            return false;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            // Stop the dimmer
            stopDimmer();
            // Store current camera zoom
            IStreamService service = mServiceFragment.getStreamService();
            if (service != null) {
                try {
                    mStartZoom = service.getZoom();
                } catch (RemoteException e) {
                    mStartZoom = 1.0f;
                }
            }
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            // Set the new camera zoom
            IStreamService service = mServiceFragment.getStreamService();
            if (service != null) {
                float zoom = mStartZoom * detector.getScaleFactor();
                try {
                    service.setZoom(zoom);
                } catch (RemoteException e) {
                    Log.e(TAG, "cannot set the zoom", e);
                }
            }
            return false;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            // Save current camera zoom
            IStreamService service = mServiceFragment.getStreamService();
            if (service != null) {
                try {
                    SettingsActivity.setCameraZoom(MainActivity.this, service.getZoom());
                } catch (RemoteException e) {
                    SettingsActivity.setCameraZoom(MainActivity.this, 1.0f);
                }
            }
            // Start the dimmer
            if (SettingsActivity.getDimScreen(MainActivity.this))
                startDimmer();
        }
    }
}

// TODO: log displaying incoming connections and streams requests
// TODO: centralized MJPEG compression
// TODO: sensor tab: temperature, ...
// TODO: check latest google compat library (com.takisoft.fix:preference still needed?)
