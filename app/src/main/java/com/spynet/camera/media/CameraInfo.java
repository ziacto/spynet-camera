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

package com.spynet.camera.media;

import android.graphics.Point;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines a {@link Parcelable} camera information container.
 */
public class CameraInfo implements Parcelable {

    private final String mDescription;
    private final int mCameraId;
    private final List<Point> mSupportedPreviewSizes;

    /**
     * Creates a new CameraInfo object from Parcel.
     *
     * @param in the input Parcel
     */
    protected CameraInfo(Parcel in) {
        mDescription = in.readString();
        mCameraId = in.readInt();
        mSupportedPreviewSizes = new ArrayList<>();
        in.readTypedList(mSupportedPreviewSizes, Point.CREATOR);
    }

    /**
     * Creates a new CameraInfo object.
     *
     * @param description the camera description
     * @param cameraId    the camera id
     * @param sizes       the preview resolutions supported by the camera.
     */
    public CameraInfo(String description, int cameraId, List<Point> sizes) {
        mDescription = description;
        mCameraId = cameraId;
        mSupportedPreviewSizes = sizes != null ? new ArrayList<>(sizes) : null;
    }

    /**
     * @return the camera description
     */
    public String getDescription() {
        return mDescription;
    }

    /**
     * @return the camera ID
     */
    public int getCameraId() {
        return mCameraId;
    }

    /**
     * @return the list of supported preview sizes
     */
    public List<Point> getSupportedPreviewSizes() {
        return mSupportedPreviewSizes;
    }

    public static final Creator<CameraInfo> CREATOR = new Creator<CameraInfo>() {
        @Override
        public CameraInfo createFromParcel(Parcel in) {
            return new CameraInfo(in);
        }

        @Override
        public CameraInfo[] newArray(int size) {
            return new CameraInfo[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mDescription);
        dest.writeInt(mCameraId);
        dest.writeTypedList(mSupportedPreviewSizes);
    }
}
