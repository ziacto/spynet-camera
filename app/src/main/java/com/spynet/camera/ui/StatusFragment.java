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
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.spynet.camera.R;

/**
 * A {@link Fragment} to display streaming status.<br>
 */
public class StatusFragment extends Fragment {

    private Callback mCallback;             // The Callback to communicate with the client Activity
    private View mViewRoot;                 // The root View
    private ImageView mImageStreaming;      // ImageView to show streaming state
    private ImageView mImageAudio;          // ImageView to show audio state
    private ImageView mImageMangocam;       // ImageView to show MangocamAdapter state
    private ImageView mImageAngelcam;       // ImageView to how AngelcamAdapter state
    private boolean mIsMute;                // The mute state

    /**
     * The clients should implement this interface to keep track of the DimmerFragment
     * state changes.
     */
    public interface Callback {
        /**
         * Indicates that the mute state has changed.
         *
         * @param mute the mute state
         */
        void onMute(boolean mute);
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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_status, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mViewRoot = view;
        mImageStreaming = (ImageView) mViewRoot.findViewById(R.id.streaming);
        mImageAudio = (ImageView) mViewRoot.findViewById(R.id.audio);
        mImageMangocam = (ImageView) mViewRoot.findViewById(R.id.mangocam);
        mImageAngelcam = (ImageView) mViewRoot.findViewById(R.id.angelcam);
        mImageAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCallback != null)
                    mCallback.onMute(!mIsMute);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mViewRoot = null;
        mImageStreaming = null;
        mImageAudio = null;
        mImageMangocam = null;
        mImageAngelcam = null;
    }

    public void setStreaming(boolean streaming) {
        if (mImageStreaming != null) {
            mImageStreaming.setVisibility(streaming ? View.VISIBLE : View.GONE);
        }
    }

    public void setAudioStreaming(boolean streaming) {
        if (mImageAudio != null) {
            mImageAudio.setVisibility(streaming ? View.VISIBLE : View.GONE);
        }
    }

    public void setMute(boolean mute) {
        mIsMute = mute;
        if (mImageAudio != null) {
            mImageAudio.setImageResource(mute ? R.drawable.ic_mic_off : R.drawable.ic_mic);
        }
    }

    public void setMangocamConnected(boolean connected) {
        if (mImageMangocam != null) {
            mImageMangocam.setVisibility(connected ? View.VISIBLE : View.GONE);
        }
    }

    public void setAngelcamConnected(boolean connected) {
        if (mImageAngelcam != null) {
            mImageAngelcam.setVisibility(connected ? View.VISIBLE : View.GONE);
        }
    }
}
