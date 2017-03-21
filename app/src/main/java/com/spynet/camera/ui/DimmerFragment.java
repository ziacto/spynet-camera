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

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.view.View;

/**
 * A {@link Fragment} used to hide the camera preview.
 */
public abstract class DimmerFragment extends Fragment {

    protected final static int DIMMER_FADE_IN_TIME = 2000;  // Fade-in time
    protected final static int DIMMER_FADE_OUT_TIME = 500;  // Fade-out time

    protected final Handler mHandler;   // The Handler used to control the UI states
    protected boolean mIsStreaming;     // Indicates that the streaming is active

    private Callback mCallback;         // The Callback to communicate with the client Activity
    private boolean mIsStarted;         // Whether the dimmer is started

    /**
     * The clients should implement this interface to keep track of the DimmerFragment
     * state changes.
     */
    public interface Callback {
        /**
         * Indicates that the dimmer is now started.<br>
         *
         * @param dimmer the DimmerFragment that generates this event
         */
        void onDimmerStarted(DimmerFragment dimmer);

        /**
         * Indicates that the dimmer is now stopped.
         *
         * @param dimmer the DimmerFragment that generates this event
         */
        void onDimmerStopped(DimmerFragment dimmer);
    }

    /**
     * Creates a new DimmerFragment object.
     */
    public DimmerFragment() {
        mHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                DimmerFragment.this.handleMessage(msg);
                return false;
            }
        });
    }

    /**
     * Sets the streaming state.
     */
    public void setStreaming(boolean streaming) {
        mIsStreaming = streaming;
    }

    /**
     * @return {@code true} if the dimmer is started, {@code false} otherwise
     */
    public boolean isStarted() {
        return mIsStarted;
    }

    /**
     * To be implemented by the classes that extend DimmerFragment.<br>
     * Subclasses may use the Handler {@code mHandler} to send messages to this function.
     */
    protected abstract void handleMessage(Message msg);

    /**
     * Indicates that the dimmer is now started.
     */
    protected void onDimmerStarted() {
        mIsStarted = true;
        if (mCallback != null)
            mCallback.onDimmerStarted(this);
    }

    /**
     * Notifies that the dimmer is now stopped.
     */
    protected void onDimmerStopped() {
        mIsStarted = true;
        if (mCallback != null)
            mCallback.onDimmerStopped(this);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof Callback)
            mCallback = (Callback) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallback = null;
    }

    /**
     * Fades-in the dimmer and generates onStarted().
     */
    protected void fadeIn() {
        final View view = getView();
        if (view != null) {
            view.setAlpha(0);
            view.setVisibility(View.VISIBLE);
            view.animate()
                    .alpha(1.0f)
                    .setDuration(DIMMER_FADE_IN_TIME)
                    .withStartAction(new Runnable() {
                        @Override
                        public void run() {
                            onDimmerStarted();
                        }
                    })
                    .start();
        }
    }

    /**
     * Fades-out the dimmer and generates onStopped().
     */
    protected void fadeOut() {
        final View view = getView();
        if (view != null) {
            view.animate()
                    .alpha(0.0f)
                    .setDuration(DIMMER_FADE_OUT_TIME)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            view.setVisibility(View.GONE);
                            onDimmerStopped();
                        }
                    })
                    .start();
        }
    }
}
