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
import android.support.v4.app.Fragment;
import android.content.ServiceConnection;
import android.os.Bundle;

import com.spynet.camera.services.IStreamService;

/**
 * Defines a retained Fragment where to store {@link com.spynet.camera.services.StreamService}
 * references, to keep them persistent between Activity recreation.
 */
public class ServiceFragment extends Fragment {

    private Context mContext;
    private ServiceConnection mConnection;
    private IStreamService mService;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
    }

    /**
     * @return the {@link Context} the fragment is attached to
     */
    public Context getContext() {
        return mContext;
    }

    /**
     * Stores a {@link ServiceConnection} reference.
     */
    public void setServiceConnection(ServiceConnection connection) {
        mConnection = connection;
    }

    /**
     * @return the {@link ServiceConnection} previously stored by setServiceConnection()
     */
    public ServiceConnection getServiceConnection() {
        return mConnection;
    }

    /**
     * Stores a {@link IStreamService} reference.
     */
    public void setStreamService(IStreamService service) {
        mService = service;
    }

    /**
     * @return the {@link IStreamService} previously stored by setStreamService()
     */
    public IStreamService getStreamService() {
        return mService;
    }
}
