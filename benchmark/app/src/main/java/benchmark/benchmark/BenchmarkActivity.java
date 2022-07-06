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

package benchmark.benchmark;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.auth.oauth2.GoogleCredentials;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import benchmark.augmented_faces.AugmentedFacesActivity;
import benchmark.augmented_image.AugmentedImageActivity;
import benchmark.augmented_object_generation.AugmentedObjectGenerationActivity;
import benchmark.augmented_object_recognition.AugmentedObjectRecognitionActivity;
import benchmark.common.helpers.CameraPermissionHelper;
import benchmark.common.helpers.LocationPermissionHelper;
import benchmark.geospatial.GeospatialActivity;

public class BenchmarkActivity extends AppCompatActivity {
    private static final String TAG = BenchmarkActivity.class.getSimpleName();
    public static final String ACTIVITY_NUMBER = "benchmark.ACTIVITY_NUMBER";

    // This is the order of activities that the app will open.
    public static final ActivityRecording[] ACTIVITY_RECORDINGS = {
            new ActivityRecording(AugmentedObjectGenerationActivity.class, "aug-obj-gen-1.mp4", "Object Generation", false),
            new ActivityRecording(AugmentedObjectGenerationActivity.class, "aug-obj-gen-2.mp4", "Multiple Objects Interaction", false),
            new ActivityRecording(AugmentedObjectGenerationActivity.class, "aug-obj-gen-3.mp4", "Scene Overloading", false),
            new ActivityRecording(AugmentedFacesActivity.class, "aug-faces-1.mp4", "Augmented Faces", false),
            new ActivityRecording(AugmentedImageActivity.class, "aug-img-1.mp4", "Augmented Image", false),
            new ActivityRecording(AugmentedObjectRecognitionActivity.class, "aug-obj-rcg-1.mp4", "Object Recognition", false),
            new ActivityRecording(AugmentedObjectRecognitionActivity.class, "aug-obj-rcg-1.mp4", "Object Recognition", true, false, true),
            new ActivityRecording(GeospatialActivity.class, "aug-geo-1.mp4", "Geospatial", true, true, false),
    };

    private LinearLayout resultsDisplay;

    private Camera camera;

    private SwitchCompat useCameraSwitch;
    private CameraPreview cameraPreview;
    private FrameLayout preview;
    private CheckBox[] sectionCheckBoxes;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        resultsDisplay = (LinearLayout) findViewById(R.id.results_display);

        Log.i(TAG, "MARAbenchmark External Files Directory: " + getExternalFilesDir(null).getAbsolutePath());
        TextView textView = new TextView(this);
        textView.setText("Sections to include:");
        resultsDisplay.addView(textView);
        sectionCheckBoxes = new CheckBox[ACTIVITY_RECORDINGS.length];

        for (int i = 0; i < ACTIVITY_RECORDINGS.length; i++) {
            ActivityRecording activityRecording = ACTIVITY_RECORDINGS[i];
            CheckBox checkBox = new CheckBox(this);
            checkBox.setChecked(true);
            checkBox.setText(activityRecording.getSectionName() + (activityRecording.doesUseCloud() ? " (Cloud)" : ""));

            if (activityRecording.doesUseCloud()) {
                if (activityRecording.doesRequireGCPKeys()) {
                    if (hasGCPKeys()) {
                        checkBox.setEnabled(true);
                    } else {
                        checkBox.setChecked(false);
                        checkBox.setEnabled(false);
                        checkBox.setText(checkBox.getText() + "\nMissing GCP Keys");
                    }
                }

                if (activityRecording.doesRequireCredentialsFile()) {
                    if (hasCredentialsFile()) {
                        checkBox.setEnabled(true);
                    } else {
                        checkBox.setChecked(false);
                        checkBox.setEnabled(false);
                        checkBox.setText(checkBox.getText() + "\nMissing Credentials File");
                    }
                }
            }

            sectionCheckBoxes[i] = checkBox;
            resultsDisplay.addView(checkBox);
        }

        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            CameraPermissionHelper.requestCameraPermission(this);
            return;
        }
        if (!LocationPermissionHelper.hasFineLocationPermission(this)) {
            LocationPermissionHelper.requestFineLocationPermission(this);
            return;
        }

        useCameraSwitch = findViewById(R.id.useCamera);

        camera = Camera.open(0);
        cameraPreview = new CameraPreview(this, camera);
        preview = (FrameLayout) findViewById(R.id.camera_frame);

        cameraPreview.setSurfaceTextureListener(cameraPreview);
        preview.addView(cameraPreview);
        useCameraSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (camera != null) {
                    turnCameraOff();
                } else {
                    turnCameraOn();
                }
            }
        });
    }

    private boolean hasGCPKeys() {
        try {
            ApplicationInfo applicationInfo = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = applicationInfo.metaData;
            String geoAPIKey = bundle.getString("com.google.android.geo.API_KEY");
            String arAPIKey = bundle.getString("com.google.android.ar.API_KEY");
            return geoAPIKey != null && arAPIKey != null;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean hasCredentialsFile() {
        System.out.println("hasCredentialsFile");
        try {
            int res = this.getResources().getIdentifier("credentials", "raw", this.getPackageName());
            if (res == 0) {
                Log.e(TAG, "Missing GCP credentials in res/raw/credentials.json.");
                return false;
            }
            GoogleCredentials.fromStream(this.getResources().openRawResource(res));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void onStartBenchmark(View view) {
        File previousLog = new File(getExternalFilesDir(null).getAbsolutePath() + "/frame-log");
        if (previousLog.exists() && !previousLog.delete()) {
            new AlertDialog.Builder(this).setMessage("Failed to remove previous benchmark results").show();
        }

        for (int i = 0; i < ACTIVITY_RECORDINGS.length; i++) {
            ACTIVITY_RECORDINGS[i].setEnabled(sectionCheckBoxes[i].isChecked());
        }

        for (int i = 0; i < ACTIVITY_RECORDINGS.length; i++) {
            if (ACTIVITY_RECORDINGS[i].isEnabled()) {
                Intent intent = new Intent(this, ACTIVITY_RECORDINGS[i].getActivity());
                intent.putExtra(ACTIVITY_NUMBER, i);
                intent.putExtra("useCloud", ACTIVITY_RECORDINGS[i].isUsingCloud());
                startActivityForResult(intent, i);
                break;
            }
        }
    }

    private void turnCameraOn() {
        camera = Camera.open(0);
        cameraPreview.setSurfaceTextureListener(cameraPreview);
        preview.addView(cameraPreview);
    }

    private void turnCameraOff() {
        cameraPreview.setSurfaceTextureListener(null);
        preview.removeAllViews();
        camera.stopPreview();
        camera.release();
        camera = null;
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_CANCELED) {
            new AlertDialog.Builder(this).setMessage("Test " + requestCode + " did not complete").show();
        }

        boolean flag = false;
        for (int i = requestCode + 1; i < ACTIVITY_RECORDINGS.length; i++) {
            if (ACTIVITY_RECORDINGS[i].isEnabled()) {
                Intent intent = new Intent(this, ACTIVITY_RECORDINGS[i].getActivity());
                intent.putExtra(ACTIVITY_NUMBER, i);
                intent.putExtra("useCloud", ACTIVITY_RECORDINGS[i].isUsingCloud());
                startActivityForResult(intent, i);
                flag = true;
                break;
            }
        }

        if (!flag) {
            reportResults();
        }
    }

    @SuppressLint("SetTextI18n")
    private void reportResults() {
        BufferedReader fpsLog;
        String logPath = getExternalFilesDir(null).getAbsolutePath() + "/frame-log";
        try {
            fpsLog = new BufferedReader(new FileReader(logPath));
        } catch (FileNotFoundException e) {
            new AlertDialog.Builder(this).setMessage("Could not access logged frame data").show();
            return;
        }

        String line;
        try {
            line = fpsLog.readLine();
        } catch (IOException e) {
            new AlertDialog.Builder(this).setMessage("Error reading frame data").show();
            return;
        }
        for (int testNumber = 0; testNumber < ACTIVITY_RECORDINGS.length; testNumber++) {
            if (!ACTIVITY_RECORDINGS[testNumber].isEnabled()) {
                continue;
            }
            String recordingName = ACTIVITY_RECORDINGS[testNumber].getRecordingFileName();
            String sectionName = ACTIVITY_RECORDINGS[testNumber].getSectionName() + (ACTIVITY_RECORDINGS[testNumber].isUsingCloud() ? " (Cloud)" : "");
            int currentPhase = 1;
            long startTime = 0, t = 0;
            long process = 0, maxInput = 0, total = 0;
            float renderObjects = 0.f;
            try {
                if (line == null || !line.equals("test " + recordingName)) {
                    new AlertDialog.Builder(this).setMessage("No frame data for test " + testNumber + 1).show();
                    continue;
                } else {
                    line = fpsLog.readLine();
                    startTime = Long.decode(line.split(",")[1]);
                }
                ImageView previewImage = new ImageView(this);
                File imageFile = new File(getExternalFilesDir(null) + "/" + recordingName.replace(".mp4", ".jpg"));
                FileInputStream fis = new FileInputStream(imageFile);
                Bitmap bitmap = BitmapFactory.decodeStream(fis);
                previewImage.setImageBitmap(bitmap);
                resultsDisplay.addView(previewImage);
                int i = 0;
                while (true) {
                    if (line != null && !line.startsWith("test ")) {
                        String[] times = line.split(",");
                        if (times.length < 6) {
                            line = fpsLog.readLine();
                            continue;
                        }
                        int phase = Integer.decode(times[0]);
                        if (phase != currentPhase) {
                            float fps = 1000.f * (i - 1) / (t - startTime);
                            TextView results = new TextView(this);
                            results.setText(
                                    "FPS and Runtimes - " + sectionName + " Phase " + currentPhase + "\n"
                                            + "File name: " + recordingName + "\n"
                                            + "FPS: " + fps + "\n"
                                            + "ARCore Processing Time: " + (float) process / i + "\n"
                                            + "Max Input Handling Time: " + maxInput + "\n"
                                            + "GPU Object Rendering Time: " + renderObjects / i + "\n"
                                            + "Total CPU Runtime per frame: " + (float) total / i + "\n");
                            resultsDisplay.addView(results);
                            startTime = Long.decode(times[1]);
                            currentPhase = phase;
                            process = 0;
                            maxInput = 0;
                            renderObjects = 0;
                            total = 0;
                            i = 0;
                        }
                        t = Long.decode(times[1]);
                        process += Integer.decode(times[2]);
                        maxInput = Math.max(maxInput, Integer.decode(times[3]));
                        renderObjects += Float.parseFloat(times[4]) / 1e6;
                        total += Integer.decode(times[5]);
                        i++;
                    } else {
                        float fps = 1000.f * (i - 1) / (t - startTime);
                        TextView results = new TextView(this);
                        results.setTextIsSelectable(true);
                        results.setText(
                                "FPS and Runtimes - " + sectionName + " Phase " + currentPhase + "\n"
                                        + "File name: " + recordingName + "\n"
                                        + "FPS: " + fps + "\n"
                                        + "ARCore Processing Time: " + (float) process / i + "\n"
                                        + "Max Input Handling Time: " + maxInput + "\n"
                                        + "GPU Object Rendering Time: " + renderObjects / i + "\n"
                                        + "Total CPU Runtime per frame: " + (float) total / i + "\n");
                        resultsDisplay.addView(results);
                        break;
                    }
                    line = fpsLog.readLine();
                }
            } catch (IOException e) {
                new AlertDialog.Builder(this).setMessage("Error reading frame data").show();
            }
        }
    }

    protected void onDestroy() {
//        turnCameraOff();
        super.onDestroy();
    }
}