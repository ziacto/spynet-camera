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
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.spynet.camera.media.Recorder;

import org.jetbrains.annotations.NotNull;

/**
 * An {@link android.app.Activity} that will fire the screen capture request
 * to the user.
 */
public class ScreenCaptureRequestActivity extends AppCompatActivity {

    private static final int SCREEN_CAPTURE_REQUEST_ID = 2536;

    private final String TAG = getClass().getSimpleName();

    /**
     * Factory method that makes an explicit intent used to start the
     * ScreenCaptureRequestActivity.
     *
     * @param context the context in which the Activity will execute
     * @return the created Intent
     */
    @NotNull
    public static Intent MakeIntent(Context context) {
        return new Intent(context, ScreenCaptureRequestActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(manager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST_ID);
        } else {
            Log.w(TAG, "Media projection requires API " + android.os.Build.VERSION_CODES.LOLLIPOP);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SCREEN_CAPTURE_REQUEST_ID) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                Intent intent = new Intent(Recorder.ACTION_START_SCREEN_CAPTURE);
                intent.putExtra(Recorder.EXTRA_MEDIA_PROJECTION_RESULT, resultCode);
                intent.putExtra(Recorder.EXTRA_MEDIA_PROJECTION_INTENT, data);
                sendBroadcast(intent);
            }
            finish();
        }
    }
}
