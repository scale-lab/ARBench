/*
 * Copyright 2022, Brown University, Providence, RI.
 * Rahul Shahi, Sherief Reda, Seif Abdelaziz
 *
 *                        All Rights Reserved
 *
 * Permission to use, copy, modify, and distribute this software and
 * its documentation for any purpose other than its incorporation into a
 * commercial product or service is hereby granted without fee, provided
 * that the above copyright notice appear in all copies and that both
 * that copyright notice and this permission notice appear in supporting
 * documentation, and that the name of Brown University not be used in
 * advertising or publicity pertaining to distribution of the software
 * without specific, written prior permission.
 *
 * BROWN UNIVERSITY DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE,
 * INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR ANY
 * PARTICULAR PURPOSE.  IN NO EVENT SHALL BROWN UNIVERSITY BE LIABLE FOR
 * ANY SPECIAL, INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.core.examples.java.geospatial;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.loader.content.CursorLoader;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Earth;
import com.google.ar.core.Frame;
import com.google.ar.core.GeospatialPose;
import com.google.ar.core.PlaybackStatus;
import com.google.ar.core.RecordingConfig;
import com.google.ar.core.RecordingStatus;
import com.google.ar.core.Session;
import com.google.ar.core.Track;
import com.google.ar.core.TrackData;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.LocationPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.common.helpers.TrackingStateHelper;
import com.google.ar.core.examples.java.common.samplerender.Framebuffer;
import com.google.ar.core.examples.java.common.samplerender.Mesh;
import com.google.ar.core.examples.java.common.samplerender.SampleRender;
import com.google.ar.core.examples.java.common.samplerender.Shader;
import com.google.ar.core.examples.java.common.samplerender.Texture;
import com.google.ar.core.examples.java.common.samplerender.arcore.BackgroundRenderer;
import com.google.ar.core.examples.java.geospatial.PrivacyNoticeDialogFragment.NoticeDialogListener;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.FineLocationPermissionNotGrantedException;
import com.google.ar.core.exceptions.GooglePlayServicesLocationLibraryNotLinkedException;
import com.google.ar.core.exceptions.PlaybackFailedException;
import com.google.ar.core.exceptions.RecordingFailedException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.google.ar.core.exceptions.UnsupportedConfigurationException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Main activity for the Geospatial API example.
 *
 * <p>This example shows how to use the Geospatial APIs. Once the device is localized, anchors can
 * be created at the device's geospatial location. Anchor locations are persisted across sessions
 * and will be recreated once localized.
 */
public class GeospatialActivity extends AppCompatActivity
        implements SampleRender.Renderer, NoticeDialogListener {

    private static final String TAG = GeospatialActivity.class.getSimpleName();

    private static final String SHARED_PREFERENCES_SAVED_ANCHORS = "SHARED_PREFERENCES_SAVED_ANCHORS";
    private static final String ALLOW_GEOSPATIAL_ACCESS_KEY = "ALLOW_GEOSPATIAL_ACCESS";

    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 1000f;

    // The thresholds that are required for horizontal and heading accuracies before entering into the
    // LOCALIZED state. Once the accuracies are equal or less than these values, the app will
    // allow the user to place anchors.
    private static final double LOCALIZING_HORIZONTAL_ACCURACY_THRESHOLD_METERS = 10;
    private static final double LOCALIZING_HEADING_ACCURACY_THRESHOLD_DEGREES = 15;

    // Once in the LOCALIZED state, if either accuracies degrade beyond these amounts, the app will
    // revert back to the LOCALIZING state.
    private static final double LOCALIZED_HORIZONTAL_ACCURACY_HYSTERESIS_METERS = 10;
    private static final double LOCALIZED_HEADING_ACCURACY_HYSTERESIS_DEGREES = 10;

    private static final int LOCALIZING_TIMEOUT_SECONDS = 180;
    private static final int MAXIMUM_ANCHORS = 10;

    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private GLSurfaceView surfaceView;

    private boolean installRequested;
    private Integer clearedAnchorsAmount = null;

    /**
     * Timer to keep track of how much time has passed since localizing has started.
     */
    private long localizingStartTimestamp;

    enum State {
        /**
         * The Geospatial API has not yet been initialized.
         */
        UNINITIALIZED,
        /**
         * The Geospatial API is not supported.
         */
        UNSUPPORTED,
        /**
         * The Geospatial API has encountered an unrecoverable error.
         */
        EARTH_STATE_ERROR,
        /**
         * The Session has started, but {@link Earth} isn't {@link TrackingState.TRACKING} yet.
         */
        PRETRACKING,
        /**
         * {@link Earth} is {@link TrackingState.TRACKING}, but the desired positioning confidence
         * hasn't been reached yet.
         */
        LOCALIZING,
        /**
         * The desired positioning confidence wasn't reached in time.
         */
        LOCALIZING_FAILED,
        /**
         * {@link Earth} is {@link TrackingState.TRACKING} and the desired positioning confidence has
         * been reached.
         */
        LOCALIZED
    }

    private State state = State.UNINITIALIZED;

    private Session session;
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private DisplayRotationHelper displayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
    private SampleRender render;
    private SharedPreferences sharedPreferences;

    private String lastStatusText;
    private TextView geospatialPoseTextView;
    private TextView statusTextView;
    private Button setAnchorButton;
    private Button clearAnchorsButton;
    private Button recordButton;
    private Button playbackButton;

    private BackgroundRenderer backgroundRenderer;
    private Framebuffer virtualSceneFramebuffer;
    private boolean hasSetTextureNames = false;

    // Virtual object (ARCore geospatial)
    private Mesh virtualObjectMesh;
    private Shader virtualObjectShader;

    private final List<Anchor> anchors = new ArrayList<>();

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private final float[] modelMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] modelViewMatrix = new float[16]; // view x model
    private final float[] modelViewProjectionMatrix = new float[16]; // projection x view x model

    private final String MP4_VIDEO_MIME_TYPE = "video/mp4";
    private final int REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    private int REQUEST_MP4_SELECTOR = 1;

    private final UUID TAP_TRACK_ID = UUID.fromString("7dee74ec-f283-11ec-b939-0242ac120002");
    private static final String TAP_TRACK_MIME_TYPE = "application/recording-playback-tap";

    public enum RecordingAppState {
        Idle,
        Recording,
        Playingback
    }

    private RecordingAppState recordingAppState = RecordingAppState.Idle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = getPreferences(Context.MODE_PRIVATE);

        setContentView(R.layout.activity_main);
        surfaceView = findViewById(R.id.surfaceview);
        geospatialPoseTextView = findViewById(R.id.geospatial_pose_view);
        statusTextView = findViewById(R.id.status_text_view);
        setAnchorButton = findViewById(R.id.set_anchor_button);
        clearAnchorsButton = findViewById(R.id.clear_anchors_button);
        recordButton = findViewById(R.id.record_button);
        playbackButton = findViewById(R.id.playback_button);

        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

        // Set up renderer.
        render = new SampleRender(surfaceView, this, getAssets());

        installRequested = false;
        clearedAnchorsAmount = null;
    }

    @Override
    protected void onDestroy() {
        if (session != null) {
            // Explicitly close ARCore Session to release native resources.
            // Review the API reference for important considerations before calling close() in apps with
            // more complicated lifecycle requirements:
            // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
            session.close();
            session = null;
        }

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (sharedPreferences.getBoolean(ALLOW_GEOSPATIAL_ACCESS_KEY, /*defValue=*/ false)) {
            createSession();
        } else {
            showPrivacyNoticeDialog();
        }

        surfaceView.onResume();
        displayRotationHelper.onResume();
    }

    private void showPrivacyNoticeDialog() {
        DialogFragment dialog = PrivacyNoticeDialogFragment.createDialog();
        dialog.show(getSupportFragmentManager(), PrivacyNoticeDialogFragment.class.getName());
    }

    private void createSession() {
        Exception exception = null;
        String message = null;
        if (session == null) {

            try {
                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }
                if (!LocationPermissionHelper.hasFineLocationPermission(this)) {
                    LocationPermissionHelper.requestFineLocationPermission(this);
                    return;
                }

                // Create the session.
                session = new Session(/* context= */ this);
            } catch (UnavailableArcoreNotInstalledException
                    | UnavailableUserDeclinedInstallationException e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            } catch (UnavailableDeviceNotCompatibleException e) {
                message = "This device does not support AR";
                exception = e;
            } catch (Exception e) {
                message = "Failed to create AR session";
                exception = e;
            }

            if (message != null) {
                messageSnackbarHelper.showError(this, message);
                Log.e(TAG, "Exception creating session", exception);
                return;
            }
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            configureSession();
            // To record a live camera session for later playback, call
            // `session.startRecording(recordingConfig)` at anytime. To playback a previously recorded AR
            // session instead of using the live camera feed, call
            // `session.setPlaybackDatasetUri(Uri)` before calling `session.resume()`. To
            // learn more about recording and playback, see:
            // https://developers.google.com/ar/develop/java/recording-and-playback
            session.resume();
        } catch (CameraNotAvailableException e) {
            message = "Camera not available. Try restarting the app.";
            exception = e;
        } catch (GooglePlayServicesLocationLibraryNotLinkedException e) {
            message = "Google Play Services location library not linked or obfuscated with Proguard.";
            exception = e;
        } catch (FineLocationPermissionNotGrantedException e) {
            message = "The Android permission ACCESS_FINE_LOCATION was not granted.";
            exception = e;
        } catch (UnsupportedConfigurationException e) {
            message = "This device does not support GeospatialMode.ENABLED.";
            exception = e;
        } catch (SecurityException e) {
            message = "Camera failure or the internet permission has not been granted.";
            exception = e;
        }

        if (message != null) {
            session = null;
            messageSnackbarHelper.showError(this, message);
            Log.e(TAG, "Exception configuring and resuming the session", exception);
            return;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            // Use toast instead of snackbar here since the activity will exit.
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
        // Check if this result pertains to the location permission.
        if (LocationPermissionHelper.hasFineLocationPermissionsResponseInResult(permissions)
                && !LocationPermissionHelper.hasFineLocationPermission(this)) {
            // Use toast instead of snackbar here since the activity will exit.
            Toast.makeText(
                            this,
                            "Precise location permission is needed to run this application",
                            Toast.LENGTH_LONG)
                    .show();
            if (!LocationPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                LocationPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    @Override
    public void onSurfaceCreated(SampleRender render) {
        // Prepare the rendering objects. This involves reading shaders and 3D model files, so may throw
        // an IOException.
        try {
            backgroundRenderer = new BackgroundRenderer(render);
            virtualSceneFramebuffer = new Framebuffer(render, /*width=*/ 1, /*height=*/ 1);

            // Virtual object to render (ARCore geospatial)
            Texture virtualObjectTexture =
                    Texture.createFromAsset(
                            render,
                            "models/spatial_marker_baked.png",
                            Texture.WrapMode.CLAMP_TO_EDGE,
                            Texture.ColorFormat.SRGB);

            virtualObjectMesh = Mesh.createFromAsset(render, "models/geospatial_marker.obj");
            virtualObjectShader =
                    Shader.createFromAssets(
                                    render,
                                    "shaders/ar_unlit_object.vert",
                                    "shaders/ar_unlit_object.frag",
                                    /*defines=*/ null)
                            .setTexture("u_Texture", virtualObjectTexture);

            backgroundRenderer.setUseDepthVisualization(render, false);
            backgroundRenderer.setUseOcclusion(render, false);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read a required asset file", e);
            messageSnackbarHelper.showError(this, "Failed to read a required asset file: " + e);
        }
    }

    @Override
    public void onSurfaceChanged(SampleRender render, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        virtualSceneFramebuffer.resize(width, height);
    }

    @Override
    public void onDrawFrame(SampleRender render) {
        if (session == null) {
            return;
        }

        // Texture names should only be set once on a GL thread unless they change. This is done during
        // onDrawFrame rather than onSurfaceCreated since the session is not guaranteed to have been
        // initialized during the execution of onSurfaceCreated.
        if (!hasSetTextureNames) {
            session.setCameraTextureNames(
                    new int[]{backgroundRenderer.getCameraColorTexture().getTextureId()});
            hasSetTextureNames = true;
        }

        // -- Update per-frame state

        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        displayRotationHelper.updateSessionIfNeeded(session);

        if (recordingAppState == RecordingAppState.Playingback
                && session.getPlaybackStatus() == PlaybackStatus.FINISHED
        ) {
            runOnUiThread(this::stopPlayingback);
            return;
        }

        // Obtain the current frame from ARSession. When the configuration is set to
        // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
        // camera framerate.
        Frame frame;
        try {
            frame = session.update();
        } catch (CameraNotAvailableException e) {
            Log.e(TAG, "Camera not available during onDrawFrame", e);
            messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
            return;
        }
        Camera camera = frame.getCamera();

        // BackgroundRenderer.updateDisplayGeometry must be called every frame to update the coordinates
        // used to draw the background camera image.
        backgroundRenderer.updateDisplayGeometry(frame);

        // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
        trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

        Earth earth = session.getEarth();
        if (earth != null) {
            updateGeospatialState(earth);
        }

        // Show a message based on whether tracking has failed, if planes are detected, and if the user
        // has placed any objects.
        String message = null;
        switch (state) {
            case UNINITIALIZED:
                break;
            case UNSUPPORTED:
                message = getResources().getString(R.string.status_unsupported);
                break;
            case PRETRACKING:
                message = getResources().getString(R.string.status_pretracking);
                break;
            case EARTH_STATE_ERROR:
                message = getResources().getString(R.string.status_earth_state_error);
                break;
            case LOCALIZING:
                message = getResources().getString(R.string.status_localize_hint);
                break;
            case LOCALIZING_FAILED:
                message = getResources().getString(R.string.status_localize_timeout);
                break;
            case LOCALIZED:
                if (anchors.size() > 0) {
                    message =
                            getResources()
                                    .getQuantityString(R.plurals.status_anchors_set, anchors.size(), anchors.size());

                } else if (clearedAnchorsAmount != null) {
                    message =
                            getResources()
                                    .getQuantityString(
                                            R.plurals.status_anchors_cleared, clearedAnchorsAmount, clearedAnchorsAmount);
                } else {
                    message = getResources().getString(R.string.status_localize_complete);
                }
                break;
        }
        if (message == null) {
            lastStatusText = null;
            runOnUiThread(() -> statusTextView.setVisibility(View.INVISIBLE));
        } else if (lastStatusText != message) {
            lastStatusText = message;
            runOnUiThread(
                    () -> {
                        statusTextView.setVisibility(View.VISIBLE);
                        statusTextView.setText(lastStatusText);
                    });
        }

        // -- Draw background

        if (frame.getTimestamp() != 0) {
            // Suppress rendering if the camera did not produce the first frame yet. This is to avoid
            // drawing possible leftover data from previous sessions if the texture is reused.
            backgroundRenderer.drawBackground(render);
        }

        // If not tracking, don't draw 3D objects.
        if (camera.getTrackingState() != TrackingState.TRACKING || state != State.LOCALIZED) {
            return;
        }

        // -- Draw virtual objects

        // Get projection matrix.
        camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR);

        // Get camera matrix and draw.
        camera.getViewMatrix(viewMatrix, 0);

        if (setAnchorButton.isPressed() && earth != null && earth.getTrackingState() == TrackingState.TRACKING) {
            GeospatialPose geospatialPose = earth.getCameraGeospatialPose();
            double latitude = geospatialPose.getLatitude();
            double longitude = geospatialPose.getLongitude();
            double verticalAccuracy = geospatialPose.getVerticalAccuracy();
            double horizontalAccuracy = geospatialPose.getHorizontalAccuracy();
            double altitude = geospatialPose.getAltitude();
            double headingDegrees = geospatialPose.getHeading();
            double headingAccuracy = geospatialPose.getHeadingAccuracy();

            if (session.getPlaybackStatus() == PlaybackStatus.OK) {
                Collection<TrackData> trackDataList = frame.getUpdatedTrackData(TAP_TRACK_ID);

                for (TrackData trackData : frame.getUpdatedTrackData(TAP_TRACK_ID)) {
                    ByteBuffer payload = trackData.getData();
                    FloatBuffer floatBuffer = payload.asFloatBuffer();
                    float[] geospatialPoseData = new float[7];
                    floatBuffer.get(geospatialPoseData);
                    latitude = geospatialPoseData[0];
                    longitude = geospatialPoseData[1];
                    verticalAccuracy = geospatialPoseData[2];
                    horizontalAccuracy = geospatialPoseData[3];
                    altitude = geospatialPoseData[4];
                    headingDegrees = geospatialPoseData[5];
                    headingAccuracy = geospatialPoseData[6];
                    break;
                }

            } else if (session.getRecordingStatus() == RecordingStatus.OK) {
               latitude = geospatialPose.getLatitude();
               longitude = geospatialPose.getLongitude();
               verticalAccuracy = geospatialPose.getVerticalAccuracy();
               horizontalAccuracy = geospatialPose.getHorizontalAccuracy();
               altitude = geospatialPose.getAltitude();
               headingDegrees = geospatialPose.getHeading();
               headingAccuracy = geospatialPose.getHeadingAccuracy();

                float[] geospatialPoseData = new float[7];
                geospatialPoseData[0] = (float) latitude;
                geospatialPoseData[1] = (float) longitude;
                geospatialPoseData[2] = (float) verticalAccuracy;
                geospatialPoseData[3] = (float) horizontalAccuracy;
                geospatialPoseData[4] = (float) altitude;
                geospatialPoseData[5] = (float) headingDegrees;
                geospatialPoseData[6] = (float) headingAccuracy;
                ByteBuffer payload = ByteBuffer.allocate(4 * 6);
                FloatBuffer floatBuffer = payload.asFloatBuffer();
                floatBuffer.put(geospatialPoseData);

                System.out.println("RECORDING DATA: " + Arrays.toString(geospatialPoseData));

                try {
                    frame.recordTrackData(TAP_TRACK_ID, payload);
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Error in recording tap input into external data track.", e);
                }
            }

            createAnchor(earth, latitude, longitude, altitude, headingDegrees);
            storeAnchorParameters(latitude, longitude, altitude, headingDegrees);
            runOnUiThread(() -> clearAnchorsButton.setVisibility(View.VISIBLE));
            if (clearedAnchorsAmount != null) {
                clearedAnchorsAmount = null;
            }
        }

        // Visualize anchors created by touch.
        render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f);
        for (Anchor anchor : anchors) {
            // Get the current pose of an Anchor in world space. The Anchor pose is updated
            // during calls to session.update() as ARCore refines its estimate of the world.
            anchor.getPose().toMatrix(modelMatrix, 0);

            // Calculate model/view/projection matrices
            Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);

            // Update shader properties and draw
            virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);

            render.draw(virtualObjectMesh, virtualObjectShader, virtualSceneFramebuffer);
        }

        // Compose the virtual scene with the background.
        backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR);
    }


    private void updateRecordButton() {
        switch (recordingAppState) {

            // The app is neither recording nor playing back. The "Record" button is visible.
            case Idle:
                if (state == State.LOCALIZED) {
                    recordButton.setText("Record");
                    recordButton.setVisibility(View.VISIBLE);
                } else {
                    recordButton.setText("Record");
                    recordButton.setVisibility(View.INVISIBLE);
                }
                break;

            // While recording, the "Record" button is visible and says "Stop".
            case Recording:
                recordButton.setText("Stop");
                recordButton.setVisibility(View.VISIBLE);
                break;

            // During playback, the "Record" button is not visible.
            case Playingback:
                recordButton.setVisibility(View.INVISIBLE);
                break;
        }
    }

    private void updateAnchorButtons() {
        switch (recordingAppState) {

            // The app is neither recording nor playing back. The "Record" button is visible.
            case Idle:

                // While recording, the "Record" button is visible and says "Stop".
            case Recording:
                if (state == State.LOCALIZED) {
                    setAnchorButton.setVisibility(View.VISIBLE);
                    clearAnchorsButton.setVisibility(View.VISIBLE);
                } else {
                    setAnchorButton.setVisibility(View.INVISIBLE);
                    clearAnchorsButton.setVisibility(View.INVISIBLE);
                }
                break;

            // During playback, the "Record" button is not visible.
            case Playingback:
                setAnchorButton.setVisibility(View.INVISIBLE);
                clearAnchorsButton.setVisibility(View.INVISIBLE);
                break;
        }
    }

    public void onClickRecord(View view) {
        Log.d(TAG, "onClickRecord");

        // Check the app's internal state and switch to the new state if needed.
        switch (recordingAppState) {
            // If the app is not recording, begin recording.
            case Idle: {
                boolean hasStarted = startRecording();
                Log.d(TAG, String.format("onClickRecord start: hasStarted %b", hasStarted));

                if (hasStarted)
                    recordingAppState = RecordingAppState.Recording;

                break;
            }

            // If the app is recording, stop recording.
            case Recording: {
                boolean hasStopped = stopRecording();
                Log.d(TAG, String.format("onClickRecord stop: hasStopped %b", hasStopped));

                if (hasStopped)
                    recordingAppState = RecordingAppState.Idle;

                break;
            }

            default:
                // Do nothing.
                break;
        }

        updateRecordButton();
        updatePlaybackButton();
        updateAnchorButtons();
    }

    public void onClickPlayback(View view) {
        Log.d(TAG, "onClickPlayback");

        switch (recordingAppState) {

            // If the app is not playing back, open the file picker.
            case Idle: {
                boolean hasStarted = selectFileToPlayback();
                Log.d(TAG, String.format("onClickPlayback start: selectFileToPlayback %b", hasStarted));
                break;
            }

            // If the app is playing back, stop playing back.
            case Playingback: {
                boolean hasStopped = stopPlayingback();
                Log.d(TAG, String.format("onClickPlayback stop: hasStopped %b", hasStopped));
                break;
            }

            default:
                // Recording - do nothing.
                break;
        }

        // Update the UI for the "Record" and "Playback" buttons.
        updateRecordButton();
        updatePlaybackButton();
        updateAnchorButtons();
    }

    private boolean selectFileToPlayback() {
        // Start file selection from Movies directory.
        // Android 10 and above requires VOLUME_EXTERNAL_PRIMARY to write to MediaStore.
        Uri videoCollection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            videoCollection = MediaStore.Video.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            videoCollection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        }

        // Create an Intent to select a file.
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        // Add file filters such as the MIME type, the default directory and the file category.
        intent.setType(MP4_VIDEO_MIME_TYPE); // Only select *.mp4 files
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, videoCollection); // Set default directory
        intent.addCategory(Intent.CATEGORY_OPENABLE); // Must be files that can be opened

        this.startActivityForResult(intent, REQUEST_MP4_SELECTOR);

        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check request status. Log an error if the selection fails.
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || requestCode != REQUEST_MP4_SELECTOR) {
            Log.e(TAG, "onActivityResult select file failed");
            return;
        }

        Uri mp4Uri = data.getData();
        Log.d(TAG, String.format("onActivityResult result is %s", mp4Uri));

        // Copy to app internal storage to get a file path.
        String localFilePath = copyToInternalFilePath(mp4Uri);

        // Begin playback.
        startPlayingback(localFilePath);
    }

    private String copyToInternalFilePath(Uri contentUri) {
        // Create a file path in the app's internal storage.
        String tempPlaybackFilePath = new File(this.getExternalFilesDir(null), "temp-playback.mp4").getAbsolutePath();

        // Copy the binary content from contentUri to tempPlaybackFilePath.
        try (InputStream inputStream = this.getContentResolver().openInputStream(contentUri);
             java.io.OutputStream tempOutputFileStream = new java.io.FileOutputStream(tempPlaybackFilePath)) {

            byte[] buffer = new byte[1024 * 1024]; // 1MB
            int bytesRead = inputStream.read(buffer);
            while (bytesRead != -1) {
                tempOutputFileStream.write(buffer, 0, bytesRead);
                bytesRead = inputStream.read(buffer);
            }

        } catch (java.io.FileNotFoundException e) {
            Log.e(TAG, "copyToInternalFilePath FileNotFoundException", e);
            return null;
        } catch (IOException e) {
            Log.e(TAG, "copyToInternalFilePath IOException", e);
            return null;
        }

        // Return the absolute file path of the copied file.
        return tempPlaybackFilePath;
    }

    private boolean startPlayingback(String mp4FilePath) {
        if (mp4FilePath == null)
            return false;

        Log.d(TAG, "startPlayingback at:" + mp4FilePath);

        pauseARCoreSession();

        try {
            session.setPlaybackDataset(mp4FilePath);
        } catch (PlaybackFailedException e) {
            Log.e(TAG, "startPlayingback - setPlaybackDataset failed", e);
        }

        // The session's camera texture name becomes invalid when the
        // ARCore session is set to play back.
        // Workaround: Reset the Texture to start Playback
        // so it doesn't crashes with AR_ERROR_TEXTURE_NOT_SET.
        hasSetTextureNames = false;

        boolean canResume = resumeARCoreSession();
        if (!canResume)
            return false;

        PlaybackStatus playbackStatus = session.getPlaybackStatus();
        Log.d(TAG, String.format("startPlayingback - playbackStatus %s", playbackStatus));


        if (playbackStatus != PlaybackStatus.OK) { // Correctness check
            return false;
        }

        recordingAppState = RecordingAppState.Playingback;
        updateRecordButton();
        updatePlaybackButton();
        updateAnchorButtons();
        return true;
    }

    private boolean stopPlayingback() {
        // Correctness check, only stop playing back when the app is playing back.
        if (recordingAppState != recordingAppState.Playingback)
            return false;

        pauseARCoreSession();

        // Close the current session and create a new session.
        session.close();
        try {
            session = new Session(this);
        } catch (UnavailableArcoreNotInstalledException
                | UnavailableApkTooOldException
                | UnavailableSdkTooOldException
                | UnavailableDeviceNotCompatibleException e) {
            Log.e(TAG, "Error in return to Idle state. Cannot create new ARCore session", e);
            return false;
        }
        configureSession();

        boolean canResume = resumeARCoreSession();
        if (!canResume)
            return false;

        // A new session will not have a camera texture name.
        // Manually set hasSetTextureNames to false to trigger a reset.
        hasSetTextureNames = false;

        // Reset appState to Idle, and update the "Record" and "Playback" buttons.
        recordingAppState = RecordingAppState.Idle;
        updateRecordButton();
        updatePlaybackButton();
        updateAnchorButtons();
        return true;
    }

    private void updatePlaybackButton() {
        switch (recordingAppState) {
            // The app is neither recording nor playing back. The "Playback" button is visible.
            case Idle:
                playbackButton.setText("Playback");
                playbackButton.setVisibility(View.VISIBLE);
                break;

            // While playing back, the "Playback" button is visible and says "Stop".
            case Playingback:
                playbackButton.setText("Stop");
                playbackButton.setVisibility(View.VISIBLE);
                break;

            // During recording, the "Playback" button is not visible.
            case Recording:
                playbackButton.setVisibility(View.INVISIBLE);
                break;
        }
    }

    private boolean startRecording() {
        String mp4FilePath = createMp4File();
        if (mp4FilePath == null)
            return false;

        Log.d(TAG, "startRecording at: " + mp4FilePath);

        pauseARCoreSession();

        Track tapTrack = new Track(session)
                .setId(TAP_TRACK_ID)
                .setMimeType(TAP_TRACK_MIME_TYPE);

        // Configure the ARCore session to start recording.
        RecordingConfig recordingConfig = new RecordingConfig(session)
                .setMp4DatasetFilePath(mp4FilePath)
                .setAutoStopOnPause(true)
                .addTrack(tapTrack);

        try {
            // Prepare the session for recording, but do not start recording yet.
            session.startRecording(recordingConfig);
        } catch (RecordingFailedException e) {
            Log.e(TAG, "startRecording - Failed to prepare to start recording", e);
            return false;
        }

        boolean canResume = resumeARCoreSession();
        if (!canResume)
            return false;

        // Correctness checking: check the ARCore session's RecordingState.
        RecordingStatus recordingStatus = session.getRecordingStatus();
        Log.d(TAG, String.format("startRecording - recordingStatus %s", recordingStatus));
        return recordingStatus == RecordingStatus.OK;
    }

    private void pauseARCoreSession() {
        // Pause the GLSurfaceView so that it doesn't update the ARCore session.
        // Pause the ARCore session so that we can update its configuration.
        // If the GLSurfaceView is not paused,
        //   onDrawFrame() will try to update the ARCore session
        //   while it's paused, resulting in a crash.
        surfaceView.onPause();
        session.pause();
    }

    private boolean resumeARCoreSession() {
        // We must resume the ARCore session before the GLSurfaceView.
        // Otherwise, the GLSurfaceView will try to update the ARCore session.
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            Log.e(TAG, "CameraNotAvailableException in resumeARCoreSession", e);
            return false;
        }

        surfaceView.onResume();
        return true;
    }

    private boolean stopRecording() {
        try {
            session.stopRecording();
        } catch (RecordingFailedException e) {
            Log.e(TAG, "stopRecording - Failed to stop recording", e);
            return false;
        }

        // Correctness checking: check if the session stopped recording.
        return session.getRecordingStatus() == RecordingStatus.NONE;
    }

    private String createMp4File() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (!checkAndRequestStoragePermission()) {
                Log.i(TAG, String.format(
                        "Didn't createMp4File. No storage permission, API Level = %d",
                        Build.VERSION.SDK_INT));
                return null;
            }
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String mp4FileName = "arcore-" + dateFormat.format(new Date()) + ".mp4";

        ContentResolver resolver = this.getContentResolver();

        Uri videoCollection = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            videoCollection = MediaStore.Video.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            videoCollection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        }

        // Create a new Media file record.
        ContentValues newMp4FileDetails = new ContentValues();
        newMp4FileDetails.put(MediaStore.Video.Media.DISPLAY_NAME, mp4FileName);
        newMp4FileDetails.put(MediaStore.Video.Media.MIME_TYPE, MP4_VIDEO_MIME_TYPE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // The Relative_Path column is only available since API Level 29.
            newMp4FileDetails.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES);
        } else {
            // Use the Data column to set path for API Level <= 28.
            File mp4FileDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
            String absoluteMp4FilePath = new File(mp4FileDir, mp4FileName).getAbsolutePath();
            newMp4FileDetails.put(MediaStore.Video.Media.DATA, absoluteMp4FilePath);
        }

        Uri newMp4FileUri = resolver.insert(videoCollection, newMp4FileDetails);

        // Ensure that this file exists and can be written.
        if (newMp4FileUri == null) {
            Log.e(TAG, String.format("Failed to insert Video entity in MediaStore. API Level = %d", Build.VERSION.SDK_INT));
            return null;
        }

        // This call ensures the file exist before we pass it to the ARCore API.
        if (!testFileWriteAccess(newMp4FileUri)) {
            return null;
        }

        String filePath = getMediaFilePath(newMp4FileUri);
        Log.d(TAG, String.format("createMp4File = %s, API Level = %d", filePath, Build.VERSION.SDK_INT));

        return filePath;
    }

    // Test if the file represented by the content Uri can be open with write access.
    private boolean testFileWriteAccess(Uri contentUri) {
        try (java.io.OutputStream mp4File = this.getContentResolver().openOutputStream(contentUri)) {
            Log.d(TAG, String.format("Success in testFileWriteAccess %s", contentUri.toString()));
            return true;
        } catch (java.io.FileNotFoundException e) {
            Log.e(TAG, String.format("FileNotFoundException in testFileWriteAccess %s", contentUri.toString()), e);
        } catch (java.io.IOException e) {
            Log.e(TAG, String.format("IOException in testFileWriteAccess %s", contentUri.toString()), e);
        }

        return false;
    }

    // Query the Media.DATA column to get file path from MediaStore content:// Uri
    private String getMediaFilePath(Uri mediaStoreUri) {
        String[] projection = {MediaStore.Images.Media.DATA};

        CursorLoader loader = new CursorLoader(this, mediaStoreUri, projection, null, null, null);
        Cursor cursor = loader.loadInBackground();
        cursor.moveToFirst();

        int data_column_index = cursor.getColumnIndexOrThrow(projection[0]);
        String data_result = cursor.getString(data_column_index);

        cursor.close();

        return data_result;
    }

    public boolean checkAndRequestStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_EXTERNAL_STORAGE);
            return false;
        }

        return true;
    }

    /**
     * Configures the session with feature settings.
     */
    private void configureSession() {
        // Earth mode may not be supported on this device due to insufficient sensor quality.
        if (!session.isGeospatialModeSupported(Config.GeospatialMode.ENABLED)) {
            state = State.UNSUPPORTED;
            return;
        }

        Config config = session.getConfig();
        config.setGeospatialMode(Config.GeospatialMode.ENABLED);
        session.configure(config);
        state = State.PRETRACKING;
        localizingStartTimestamp = System.currentTimeMillis();
    }

    /**
     * Change behavior depending on the current {@link State} of the application.
     */
    private void updateGeospatialState(Earth earth) {
        if (state == State.PRETRACKING) {
            updatePretrackingState(earth);
        } else if (state == State.LOCALIZING) {
            updateLocalizingState(earth);
        } else if (state == State.LOCALIZED) {
            updateLocalizedState(earth);
        }
    }

    /**
     * Handles the updating for {@link State.PRETRACKING}. In this state, wait for {@link Earth} to
     * have {@link TrackingState.TRACKING}. If it hasn't been enabled by now, then we've encountered
     * an unrecoverable {@link State.EARTH_STATE_ERROR}.
     */
    private void updatePretrackingState(Earth earth) {
        if (earth.getTrackingState() == TrackingState.TRACKING) {
            state = State.LOCALIZING;
            return;
        }

        if (earth.getEarthState() != Earth.EarthState.ENABLED) {
            state = State.EARTH_STATE_ERROR;
            return;
        }

        runOnUiThread(() -> geospatialPoseTextView.setText(R.string.geospatial_pose_not_tracking));
    }

    /**
     * Handles the updating for {@link State.LOCALIZING}. In this state, wait for the horizontal and
     * heading threshold to improve until it reaches your threshold.
     *
     * <p>If it takes too long for the threshold to be reached, this could mean that GPS data isn't
     * accurate enough, or that the user is in an area that can't be localized with StreetView.
     */
    private void updateLocalizingState(Earth earth) {
        GeospatialPose geospatialPose = earth.getCameraGeospatialPose();
        if (geospatialPose.getHorizontalAccuracy() <= LOCALIZING_HORIZONTAL_ACCURACY_THRESHOLD_METERS
                && geospatialPose.getHeadingAccuracy() <= LOCALIZING_HEADING_ACCURACY_THRESHOLD_DEGREES) {
            state = State.LOCALIZED;
            if (anchors.isEmpty()) {
                createAnchorFromSharedPreferences(earth);
            }
            runOnUiThread(
                    () -> {
                        setAnchorButton.setVisibility(View.VISIBLE);
                        updateRecordButton();
                        updatePlaybackButton();
                    });
            return;
        }

        if (TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - localizingStartTimestamp)
                > LOCALIZING_TIMEOUT_SECONDS) {
            state = State.LOCALIZING_FAILED;
            return;
        }

        updateGeospatialPoseText(geospatialPose);
    }

    /**
     * Handles the updating for {@link State.LOCALIZED}. In this state, check the accuracy for
     * degradation and return to {@link State.LOCALIZING} if the position accuracies have dropped too
     * low.
     */
    private void updateLocalizedState(Earth earth) {
        GeospatialPose geospatialPose = earth.getCameraGeospatialPose();
        // Check if either accuracy has degraded to the point we should enter back into the LOCALIZING
        // state.
        if (geospatialPose.getHorizontalAccuracy()
                > LOCALIZING_HORIZONTAL_ACCURACY_THRESHOLD_METERS
                + LOCALIZED_HORIZONTAL_ACCURACY_HYSTERESIS_METERS
                || geospatialPose.getHeadingAccuracy()
                > LOCALIZING_HEADING_ACCURACY_THRESHOLD_DEGREES
                + LOCALIZED_HEADING_ACCURACY_HYSTERESIS_DEGREES) {
            // Accuracies have degenerated, return to the localizing state.
            state = State.LOCALIZING;
            localizingStartTimestamp = System.currentTimeMillis();
            runOnUiThread(
                    () -> {
                        setAnchorButton.setVisibility(View.INVISIBLE);
                        clearAnchorsButton.setVisibility(View.INVISIBLE);
                        recordButton.setVisibility(View.INVISIBLE);
                        playbackButton.setVisibility(View.INVISIBLE);
                    });
            return;
        }

        updateGeospatialPoseText(geospatialPose);
    }

    private void updateGeospatialPoseText(GeospatialPose geospatialPose) {
        String poseText =
                getResources()
                        .getString(
                                R.string.geospatial_pose,
                                geospatialPose.getLatitude(),
                                geospatialPose.getLongitude(),
                                geospatialPose.getHorizontalAccuracy(),
                                geospatialPose.getAltitude(),
                                geospatialPose.getVerticalAccuracy(),
                                geospatialPose.getHeading(),
                                geospatialPose.getHeadingAccuracy());
        runOnUiThread(
                () -> {
                    geospatialPoseTextView.setText(poseText);
                });
    }

    /**
     * Handles the button that creates an anchor.
     *
     * <p>Ensure Earth is in the proper state, then create the anchor. Persist the parameters used to
     * create the anchors so that the anchors will be loaded next time the app is launched.
     */
    private void handleSetAnchorButton() {
        Earth earth = session.getEarth();
        if (earth == null || earth.getTrackingState() != TrackingState.TRACKING) {
            return;
        }

        GeospatialPose geospatialPose = earth.getCameraGeospatialPose();
        double latitude = geospatialPose.getLatitude();
        double longitude = geospatialPose.getLongitude();
        double altitude = geospatialPose.getAltitude();
        double headingDegrees = geospatialPose.getHeading();

        createAnchor(earth, latitude, longitude, altitude, headingDegrees);
        storeAnchorParameters(latitude, longitude, altitude, headingDegrees);
        runOnUiThread(() -> clearAnchorsButton.setVisibility(View.VISIBLE));
        if (clearedAnchorsAmount != null) {
            clearedAnchorsAmount = null;
        }
    }

    private void handleClearAnchorsButton() {
        clearedAnchorsAmount = anchors.size();
        anchors.clear();
        clearAnchorsFromSharedPreferences();
        clearAnchorsButton.setVisibility(View.INVISIBLE);
    }

    /**
     * Create an anchor at a specific geodetic location using a heading.
     */
    private void createAnchor(
            Earth earth, double latitude, double longitude, double altitude, double headingDegrees) {
        // Convert a heading to a EUS quaternion:
        double angleRadians = Math.toRadians(180.0f - headingDegrees);
        Anchor anchor =
                earth.createAnchor(
                        latitude,
                        longitude,
                        altitude,
                        0.0f,
                        (float) Math.sin(angleRadians / 2),
                        0.0f,
                        (float) Math.cos(angleRadians / 2));
        anchors.add(anchor);
        if (anchors.size() > MAXIMUM_ANCHORS) {
            anchors.remove(0);
        }
    }

    /**
     * Helper function to store the parameters used in anchor creation in {@link SharedPreferences}.
     */
    private void storeAnchorParameters(
            double latitude, double longitude, double altitude, double headingDegrees) {
        Set<String> anchorParameterSet =
                sharedPreferences.getStringSet(SHARED_PREFERENCES_SAVED_ANCHORS, new HashSet<>());
        HashSet<String> newAnchorParameterSet = new HashSet<>(anchorParameterSet);

        SharedPreferences.Editor editor = sharedPreferences.edit();
        newAnchorParameterSet.add(
                String.format("%.6f,%.6f,%.6f,%.6f", latitude, longitude, altitude, headingDegrees));
        editor.putStringSet(SHARED_PREFERENCES_SAVED_ANCHORS, newAnchorParameterSet);
        editor.commit();
    }

    private void clearAnchorsFromSharedPreferences() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putStringSet(SHARED_PREFERENCES_SAVED_ANCHORS, null);
        editor.commit();
    }

    /**
     * Creates all anchors that were stored in the {@link SharedPreferences}.
     */
    private void createAnchorFromSharedPreferences(Earth earth) {
        Set<String> anchorParameterSet =
                sharedPreferences.getStringSet(SHARED_PREFERENCES_SAVED_ANCHORS, null);
        if (anchorParameterSet == null) {
            return;
        }

        for (String anchorParameters : anchorParameterSet) {
            String[] parameters = anchorParameters.split(",");
            if (parameters.length != 4) {
                Log.d(
                        TAG, "Invalid number of anchor parameters. Expected four, found " + parameters.length);
                return;
            }
            double latitude = Double.parseDouble(parameters[0]);
            double longitude = Double.parseDouble(parameters[1]);
            double altitude = Double.parseDouble(parameters[2]);
            double heading = Double.parseDouble(parameters[3]);
            createAnchor(earth, latitude, longitude, altitude, heading);
        }

        runOnUiThread(() -> clearAnchorsButton.setVisibility(View.VISIBLE));
    }

    @Override
    public void onDialogPositiveClick(DialogFragment dialog) {
        if (!sharedPreferences.edit().putBoolean(ALLOW_GEOSPATIAL_ACCESS_KEY, true).commit()) {
            throw new AssertionError("Could not save the user preference to SharedPreferences!");
        }
        createSession();
    }
}
