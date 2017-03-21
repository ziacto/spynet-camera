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

package com.spynet.camera.network.Mangocam;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;

import com.spynet.camera.R;
import com.spynet.camera.ui.SettingsActivity;

import org.jetbrains.annotations.NotNull;

/**
 * Defines the notifier used to send Mangocam Connect notifications to the user.
 */
public class MangocamNotification {

    // Default notification
    public final static int TYPE_DEFAULT = 0;
    // Register to Mangocam
    public final static int TYPE_REGISTER = 1;
    // Action to show a dialog
    public final static String ACTION_SHOW_DIALOG = "com.spynet.camera.network.Mangocam.showdialog";

    private final static int NOTIFICATION_ID = 122333;

    private final Context mContext;                         // The context that uses the MangocamAdapter
    private final NotificationCompat.Builder mBuilder;      // Notification builder
    private final NotificationManager mNotificationManager; // Notification manager

    /**
     * Creates a new MangocamNotification object.
     *
     * @param context the context where the MangocamNotification is used
     */
    public MangocamNotification(@NotNull Context context) {
        mContext = context;
        mBuilder = new NotificationCompat.Builder(mContext)
                .setVibrate(new long[0]) // dummy vibration to show as peeking notification
                .setSmallIcon(R.drawable.ic_cloud_upload)
                .setColor(0xF96E03) // the main color used in the Mangocam web site
                .setAutoCancel(true)
                .setShowWhen(false);
        mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /**
     * Notify the user about the connection progress.
     *
     * @param type        specifies what to notify, one of the TYPE_* definitions
     * @param title       notification title
     * @param text        notification text
     * @param description detailed description, shown when expanded
     */
    public void notify(int type, String title, String text, String description) {
        notify(type, title, text, description, null);
    }

    /**
     * Notify the user about the connection progress.
     *
     * @param type        specifies what to notify, one of the TYPE_* definitions
     * @param title       notification title
     * @param text        notification text
     * @param description detailed description, shown when expanded
     * @param link        link to visit to add the camera
     */
    public void notify(int type, String title, String text, String description, String link) {

        Intent intent;
        PendingIntent pendingIntent = null;
        int priority = NotificationCompat.PRIORITY_LOW;
        Uri sound = Uri.parse("android.resource://" + mContext.getPackageName() + "/" + R.raw.notification2);
        boolean ongoing = false;

        switch (type) {
            case TYPE_REGISTER:
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                pendingIntent = PendingIntent.getActivity(mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                priority = NotificationCompat.PRIORITY_HIGH;
                sound = Uri.parse("android.resource://" + mContext.getPackageName() + "/" + R.raw.notification1);
                ongoing = true;
                break;
        }

        mBuilder.setContentIntent(pendingIntent)
                .setPriority(priority)
                .setSound(SettingsActivity.getSilentNotifications(mContext) ? null : sound)
                .setOngoing(ongoing)
                .setTicker(title)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(description == null ? null :
                        (new NotificationCompat.BigTextStyle()).bigText(description));
        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    /**
     * Removes the notification.
     */
    public void cancel() {
        mNotificationManager.cancel(NOTIFICATION_ID);
    }
}
