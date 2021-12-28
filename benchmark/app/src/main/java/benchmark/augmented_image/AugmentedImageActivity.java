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
 * Copyright 2018 Google LLC
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

package benchmark.augmented_image;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.PlaybackStatus;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.PlaybackFailedException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
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
import java.util.HashMap;
import java.util.Map;

import benchmark.benchmark.BenchmarkActivity;
import benchmark.benchmark.R;
import benchmark.common.helpers.CameraPermissionHelper;
import benchmark.common.helpers.DisplayRotationHelper;
import benchmark.common.helpers.FullScreenHelper;
import benchmark.common.helpers.SnackbarHelper;
import benchmark.common.helpers.TrackingStateHelper;
import benchmark.common.rendering.BackgroundRenderer;
import benchmark.common.samplerender.OffscreenRender;
import benchmark.common.samplerender.SampleRender;

/**
 * This app extends the HelloAR Java app to include image tracking functionality.
 *
 * <p>In this example, we assume all images are static or moving slowly with a large occupation of
 * the screen. If the target is actively moving, we recommend to check
 * AugmentedImage.getTrackingMethod() and render only when the tracking method equals to
 * FULL_TRACKING. See details in <a
 * href="https://developers.google.com/ar/develop/java/augmented-images/">Recognize and Augment
 * Images</a>.
 */
public class AugmentedImageActivity extends AppCompatActivity implements SampleRender.Renderer {
    private static final String TAG = AugmentedImageActivity.class.getSimpleName();

    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private SurfaceView surfaceView;
    private ImageView fitToScanView;
    private RequestManager glideRequestManager;

    private boolean installRequested;

    private OffscreenRender render;
    private Session session;
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private DisplayRotationHelper displayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);

    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private final AugmentedImageRenderer augmentedImageRenderer = new AugmentedImageRenderer();

    private boolean shouldConfigureSession = false;

    // Augmented image configuration and rendering.
    // Load a single image (true) or a pre-generated image database (false).
    private final boolean useSingleImage = false;
    // Augmented image and its associated center pose anchor, keyed by index of the augmented image in
    // the
    // database.
    private final Map<Integer, Pair<AugmentedImage, Anchor>> augmentedImageMap = new HashMap<>();

    private final String MP4_VIDEO_MIME_TYPE = "video/mp4";
    private final int REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    private int REQUEST_MP4_SELECTOR = 1;
    private boolean hasSetTextureNames = false;

    private BufferedWriter fpsLog;

    String fileName;
    int currentPhase = 1;

    private boolean hasTimerExtension;
    private static final int TIME_ELAPSED_EXT = 0x88BF;
    private static final int NUM_QUERIES = 10;
    private int[] timeQueries;
    private int[] queryBuffer;
    private int queryIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_augmented_image);
        surfaceView = new SurfaceView(this);
        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

        // Set up renderer.
        // Offscreen
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                render = new OffscreenRender(surfaceView, AugmentedImageActivity.this, getAssets());
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
        if (fpsLog != null) {
            try {
                fpsLog.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
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

                session = new Session(/* context = */ this);
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
            } catch (Exception e) {
                message = "This device does not support AR";
                exception = e;
            }

            if (message != null) {
                messageSnackbarHelper.showError(this, message);
                Log.e(TAG, "Exception creating session", exception);
                return;
            }

            shouldConfigureSession = true;
        }

        if (shouldConfigureSession) {
            configureSession();
            shouldConfigureSession = false;
        }

        try {
            configureSession();
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
            Toast.makeText(
                    this, "Camera permissions are needed to run this application", Toast.LENGTH_LONG)
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
            augmentedImageRenderer.createOnGlThread(/*context=*/ this);
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

        if (!hasSetTextureNames) {
            session.setCameraTextureName(backgroundRenderer.getTextureId());
            hasSetTextureNames = true;
        }

        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        displayRotationHelper.updateSessionIfNeeded(session);

        Frame frame;
        try {
            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            long processTime = System.currentTimeMillis();
            frame = session.update();
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

            // Visualize augmented images.
            drawAugmentedImages(frame, projectionMatrix, viewMatrix, colorCorrectionRgba);

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
        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        } finally {
            GLES30.glDepthMask(true);
        }
    }

    private void configureSession() {
        Config config = new Config(session);
        config.setFocusMode(Config.FocusMode.AUTO);
        if (!setupAugmentedImageDatabase(config)) {
            messageSnackbarHelper.showError(this, "Could not setup augmented image database");
        }
        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
        session.configure(config);
    }

    private void drawAugmentedImages(
            Frame frame, float[] projmtx, float[] viewmtx, float[] colorCorrectionRgba) {
        Collection<AugmentedImage> updatedAugmentedImages =
                frame.getUpdatedTrackables(AugmentedImage.class);

        // Iterate to update augmentedImageMap, remove elements we cannot draw.
        for (AugmentedImage augmentedImage : updatedAugmentedImages) {
            switch (augmentedImage.getTrackingState()) {
                case PAUSED:
                    // When an image is in PAUSED state, but the camera is not PAUSED, it has been detected,
                    // but not yet tracked.
                    String text = String.format("Detected Image %d", augmentedImage.getIndex());
                    Log.d(TAG, text);
                    break;

                case TRACKING:
                    // Have to switch to UI Thread to update View.
                    this.runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                }
                            });

                    // Create a new anchor for newly found images.
                    if (!augmentedImageMap.containsKey(augmentedImage.getIndex())) {
                        Anchor centerPoseAnchor = augmentedImage.createAnchor(augmentedImage.getCenterPose());
                        augmentedImageMap.put(
                                augmentedImage.getIndex(), Pair.create(augmentedImage, centerPoseAnchor));
                    }
                    break;

                case STOPPED:
                    augmentedImageMap.remove(augmentedImage.getIndex());
                    break;

                default:
                    break;
            }
        }

        // Draw all images in augmentedImageMap
        for (Pair<AugmentedImage, Anchor> pair : augmentedImageMap.values()) {
            AugmentedImage augmentedImage = pair.first;
            Anchor centerAnchor = augmentedImageMap.get(augmentedImage.getIndex()).second;
            switch (augmentedImage.getTrackingState()) {
                case TRACKING:
                    augmentedImageRenderer.draw(
                            viewmtx, projmtx, augmentedImage, centerAnchor, colorCorrectionRgba);
                    break;
                default:
                    break;
            }
        }
    }

    private boolean setupAugmentedImageDatabase(Config config) {
        AugmentedImageDatabase augmentedImageDatabase;

        // There are two ways to configure an AugmentedImageDatabase:
        // 1. Add Bitmap to DB directly
        // 2. Load a pre-built AugmentedImageDatabase
        // Option 2) has
        // * shorter setup time
        // * doesn't require images to be packaged in apk.
        if (useSingleImage) {
            Bitmap augmentedImageBitmap = loadAugmentedImageBitmap();
            if (augmentedImageBitmap == null) {
                return false;
            }

            augmentedImageDatabase = new AugmentedImageDatabase(session);
            augmentedImageDatabase.addImage("image_name", augmentedImageBitmap);
            // If the physical size of the image is known, you can instead use:
            //     augmentedImageDatabase.addImage("image_name", augmentedImageBitmap, widthInMeters);
            // This will improve the initial detection speed. ARCore will still actively estimate the
            // physical size of the image as it is viewed from multiple viewpoints.
        } else {
            // This is an alternative way to initialize an AugmentedImageDatabase instance,
            // load a pre-existing augmented image database.
            try (InputStream is = getAssets().open("sample_database.imgdb")) {
                augmentedImageDatabase = AugmentedImageDatabase.deserialize(session, is);
            } catch (IOException e) {
                Log.e(TAG, "IO exception loading augmented image database.", e);
                return false;
            }
        }

        config.setAugmentedImageDatabase(augmentedImageDatabase);
        return true;
    }

    private Bitmap loadAugmentedImageBitmap() {
        try (InputStream is = getAssets().open("default.jpg")) {
            return BitmapFactory.decodeStream(is);
        } catch (IOException e) {
            Log.e(TAG, "IO exception loading augmented image bitmap.", e);
        }
        return null;
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
