/*
 * Copyright 2021, Brown University, Providence, RI.
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
 * Copyright 2020 Google LLC
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

package benchmark.augmented_faces;

import android.content.Intent;
import android.graphics.Bitmap;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedFace;
import com.google.ar.core.AugmentedFace.RegionType;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.Config;
import com.google.ar.core.Config.AugmentedFaceMode;
import com.google.ar.core.Frame;
import com.google.ar.core.PlaybackStatus;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;

import benchmark.benchmark.BenchmarkActivity;
import benchmark.common.helpers.CameraPermissionHelper;
import benchmark.common.helpers.DisplayRotationHelper;
import benchmark.common.helpers.FullScreenHelper;
import benchmark.common.helpers.SnackbarHelper;
import benchmark.common.helpers.TrackingStateHelper;
import benchmark.common.rendering.BackgroundRenderer;
import benchmark.common.rendering.ObjectRenderer;

import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.PlaybackFailedException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import benchmark.benchmark.R;
import benchmark.common.samplerender.OffscreenRender;
import benchmark.common.samplerender.SampleRender;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore API. The application will display any detected planes and will allow the user to tap on a
 * plane to place a 3d model of the Android robot.
 */
public class AugmentedFacesActivity extends AppCompatActivity implements SampleRender.Renderer {
    private static final String TAG = AugmentedFacesActivity.class.getSimpleName();

    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private SurfaceView surfaceView;

    private boolean installRequested;

    private Session session;
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private DisplayRotationHelper displayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);

    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private final AugmentedFaceRenderer augmentedFaceRenderer = new AugmentedFaceRenderer();
    private final ObjectRenderer noseObject = new ObjectRenderer();
    private final ObjectRenderer rightEarObject = new ObjectRenderer();
    private final ObjectRenderer leftEarObject = new ObjectRenderer();
    private OffscreenRender render;
    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private final float[] noseMatrix = new float[16];
    private final float[] rightEarMatrix = new float[16];
    private final float[] leftEarMatrix = new float[16];
    private static final float[] DEFAULT_COLOR = new float[]{0f, 0f, 0f, 0f};

    private BufferedWriter fpsLog;
    String fileName;
    private int currentPhase = 1;

    private boolean hasTimerExtension;
    private static final int TIME_ELAPSED_EXT = 0x88BF;
    private static final int NUM_QUERIES = 10;
    private int[] timeQueries;
    private int[] queryBuffer;
    private int queryIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_augmented_faces);
        surfaceView = new SurfaceView(this);
        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

        // Set up renderer.
        // Offscreen
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                render = new OffscreenRender(surfaceView, AugmentedFacesActivity.this, getAssets());
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

        if (session == null) {
            Exception exception = null;
            String message = null;
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

                // Create the session and configure it to use a front-facing (selfie) camera.
                session = new Session(/* context= */ this, EnumSet.noneOf(Session.Feature.class));
                CameraConfigFilter cameraConfigFilter = new CameraConfigFilter(session);
                cameraConfigFilter.setFacingDirection(CameraConfig.FacingDirection.FRONT);
                List<CameraConfig> cameraConfigs = session.getSupportedCameraConfigs(cameraConfigFilter);
                if (!cameraConfigs.isEmpty()) {
                    // Element 0 contains the camera config that best matches the session feature
                    // and filter settings.
                    session.setCameraConfig(cameraConfigs.get(0));
                } else {
                    message = "This device does not have a front-facing (selfie) camera";
                    exception = new UnavailableDeviceNotCompatibleException(message);
                }
                configureSession();

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
            String destination = new File(getExternalFilesDir(null), fileName).getAbsolutePath();
            session.setPlaybackDataset(destination);
            session.resume();
        } catch (CameraNotAvailableException e) {
            messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
            session = null;
            return;
        } catch (PlaybackFailedException e) {
            setResult(RESULT_CANCELED);
            cleanupCollectionResources();
            finish();
        }

        //surfaceView.onResume();
        displayRotationHelper.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            //surfaceView.onPause();
            session.pause();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
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

        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
        try {
            // Create the texture and pass it to ARCore session to be filled during update().
            backgroundRenderer.createOnGlThread(/*context=*/ this);
            augmentedFaceRenderer.createOnGlThread(this, "models/freckles.png");
            augmentedFaceRenderer.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f);
            noseObject.createOnGlThread(/*context=*/ this, "models/nose.obj", "models/nose_fur.png");
            noseObject.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f);
            noseObject.setBlendMode(ObjectRenderer.BlendMode.AlphaBlending);
            rightEarObject.createOnGlThread(this, "models/forehead_right.obj", "models/ear_fur.png");
            rightEarObject.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f);
            rightEarObject.setBlendMode(ObjectRenderer.BlendMode.AlphaBlending);
            leftEarObject.createOnGlThread(this, "models/forehead_left.obj", "models/ear_fur.png");
            leftEarObject.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f);
            leftEarObject.setBlendMode(ObjectRenderer.BlendMode.AlphaBlending);

        } catch (IOException e) {
            Log.e(TAG, "Failed to read an asset file", e);
        }
        String extensions = GLES30.glGetString(GLES30.GL_EXTENSIONS);
        hasTimerExtension = extensions.contains(" GL_EXT_disjoint_timer_query ");
    }

    @Override
    public void onSurfaceChanged(SampleRender render, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
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

        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        displayRotationHelper.updateSessionIfNeeded(session);

        try {
            session.setCameraTextureName(backgroundRenderer.getTextureId());

            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            long processTime = System.currentTimeMillis();
            Frame frame = session.update();
            Camera camera = frame.getCamera();

            // Get projection matrix.
            float[] projectionMatrix = new float[16];
            camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);

            // Get camera matrix and draw.
            float[] viewMatrix = new float[16];
            camera.getViewMatrix(viewMatrix, 0);

            // Compute lighting from average intensity of the image.
            // The first three components are color scaling factors.
            // The last one is the average pixel intensity in gamma space.
            final float[] colorCorrectionRgba = new float[4];
            frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);
            processTime = System.currentTimeMillis() - processTime;

            // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
            trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

            // If frame is ready, render camera preview image to the GL surface.
            backgroundRenderer.draw(frame);

            if (!hasTimerExtension) {
                messageSnackbarHelper.showError(this, "OpenGL extension EXT_disjoint_timer_query is unavailable on this device");
                return;
            }
            if (timeQueries[queryIndex] < 0) {
                GLES30.glGenQueries(1, timeQueries, queryIndex);
            }
            if (timeQueries[(queryIndex + 1) % NUM_QUERIES] >= 0) {
                IntBuffer queryResult = IntBuffer.allocate(1);
                GLES30.glGetQueryObjectuiv(timeQueries[(queryIndex + 1) % NUM_QUERIES], GLES30.GL_QUERY_RESULT_AVAILABLE, queryResult);
                if (queryResult.get() == GLES30.GL_TRUE) {
                    GLES30.glGetQueryObjectuiv(timeQueries[(queryIndex + 1) % NUM_QUERIES], GLES30.GL_QUERY_RESULT, queryBuffer, 0);
                }
            }
            GLES30.glBeginQuery(TIME_ELAPSED_EXT, timeQueries[queryIndex]);

            // ARCore's face detection works best on upright faces, relative to gravity.
            // If the device cannot determine a screen side aligned with gravity, face
            // detection may not work optimally.
            Collection<AugmentedFace> faces = session.getAllTrackables(AugmentedFace.class);
            for (AugmentedFace face : faces) {
                if (face.getTrackingState() != TrackingState.TRACKING) {
                    break;
                }

                float scaleFactor = 1.0f;

                // Face objects use transparency so they must be rendered back to front without depth write.
                GLES30.glDepthMask(false);

                // Each face's region poses, mesh vertices, and mesh normals are updated every frame.

                // 1. Render the face mesh first, behind any 3D objects attached to the face regions.
                float[] modelMatrix = new float[16];
                face.getCenterPose().toMatrix(modelMatrix, 0);
                augmentedFaceRenderer.draw(
                        projectionMatrix, viewMatrix, modelMatrix, colorCorrectionRgba, face);

                // 2. Next, render the 3D objects attached to the forehead.
                face.getRegionPose(RegionType.FOREHEAD_RIGHT).toMatrix(rightEarMatrix, 0);
                rightEarObject.updateModelMatrix(rightEarMatrix, scaleFactor);
                rightEarObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba, DEFAULT_COLOR);

                face.getRegionPose(RegionType.FOREHEAD_LEFT).toMatrix(leftEarMatrix, 0);
                leftEarObject.updateModelMatrix(leftEarMatrix, scaleFactor);
                leftEarObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba, DEFAULT_COLOR);

                // 3. Render the nose last so that it is not occluded by face mesh or by 3D objects attached
                // to the forehead regions.
                face.getRegionPose(RegionType.NOSE_TIP).toMatrix(noseMatrix, 0);
                noseObject.updateModelMatrix(noseMatrix, scaleFactor);
                noseObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba, DEFAULT_COLOR);

                GLES30.glEndQuery(TIME_ELAPSED_EXT);
                queryIndex = (queryIndex + 1) % NUM_QUERIES;

                try {
                    if (fpsLog != null) {
                        fpsLog.write(currentPhase + "," + frameTime + "," + processTime + ",0," + queryBuffer[0] + "," + (System.currentTimeMillis() - frameTime) + "\n");
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to log frame data", e);
                    messageSnackbarHelper.showError(this, "Failed to log frame data: " + e);
                }
            }
        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        } finally {
            GLES30.glDepthMask(true);
        }

    }

    private void configureSession() {
        Config config = new Config(session);
        config.setAugmentedFaceMode(AugmentedFaceMode.MESH3D);
        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
        session.configure(config);
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
}
