package com.spynet.camera.services;

import com.spynet.camera.media.CameraInfo;
import com.spynet.camera.services.IStreamServiceCallBack;

/**
 * Interface defining the methods to communicate with the StreamService.
 */
interface IStreamService {
    /**
     * Registers the callback used by the StreamService to communicate with the client.
     */
    oneway void registerCallBack(in IStreamServiceCallBack cb);

    /**
     * Restarts a service functionality.
     *
     * @param what what to restart
     */
    void restart(in String what);

    /**
     * @return the number of available cameras
     */
    int getNumberOfCameras();

    /**
     * Returns the information of one camera.
     *
     * @param index the index of the information to be returned,
     *              from 0 to {@code getNumberOfCameras()} - 1
     * @return the information of the camera referred by {@code cameraId}
     */
    CameraInfo getCameraInfo(int index);

    /**
     * Sets the surface where to stream the video.
     *
     * @param surface the surface where to stream the video, null to render offscreen
     */
    void setSurface(in Surface surface);

    /**
     * @return whether the screen capture camera is used
     */
    boolean isScreenCaptureCamera();

    /**
     * @return whether a front camera is used
     */
    boolean isFrontCamera();

    /**
     * @return the frame size in pixels
     */
    Point getFrameSize();

    /**
     * @return the average frame rate in frames per second
     */
    float getAverageFrameRate();

    /**
     * Sets the camera zoom.
     *
     * @param zoom the zoom factor
     */
    void setZoom(float zoom);

    /**
     * @return the camera zoom
     */
    float getZoom();

    /**
     * Starts camera auto-focus.
     *
     * @param x the horizontal coordinate of the focus area, in the range -1000 to 1000.
     * @param y the vertical coordinate of the focus area, in the range -1000 to 1000.
     */
    void autoFocus(int x, int y);

    /**
     * @return the video bitrate in bps
     */
    int getVideoBitrate();

    /**
     * @return the audio bitrate in bps
     */
    int getAudioBitrate();

    /**
     * @return the number of active streams
     */
    int getNumberOfStreams();

    /**
     * @return the number of active audio streams
     */
    int getNumberOfAudioStreams();

    /**
     * Sets the audio gain.
     *
     * @param audio gain in dB
     */
    void setGain(double gain);

    /**
     * Sets the audio mute state.
     *
     * @param muet the mute state
     */
    void setMute(boolean mute);

    /**
     * @return the audio mute state
     */
    boolean getMute();

    /**
     * @return whether the H264 stream is available
     */
    boolean isH264Available();

   /**
     * @return whether the MJPEG stream is available
     */
    boolean isMJPEGAvailable();

   /**
     * @return whether the screen capture is available
     */
    boolean isScreenCaptureAvailable();

    /**
     * @return whether the MangocamAdapter is connected
     */
    boolean isMangocamConnected();

    /**
     * @return whether the AngelcamAdapter is connected
     */
    boolean isAngelcamConnected();
}
