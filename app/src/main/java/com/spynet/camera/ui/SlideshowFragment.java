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
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Message;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.Display;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.spynet.camera.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A {@link Fragment} used to hide the camera preview.<br>
 * A slideshow will be shown to simulate a photo-frame.
 */
public class SlideshowFragment extends DimmerFragment {


    private final static int FLING_MIN_SPEED = 2500;    // Minimum speed to handle fling, in pixels per second
    private final static int ACTION_FIRST_IMAGE = 1;    // Show the first image
    private final static int ACTION_NEXT_IMAGE = 2;     // Show the next image
    private final static int ACTION_PREV_IMAGE = 3;     // Show the previous image
    private final static int FIRST_IMAGE_DELAY = 20000; // Delay before to reduce screen luminosity
    private final static int IMAGE_DELAY = 30000;       // Delay before to show next/previous image

    private ViewFlipper mViewFlipper;                   // The ViewFlipper that allows to animate the pictures
    private ImageView[] mViewImage;                     // The ImageView that will show the pictures
    private Point mDisplaySize;                         // The screen size
    private List<String> mImages;                       // List of the path of all the available images
    private int mImageIndex;                            // Index of the currently shown image
    private int mActiveImage;                           // The ViewFlipper visible child
    private volatile boolean mIsStopped;                // Specifies that the Fragment has been stopped

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case ACTION_FIRST_IMAGE:
                // Load the first image
                if (mImages != null && mImages.size() > 0) {
                    mImageIndex = (new Random()).nextInt(mImages.size());
                    showImage(mImages.get(mImageIndex), mViewImage[mActiveImage]);
                } else {
                    Toast.makeText(getContext(), R.string.msg_no_image, Toast.LENGTH_LONG).show();
                }
                // Set the listener to handle touch events
                final GestureDetector gestureDetector =
                        new GestureDetector(getContext(), new SlideshowGestureListener());
                mViewFlipper.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        return gestureDetector.onTouchEvent(event);
                    }
                });
                // Show the slideshow screen
                fadeIn();
                Toast.makeText(getContext(), R.string.msg_exit_slideshow, Toast.LENGTH_LONG).show();
                // Trigger the next image, if any
                if (mImages != null && mImages.size() > 1)
                    mHandler.sendEmptyMessageDelayed(ACTION_NEXT_IMAGE, IMAGE_DELAY);
                break;
            case ACTION_NEXT_IMAGE:
                // Load the next image
                mActiveImage = ++mActiveImage % 2;
                mImageIndex = ++mImageIndex % mImages.size();
                showImage(mImages.get(mImageIndex), mViewImage[mActiveImage]);
                mViewFlipper.setInAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.in_left));
                mViewFlipper.setOutAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.out_right));
                mViewFlipper.showNext();
                // Trigger the next image
                mHandler.sendEmptyMessageDelayed(ACTION_NEXT_IMAGE, IMAGE_DELAY);
                break;
            case ACTION_PREV_IMAGE:
                // Load the previous image
                mActiveImage = (--mActiveImage + 2) % 2;
                mImageIndex = (--mImageIndex + mImages.size()) % mImages.size();
                showImage(mImages.get(mImageIndex), mViewImage[mActiveImage]);
                mViewFlipper.setInAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.in_right));
                mViewFlipper.setOutAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.out_left));
                mViewFlipper.showPrevious();
                // Trigger the next image
                mHandler.sendEmptyMessageDelayed(ACTION_PREV_IMAGE, IMAGE_DELAY);
                break;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDisplaySize = new Point();
        Display display = getActivity().getWindowManager().getDefaultDisplay();
        display.getRealSize(mDisplaySize);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_slideshow, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mViewFlipper = (ViewFlipper) view;
        mViewImage = new ImageView[2];
        mViewImage[0] = (ImageView) mViewFlipper.findViewById(R.id.image1);
        mViewImage[1] = (ImageView) mViewFlipper.findViewById(R.id.image2);
    }

    @Override
    public void onStart() {
        super.onStart();
        mIsStopped = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                mImages = getImagesList(getContext());
                if (!mIsStopped)
                    mHandler.sendEmptyMessageDelayed(ACTION_FIRST_IMAGE, FIRST_IMAGE_DELAY);
            }
        }).start();
    }

    @Override
    public void onStop() {
        super.onStop();
        mIsStopped = true;
        mHandler.removeMessages(ACTION_FIRST_IMAGE);
        mHandler.removeMessages(ACTION_NEXT_IMAGE);
        mHandler.removeMessages(ACTION_PREV_IMAGE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mIsStopped = true;
        mHandler.removeMessages(ACTION_FIRST_IMAGE);
        mHandler.removeMessages(ACTION_NEXT_IMAGE);
        mHandler.removeMessages(ACTION_PREV_IMAGE);
    }

    /**
     * Shows the next image in the slideshow.
     *
     * @return {@code true} if the request has been accepted, {@code false} if not
     */
    public boolean nextImage() {
        if (mImages != null && mImages.size() > 1) {
            mHandler.removeMessages(ACTION_NEXT_IMAGE);
            mHandler.removeMessages(ACTION_PREV_IMAGE);
            mHandler.sendEmptyMessage(ACTION_NEXT_IMAGE);
            return true;
        }
        return false;
    }

    /**
     * Shows the previous image in the slideshow.
     *
     * @return {@code true} if the request has been accepted, {@code false} if not
     */
    public boolean prevImage() {
        if (mImages != null && mImages.size() > 1) {
            mHandler.removeMessages(ACTION_NEXT_IMAGE);
            mHandler.removeMessages(ACTION_PREV_IMAGE);
            mHandler.sendEmptyMessage(ACTION_PREV_IMAGE);
            return true;
        }
        return false;
    }

    /**
     * @return the list of all the available images, {@code null} if there's none
     * or the app does not have permissions to access the external storage
     */
    private List<String> getImagesList(Context context) {
        if (context != null) {
            int readStoragePermissions = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE);
            if (readStoragePermissions == PackageManager.PERMISSION_GRANTED) {
                String[] projection = {MediaStore.Images.Media.DATA};
                Cursor cursor = context.getContentResolver().query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection, null, null, null);
                if (cursor != null) {
                    ArrayList<String> result = new ArrayList<>(cursor.getCount());
                    if (cursor.moveToFirst()) {
                        int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                        do {
                            result.add(cursor.getString(dataColumn));
                        } while (cursor.moveToNext());
                    }
                    cursor.close();
                    return result;
                }
            }
        }
        return null;
    }

    /**
     * Load an image from file and display it on an ImageView.
     * The image is automatically rescaled based on the screen size.
     */
    private void showImage(String image, ImageView view) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(image, options);
            options.inSampleSize = calculateInSampleSize(options);
            options.inJustDecodeBounds = false;
            Bitmap bmp = BitmapFactory.decodeFile(image, options);
            if (bmp != null)
                view.setImageBitmap(bmp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Calculates the optimal bitmap sampling based on the screen size.
     */
    private int calculateInSampleSize(BitmapFactory.Options options) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > mDisplaySize.y || width > mDisplaySize.x) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) > mDisplaySize.y
                    || (halfWidth / inSampleSize) > mDisplaySize.x) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /**
     * Gesture listener to handle user interaction.
     */
    private class SlideshowGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            fadeOut();
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            // Handle slideshow by scrolling images
            if (velocityX > FLING_MIN_SPEED) {
                nextImage();
                return true;
            } else if (velocityX < -FLING_MIN_SPEED) {
                prevImage();
                return true;
            } else {
                return false;
            }
        }
    }
}
