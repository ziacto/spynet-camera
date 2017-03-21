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

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.lang.ref.WeakReference;

/**
 * Extends Activity to implement immersive full-screen mode.
 */
public class ImmersiveModeActivity extends AppCompatActivity {

    private final static int INITIAL_HIDE_DELAY = 8000; // Time after which to hide the bars the first time
    private final static int HIDE_DELAY = 4000;         // Time after which to hide the bars

    private View mDecor;                                // Top-level window decor view
    private Handler mHideSystemUiHandler;               // Handler to hide the UI bars on demand
    private boolean mIsFullScreen;                      // Flag that indicates that fullscreen is active

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get the decor view
        mDecor = getWindow().getDecorView();

        // Ensure that the navigation bar will be transparent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        }

        // Be sure to set the correct UI flags
        showSystemUI();

        // Setup the Handler to hide the bars after a specified delay
        mHideSystemUiHandler = new HideSystemUiHandler(this);

        // Setup the GestureDetector used to detect when the user click on the activity
        // (better than OnClickListener because it doesn't trigger on swipe gesture)
        View contentView = findViewById(android.R.id.content);
        contentView.setClickable(true);
        final GestureDetector clickDetector =
                new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        exitFullScreen();
                        return true;
                    }
                });
        contentView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return clickDetector.onTouchEvent(event);
            }
        });
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Hide the bars when the activity starts
        delayedHide(INITIAL_HIDE_DELAY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Hide the bars when the activity gains the focus
            delayedHide(HIDE_DELAY);
        } else {
            // Remove pending events to keep the bars status
            // when the activity loose the focus
            dropPendingHideRequests();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Remove pending events to prevent further fullscreen
        dropPendingHideRequests();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove pending events to prevent further fullscreen
        dropPendingHideRequests();
    }

    /**
     * Enters the fullscreen mode by hiding the UI bars.
     */
    protected void enterFullScreen() {
        hideSystemUI();
        onEnterFullScreen();
    }

    /**
     * Exits the fullscreen mode by showing the UI bars.
     */
    protected void exitFullScreen() {
        showSystemUI();
        onExitFullScreen();
        delayedHide(HIDE_DELAY);
    }

    /**
     * @return {@code true} if fullscreen is active, {@code false} otherwise
     */
    protected boolean isFullScreen() {
        return mIsFullScreen;
    }

    /**
     * Hook method to notify that the activity enters fullscreen mode.
     */
    protected void onEnterFullScreen() {
    }

    /**
     * Hook method to notify that the activity exits fullscreen mode.
     */
    protected void onExitFullScreen() {
    }

    /**
     * The Handler used to hide the UI bars on demand, after the specified delay.
     */
    private static class HideSystemUiHandler extends Handler {

        private final WeakReference<ImmersiveModeActivity> activityWeakReference;

        public HideSystemUiHandler(ImmersiveModeActivity activity) {
            activityWeakReference = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            ImmersiveModeActivity activity = activityWeakReference.get();
            if (activity != null) {
                activity.hideSystemUI();
                activity.onEnterFullScreen();
            }
        }
    }

    /**
     * Hides the bars after a delay in milliseconds.
     */
    private void delayedHide(int delayMillis) {
        mHideSystemUiHandler.removeMessages(0);
        mHideSystemUiHandler.sendEmptyMessageDelayed(0, delayMillis);
    }

    /**
     * Drops pending hide requests.
     */
    private void dropPendingHideRequests() {
        mHideSystemUiHandler.removeMessages(0);
    }

    /**
     * Hides the system bars.
     * Set the IMMERSIVE flag.
     * Set the content to appear under the system bars so that the content
     * doesn't resize when the system bars hide and show.
     */
    private void hideSystemUI() {
        int visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                | View.SYSTEM_UI_FLAG_FULLSCREEN; // hide status bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            visibility |= View.SYSTEM_UI_FLAG_IMMERSIVE;
        }
        mDecor.setSystemUiVisibility(visibility);
        mIsFullScreen = true;
    }

    /**
     * Shows the system bars.
     * Remove all the flags except for the ones that make the content
     * appear under the system bars.
     */
    private void showSystemUI() {
        int visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        mDecor.setSystemUiVisibility(visibility);
        mIsFullScreen = false;
    }
}
