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
import android.graphics.Point;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.spynet.camera.R;

/**
 * A {@link Fragment} to display overlay information.<br>
 */
public class OverlayFragment extends Fragment {

    private View mViewRoot;                 // The root View
    private TextView mTextConnection;       // TextView to display connection information
    private TextView mTextResolution;       // TextView to display current video resolution
    private TextView mTextBitrate;          // TextView to display current video stream bitrate
    private TextView mTextZoom;             // TextView to display current camera zoom
    private TextView mTextFps;              // TextView to display current video speed
    private TextView mTextLog;              // TextView to display log messages

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_overlay, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mViewRoot = view;
        mTextConnection = (TextView) mViewRoot.findViewById(R.id.addressText);
        mTextResolution = (TextView) mViewRoot.findViewById(R.id.resolutionText);
        mTextBitrate = (TextView) mViewRoot.findViewById(R.id.bitrateText);
        mTextZoom = (TextView) mViewRoot.findViewById(R.id.zoomText);
        mTextFps = (TextView) mViewRoot.findViewById(R.id.fpsText);
        mTextLog = (TextView) mViewRoot.findViewById(R.id.logText);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mViewRoot = null;
        mTextConnection = null;
        mTextResolution = null;
        mTextBitrate = null;
        mTextZoom = null;
        mTextFps = null;
        mTextLog = null;
    }

    public void setVisible(boolean visible) {
        if (mViewRoot != null) {
            mViewRoot.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public void setFrameSize(@Nullable Point size) {
        if (mTextResolution != null) {
            if (size != null) {
                mTextResolution.setText(String.format(
                        getResources().getString(R.string.info_resolution),
                        size.x, size.y));
                mTextResolution.setVisibility(View.VISIBLE);
            } else {
                mTextResolution.setText("");
                mTextResolution.setVisibility(View.GONE);
            }
        }
    }

    public void setBitrate(long video, long audio) {
        if (mTextBitrate != null) {
            mTextBitrate.setText(String.format(
                    getResources().getString(R.string.info_bitrate),
                    video / 1000,
                    audio / 1000));
            mTextBitrate.setVisibility(video > 0 || audio > 0 ? View.VISIBLE : View.GONE);
        }
    }

    public void setZoom(float zoom) {
        if (mTextZoom != null) {
            mTextZoom.setText(String.format(
                    getResources().getString(R.string.info_zoom),
                    zoom));
        }
    }

    public void setAverageFrameRate(float fps) {
        if (mTextFps != null) {
            if (fps > 0) {
                mTextFps.setText(String.format(getString(R.string.info_fps), fps));
                mTextFps.setVisibility(View.VISIBLE);
            } else {
                mTextFps.setText(R.string.info_fps_unknown);
                mTextFps.setVisibility(View.GONE);
            }
        }
    }

    public void setAddress(@Nullable String ip, int port) {
        if (mTextConnection != null) {
            if (ip != null) {
                String format = getResources().getString(R.string.info_ip);
                mTextConnection.setText(String.format(format, ip, port));
            } else {
                mTextConnection.setText(R.string.info_ip_unavailable);
            }
        }
    }

    public void setLog(@Nullable String message) {
        if (mTextLog != null) {
            if (message == null) {
                mTextLog.setVisibility(View.GONE);
                mTextLog.setText("");
            } else {
                mTextLog.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
                mTextLog.setText(message);
            }
        }
    }
}
