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
import android.util.AttributeSet;

/**
 * Extends {@link com.takisoft.fix.support.v7.preference.EditTextPreference} to automatically
 * update the summary.
 */
public class EditTextPreference extends com.takisoft.fix.support.v7.preference.EditTextPreference {

    private CharSequence mSummary;

    public EditTextPreference(Context context) {
        super(context);
        mSummary = super.getSummary();
    }

    public EditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mSummary = super.getSummary();
    }

    public EditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mSummary = super.getSummary();
    }

    @Override
    public void setText(String text) {
        super.setText(text);
        if (mSummary != null)
            setSummary(String.format(mSummary.toString(), text == null ? "" : text));
    }
}
