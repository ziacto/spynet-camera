package com.spynet.camera.services;

/**
 * Interface defining the methods that the StreamService uses to communicate with the client.
 */
interface IStreamServiceCallBack {
    /**
     * Notifies that the streaming has started.
     */
    oneway void onStreamingStarted();

    /**
     * Notifies that the streaming has stopped.
     */
    oneway void onStreamingStopped();

    /**
     * Notifies that the audio streaming has started.
     */
    oneway void onAudioStarted();

    /**
     * Notifies that the audio streaming has stopped.
     */
    oneway void onAudioStopped();

    /**
     * Notifies that an adapter has connected to its server.
     *
     * @param adapter the adapter name
     */
    oneway void onAdapterConnected(in String adapter);

    /**
     * Notifies that an adapter has disconnected from the server.
     *
     * @param adapter the adapter name
     */
    oneway void onAdapterDisconnected(in String adapter);

    /**
     * Notifies that an action has been requested by a client.
     *
     * @param code    the log code
     * @param message the log message
     */
    oneway void onLog(int code, in String message);

    /**
     * Notifies that the frame size changed.
     *
     * @param size the frame size in pixels
     */
    oneway void onFrameSizeChanged(in Point size);

    /**
     * Notifies that the bitrate changed.
     *
     * @param video the video bitrate
     * @param audio the audio bitrate
     */
    oneway void onBitrateChanged(int video, int audio);

    /**
     * Notifies that a new zoom has been set.
     *
     * @param zoom the new zoom factor
     */
    oneway void onZoom(float zoom);

    /**
     * Called when the torch state changes.
     *
     * @param state {@code true} if the torch is on, {@code false} if it is off
     */
    oneway void onTorch(boolean state);

    /**
     * Notifies that a new mute state has been set.
     *
     * @param mute the new mute state
     */
    oneway void onMute(boolean mute);

    /**
     * Notifies that a new frame rate value is available.
     *
     * @param fps the new frame rate value
     */
    oneway void onFrameRate(float fps);

    /**
     * Called when the screen capture has been denied or authorized by the user.
     *
     * @param authorized whether the screen capture has been authorized by the user
     */
    oneway void onScreenCapture(boolean authorized);
}
