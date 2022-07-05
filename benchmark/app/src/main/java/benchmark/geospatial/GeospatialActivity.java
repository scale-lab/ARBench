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

package benchmark.geospatial;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
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
import com.google.ar.core.TrackData;
import com.google.ar.core.TrackingState;
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
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

import benchmark.augmented_faces.AugmentedFacesActivity;
import benchmark.benchmark.BenchmarkActivity;
import benchmark.benchmark.R;
import benchmark.common.helpers.CameraPermissionHelper;
import benchmark.common.helpers.DisplayRotationHelper;
import benchmark.common.helpers.FullScreenHelper;
import benchmark.common.helpers.LocationPermissionHelper;
import benchmark.common.helpers.SnackbarHelper;
import benchmark.common.helpers.TrackingStateHelper;
import benchmark.common.samplerender.Framebuffer;
import benchmark.common.samplerender.GLError;
import benchmark.common.samplerender.Mesh;
import benchmark.common.samplerender.OffscreenRender;
import benchmark.common.samplerender.SampleRender;
import benchmark.common.samplerender.Shader;
import benchmark.common.samplerender.Texture;
import benchmark.common.samplerender.arcore.BackgroundRenderer;

/**
 * Main activity for the Geospatial API example.
 *
 * <p>This example shows how to use the Geospatial APIs. Once the device is localized, anchors can
 * be created at the device's geospatial location. Anchor locations are persisted across sessions
 * and will be recreated once localized.
 */
public class GeospatialActivity extends AppCompatActivity
        implements SampleRender.Renderer, PrivacyNoticeDialogFragment.NoticeDialogListener {

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
    private SurfaceView surfaceView;

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
        PRETRACKING,
        LOCALIZING,
        /**
         * The desired positioning confidence wasn't reached in time.
         */
        LOCALIZING_FAILED,
        LOCALIZED
    }

    private State state = State.UNINITIALIZED;

    private Session session;
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private DisplayRotationHelper displayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
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

    private BufferedWriter fpsLog;
    String fileName;
    private int currentPhase = 1;

    private boolean hasTimerExtension;
    private static final int TIME_ELAPSED_EXT = 0x88BF;
    private static final int NUM_QUERIES = 10;
    private int[] timeQueries;
    private int[] queryBuffer;
    private int queryIndex;
    private OffscreenRender render;

    private final UUID TAP_TRACK_ID = UUID.fromString("7dee74ec-f283-11ec-b939-0242ac120002");

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

        setContentView(R.layout.geospatial);
        surfaceView = new SurfaceView(this);
        geospatialPoseTextView = findViewById(R.id.geospatial_pose_view);

        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

        // Set up renderer.
        // Offscreen
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                render = new OffscreenRender(surfaceView, GeospatialActivity.this, getAssets());
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                render.stop();
            }
        });
        RelativeLayout mainLayout =  findViewById(R.id.layout_main);
        mainLayout.addView(surfaceView);

        // Onscreen
//        surfaceView.setPreserveEGLContextOnPause(true);
//        surfaceView.setEGLContextClientVersion(2);
//        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
//        surfaceView.setRenderer(this);
//        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
//        surfaceView.setWillNotDraw(false);

        installRequested = false;

        Intent intent = getIntent();
        int activityNumber = intent.getIntExtra(BenchmarkActivity.ACTIVITY_NUMBER, 0);
        fileName = BenchmarkActivity.ACTIVITY_RECORDINGS[activityNumber].getRecordingFileName();
        File f = new File(getExternalFilesDir(null) + "/" + fileName);
        if (!f.exists()) try {

            InputStream is = getAssets().open("recordings/" + fileName);
            int len;
            byte[] buffer = new byte[1024];
            FileOutputStream fos = new FileOutputStream(f);
            while ((len = is.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            is.close();
            fos.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            String logPath = getExternalFilesDir(null).getAbsolutePath() + "/frame-log";
            Log.d(TAG, "Logging FPS to " + logPath);
            fpsLog = new BufferedWriter(new FileWriter(logPath, true));
            fpsLog.write("test " + fileName + "\n");
        } catch (IOException e) {
            messageSnackbarHelper.showError(this, "Could not open file to log FPS");
        }

        // Queries are initialized in onDrawFrame
        timeQueries = new int[NUM_QUERIES];
        queryBuffer = new int[1];
        queryBuffer[0] = 0;
        queryIndex = 0;
        for (int i=0; i < NUM_QUERIES; i++) {
            timeQueries[i] = -1;
        }
    }

    private void cleanupCollectionResources() {
        try {
            if (fpsLog != null) {
                fpsLog.flush();
                fpsLog.close();
            }
            for (int i=0; i < NUM_QUERIES; i++) {
                if (timeQueries[i] >= 0) {
                    GLES30.glDeleteQueries(1, timeQueries, i);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Exception closing frame log: ", e);
        }
    }

    @Override
    protected void onDestroy() {
        try {
            if (fpsLog != null) {
                fpsLog.close();
            }
        } catch (IOException e) {

        }
        if (session != null) {
            // Explicitly close ARCore Session to release native resources.
            // Review the API reference for important considerations before calling close() in apps with
            // more complicated lifecycle requirements:
            // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
            session.close();
            session = null;
        }
        cleanupCollectionResources();
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

//        surfaceView.onResume();
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
            String destination = new File(getExternalFilesDir(null), fileName).getAbsolutePath();
            session.setPlaybackDataset(destination);
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
        } catch (PlaybackFailedException e) {
            setResult(RESULT_CANCELED);
            cleanupCollectionResources();
            finish();
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
//            surfaceView.onPause();
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
        GLES30.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

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

        String extensions = GLES30.glGetString(GLES30.GL_EXTENSIONS);
        hasTimerExtension = extensions.contains(" GL_EXT_disjoint_timer_query ");
    }

    @Override
    public void onSurfaceChanged(SampleRender render, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        virtualSceneFramebuffer.resize(width, height);
        GLES30.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(SampleRender render) {
        long frameTime = System.currentTimeMillis();
        if (session == null) {
            return;
        }
        if (session.getPlaybackStatus() == PlaybackStatus.FINISHED) {
            session.close();
            session = null;
            saveLastFrame(this.render.getViewportWidth(), this.render.getViewportHeight());
            try {
                if (fpsLog != null) {
                    fpsLog.flush();
                    fpsLog.close();
                    fpsLog = null;
                }
            } catch (IOException e) {

            }
            setResult(RESULT_OK);
            finish();
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

        // Obtain the current frame from ARSession. When the configuration is set to
        // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
        // camera framerate.
        Frame frame;
        // ARCore Processing Time
        long processTime = System.currentTimeMillis();
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
            return;
        }

//        // Show a message based on whether tracking has failed, if planes are detected, and if the user
//        // has placed any objects.
//        String message = null;
//        switch (state) {
//            case UNINITIALIZED:
//                break;
//            case UNSUPPORTED:
//                message = getResources().getString(R.string.status_unsupported);
//                break;
//            case PRETRACKING:
//                message = getResources().getString(R.string.status_pretracking);
//                break;
//            case EARTH_STATE_ERROR:
//                message = getResources().getString(R.string.status_earth_state_error);
//                break;
//            case LOCALIZING:
//                message = getResources().getString(R.string.status_localize_hint);
//                break;
//            case LOCALIZING_FAILED:
//                message = getResources().getString(R.string.status_localize_timeout);
//                break;
//            case LOCALIZED:
//                if (anchors.size() > 0) {
//                    message =
//                            getResources()
//                                    .getQuantityString(R.plurals.status_anchors_set, anchors.size(), anchors.size());
//
//                } else if (clearedAnchorsAmount != null) {
//                    message =
//                            getResources()
//                                    .getQuantityString(
//                                            R.plurals.status_anchors_cleared, clearedAnchorsAmount, clearedAnchorsAmount);
//                } else {
//                    message = getResources().getString(R.string.status_localize_complete);
//                }
//                break;
//        }
//        if (message == null) {
//            lastStatusText = null;
//            runOnUiThread(() -> statusTextView.setVisibility(View.INVISIBLE));
//        } else if (lastStatusText != message) {
//            lastStatusText = message;
//            runOnUiThread(
//                    () -> {
//                        statusTextView.setVisibility(View.VISIBLE);
//                        statusTextView.setText(lastStatusText);
//                    });
//        }

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
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);

        // Input Handling Time
        long handleInputTime = System.currentTimeMillis();
        processTime = handleInputTime - processTime;

        if (setAnchorButton.isPressed() && earth != null && earth.getTrackingState() == TrackingState.TRACKING) {
            System.out.println("SET ANCHOR BUTTON PRESSED");
            handleInputTime = System.currentTimeMillis() - handleInputTime;
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
                ByteBuffer payload = ByteBuffer.allocate(4 * 7);
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

        handleInputTime = System.currentTimeMillis() - handleInputTime;

        // Setup OpenGL time queries. Queries are organized in a queues that new queries can be made while the old result becomes
        // available.
        if (!hasTimerExtension) {
            messageSnackbarHelper.showError(this, "OpenGL extension EXT_disjoint_timer_query is unavailable on this device");
            return;
        }
        if (timeQueries[queryIndex] < 0) {
            GLES30.glGenQueries(1, timeQueries, queryIndex);
        }
        // Pop query off queue and fetch its result.
        if (timeQueries[(queryIndex + 1) % NUM_QUERIES] >= 0) {
            IntBuffer queryResult = IntBuffer.allocate(1);
            GLES30.glGetQueryObjectuiv(timeQueries[(queryIndex + 1) % NUM_QUERIES], GLES30.GL_QUERY_RESULT_AVAILABLE, queryResult);
            if (queryResult.get() == GLES30.GL_TRUE) {
                GLES30.glGetQueryObjectuiv(timeQueries[(queryIndex + 1) % NUM_QUERIES], GLES30.GL_QUERY_RESULT, queryBuffer, 0);
            }
        }
        // Begin query for current frame.
        GLES30.glBeginQuery(TIME_ELAPSED_EXT, timeQueries[queryIndex]);

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

            GLES30.glEndQuery(TIME_ELAPSED_EXT);
            queryIndex = (queryIndex + 1) % NUM_QUERIES;
            try {
                if (fpsLog != null) {
                    fpsLog.write(currentPhase + "," + frameTime + "," + processTime + "," + handleInputTime + "," + queryBuffer[0] + "," + (System.currentTimeMillis() - frameTime) + "\n");
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to log frame data", e);
            }

        }

        // Compose the virtual scene with the background.
        backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR);
    }

    private void saveLastFrame(int width, int height) {
        int size = width * height;
        int[] imageArray = new int[size];
        IntBuffer intBuffer = IntBuffer.allocate(size);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, intBuffer);
        int[] imageArray2 = intBuffer.array();
        for (int i=0; i < height; i++) {
            for (int j=0; j < width; j++) {
                imageArray[(height - i - 1) * width + j] = imageArray2[i * width + j];
            }
        }
        bitmap.copyPixelsFromBuffer(IntBuffer.wrap(imageArray));
        File imageFile = new File(getExternalFilesDir(null) + "/" + this.fileName.replace(".mp4", ".jpg"));
        try {
            imageFile.delete();
            FileOutputStream fileOutputStream = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, fileOutputStream);
            fileOutputStream.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to save preview image: ", e);
        }
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

    private void updateLocalizingState(Earth earth) {
        GeospatialPose geospatialPose = earth.getCameraGeospatialPose();
        if (geospatialPose.getHorizontalAccuracy() <= LOCALIZING_HORIZONTAL_ACCURACY_THRESHOLD_METERS
                && geospatialPose.getHeadingAccuracy() <= LOCALIZING_HEADING_ACCURACY_THRESHOLD_DEGREES) {
            state = State.LOCALIZED;
            if (anchors.isEmpty()) {
                createAnchorFromSharedPreferences(earth);
            }
            return;
        }

        if (TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - localizingStartTimestamp)
                > LOCALIZING_TIMEOUT_SECONDS) {
            state = State.LOCALIZING_FAILED;
            return;
        }

        updateGeospatialPoseText(geospatialPose);
    }

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
