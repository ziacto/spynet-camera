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

import android.os.Bundle;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.spynet.camera.R;

/**
 * A {@link Fragment} used to hide the camera preview.<br>
 * The screen will be initially dimmed and then the logo will be shown.<br>
 * The logo will pulse when at least one stream is active to indicate the network activity.
 */
public class LogoFragment extends DimmerFragment {

    private final static int ACTION_DARK = 1;           // Reduce the screen luminosity
    private final static int ACTION_LOGO = 2;           // Show the logo screen
    private final static int DARK_DELAY = 20000;        // Delay before to reduce screen luminosity
    private final static int LOGO_DELAY = 40000;        // Delay before to show the black screen
    private final static int LOGO_FADE_IN_TIME = 2000;  // Logo fade-in time
    private final static int LOGO_FADE_OUT_TIME = 5000; // Logo fade-out time

    private View mViewRoot;                             // The root View
    private View mViewLogo;                             // The logo View
    private boolean mAnimation;                         // Whether the logo animation is running

    @Override
    public void setStreaming(boolean streaming) {
        super.setStreaming(streaming);
        if (mIsStreaming && !mAnimation) fadeInLogo();
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case ACTION_DARK:
                // Set the minimum screen brightness
                WindowManager.LayoutParams layout = getActivity().getWindow().getAttributes();
                layout.screenBrightness = 0.0f;
                getActivity().getWindow().setAttributes(layout);
                // Trigger the dimmer
                mHandler.sendEmptyMessageDelayed(ACTION_LOGO, LOGO_DELAY);
                break;
            case ACTION_LOGO:
                // Set the listener to stop the dimmer on click
                mViewRoot.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        fadeOut();
                    }
                });
                // Show the logo screen
                fadeIn();
                break;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_logo, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mViewRoot = view;
        mViewLogo = mViewRoot.findViewById(R.id.logo);
    }

    @Override
    public void onStart() {
        super.onStart();
        mHandler.sendEmptyMessageDelayed(ACTION_DARK, DARK_DELAY);
        if (mIsStreaming && !mAnimation) fadeInLogo();
    }

    @Override
    public void onStop() {
        super.onStop();
        // Remove pending messages
        mHandler.removeMessages(ACTION_DARK);
        mHandler.removeMessages(ACTION_LOGO);
        // Set the default screen brightness
        int curBrightnessValue;
        WindowManager.LayoutParams layout = getActivity().getWindow().getAttributes();
        try {
            curBrightnessValue = android.provider.Settings.System.getInt(
                    getActivity().getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS);
        } catch (Exception e) {
            curBrightnessValue = 255;
        }
        layout.screenBrightness = curBrightnessValue / 255.0f;
        getActivity().getWindow().setAttributes(layout);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mHandler.removeMessages(ACTION_DARK);
        mHandler.removeMessages(ACTION_LOGO);
    }

    /**
     * Fade-in the logo and finally fade-out it again, do nothing if not streaming.
     */
    private void fadeInLogo() {
        if (mViewLogo != null && mIsStreaming) {
            mAnimation = true;
            mViewLogo.animate()
                    .alpha(1.0f)
                    .setDuration(LOGO_FADE_IN_TIME)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            fadeOutLogo();
                        }
                    })
                    .start();
        } else {
            mAnimation = false;
        }
    }

    /**
     * Fade-out the logo and finally fade-in it again.
     */
    private void fadeOutLogo() {
        if (mViewLogo != null) {
            mViewLogo.animate()
                    .alpha(0.25f)
                    .setDuration(LOGO_FADE_OUT_TIME)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            fadeInLogo();
                        }
                    })
                    .start();
        }
    }
}
