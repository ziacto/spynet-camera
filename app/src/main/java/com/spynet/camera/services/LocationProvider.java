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

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Defines the provider that will notify the client on location changes.<br>
 * It handles two different location update modes: coarse (default) and fine.<br>
 * 'coarse' is for power-saving (device is supposed to don't move so much),
 * while 'fine' is for high-performance (the device is moving).<br>
 * It is possible to switch between the two modes, but the coarse mode will be automatically
 * selected if no new requests are received for the fine mode within a predefined timeout time.
 */
public class LocationProvider implements Closeable, LocationListener {

    // Minimum time interval between location updates, in milliseconds
    private static final int FINE_MIN_LOCATION_TIME = 5 * 1000;
    private static final int COARSE_MIN_LOCATION_TIME = 5 * 60 * 1000;
    // Minimum distance between location updates, in meters
    private static final int FINE_MIN_LOCATION_DISTANCE = 5;
    private static final int COARSE_MIN_LOCATION_DISTANCE = 10;
    // The fine mode timeout, in milliseconds
    private static final int FINE_TIMEOUT = 30 * 1000;
    // Two minutes, in milliseconds
    private static final int TWO_MINUTES = 2 * 60 * 1000;

    protected final String TAG = getClass().getSimpleName();

    private final Context mContext;                     // The context that uses the LocationProvider
    private final LocationManager mLocationManager;     // The location manager
    private final Looper mLooper;                       // The looper that will receive location notifications
    private final Timer mTimeoutTimer;                  // The timer used to switch back to coarse mode
    private LocationCallback mCallback;                 // The LocationCallback implemented by mContext
    private Location mCurrentLocation;                  // Last known location
    private volatile boolean mIsFineMode;               // Whether the fine location is active
    private volatile int mFineRequestsNum;              // The number of fine location requests (since last check)

    /**
     * A client may implement this interface to receive audio and video data buffers
     * as they are available.
     */
    public interface LocationCallback {
        /**
         * Called when a new {@link Location} is available.<br>
         * This method is always called within the main thread of its process.
         *
         * @param location the new {@link Location}
         */
        void onLocationAvailable(Location location);
    }

    /**
     * Creates a new LocationProvider object.
     *
     * @param context the context where the LocationProvider is used; it should implement
     *                {@link LocationCallback}
     */
    public LocationProvider(@NotNull Context context) {
        mContext = context;
        if (mContext instanceof LocationCallback) {
            mCallback = (LocationCallback) mContext;
        } else {
            Log.w(TAG, "LocationCallback is not implemented by the specified context");
        }
        mLocationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        if (Looper.myLooper() != null) {
            mLooper = Looper.myLooper();
        } else {
            mLooper = Looper.getMainLooper();
        }
        mIsFineMode = true; // allow requestCoarseUpdates() to execute
        requestCoarseUpdates();
        getLastKnownLocation();
        if (mCallback != null) {
            mCallback.onLocationAvailable(mCurrentLocation);
        }
        mTimeoutTimer = new Timer();
        mTimeoutTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (LocationProvider.this) {
                    if (mFineRequestsNum == 0 && mIsFineMode) {
                        requestCoarseUpdates();
                    }
                    mFineRequestsNum = 0;
                }
            }
        }, FINE_TIMEOUT, FINE_TIMEOUT);
    }

    /**
     * Closes the LocationProvider.
     */
    @Override
    public void close() {
        mTimeoutTimer.cancel();
        mLocationManager.removeUpdates(this);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (isBetterLocation(location)) {
            mCurrentLocation = location;
            if (mCallback != null) {
                mCallback.onLocationAvailable(mCurrentLocation);
            }
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.v(TAG, "location status hanged: " + provider + "=" + status);
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.v(TAG, "location provider enabled: " + provider);
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.v(TAG, "location provider disabled: " + provider);
    }

    /**
     * Sets location updates for power saving.
     */
    public void requestCoarseUpdates() {
        synchronized (this) {
            if (!mIsFineMode)
                return;
            mIsFineMode = false;
            mFineRequestsNum = 0;
        }
        int fineLocationPermission =
                ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION);
        int coarseLocationPermission =
                ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (fineLocationPermission == PackageManager.PERMISSION_GRANTED ||
                coarseLocationPermission == PackageManager.PERMISSION_GRANTED) {
            Criteria c = new Criteria();
            c.setAccuracy(Criteria.ACCURACY_COARSE);
            c.setPowerRequirement(Criteria.POWER_LOW);
            c.setAltitudeRequired(false);
            c.setBearingRequired(false);
            c.setCostAllowed(false);
            c.setSpeedRequired(false);
            mLocationManager.requestLocationUpdates(
                    COARSE_MIN_LOCATION_TIME, COARSE_MIN_LOCATION_DISTANCE, c, this, mLooper);
            Log.d(TAG, "switch to coarse mode");
        } else {
            Log.w(TAG, "no permissions to start coarse mode");
        }
    }

    /**
     * Sets location updates for better performance.
     */
    public synchronized void requestFineUpdates() {

        synchronized (this) {
            mFineRequestsNum++;
            if (mIsFineMode)
                return;
            mIsFineMode = true;
        }
        int fineLocationPermission =
                ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION);
        int coarseLocationPermission =
                ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (fineLocationPermission == PackageManager.PERMISSION_GRANTED ||
                coarseLocationPermission == PackageManager.PERMISSION_GRANTED) {
            Criteria c = new Criteria();
            c.setAccuracy(Criteria.ACCURACY_FINE);
            c.setPowerRequirement(Criteria.POWER_HIGH);
            c.setAltitudeRequired(false);
            c.setBearingRequired(false);
            c.setCostAllowed(false);
            c.setSpeedRequired(false);
            mLocationManager.requestLocationUpdates(
                    FINE_MIN_LOCATION_TIME, FINE_MIN_LOCATION_DISTANCE, c, this, mLooper);
            Log.d(TAG, "switch to fine mode");
        } else {
            Log.w(TAG, "no permissions to start fine mode");
        }
    }

    /**
     * Gets the last known location.
     */
    private void getLastKnownLocation() {
        int fineLocationPermission = ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION);
        int coarseLocationPermission = ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (fineLocationPermission == PackageManager.PERMISSION_GRANTED) {
            mCurrentLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
        if (coarseLocationPermission == PackageManager.PERMISSION_GRANTED) {
            Location location = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (location != null && isBetterLocation(location))
                mCurrentLocation = location;
        }
    }

    /**
     * Determines whether one Location reading is better than the current best fix.
     *
     * @param location the new Location to evaluate
     */
    private boolean isBetterLocation(Location location) {

        if (mCurrentLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - mCurrentLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        if (isSignificantlyNewer) {
            // If it's significantly newer use the new location because the user has likely moved
            return true;
        } else if (isSignificantlyOlder) {
            // If it's significantly older it must be worse
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - mCurrentLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                mCurrentLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /**
     * Checks whether two providers are the same.
     */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null)
            return provider2 == null;
        return provider1.equals(provider2);
    }
}
