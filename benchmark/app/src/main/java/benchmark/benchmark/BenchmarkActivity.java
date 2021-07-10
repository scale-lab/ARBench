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

import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import benchmark.augmentedfaces.AugmentedFacesActivity;
import benchmark.common.samplerender.SampleRender;
import benchmark.helloar.HelloArActivity;

public class BenchmarkActivity extends AppCompatActivity {
    public static final String FILE_NUMBER = "benchmark.FILE_NUMBER";
    public static final int NUM_TESTS = 5;
    private LinearLayout resultsDisplay;
    private String filesPath;
    private GLSurfaceView surfaceView;
    private SampleRender render;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        filesPath = this.getExternalFilesDir(null).getAbsolutePath();
        resultsDisplay = (LinearLayout) findViewById(R.id.results_display);
    }

    public void onStartBenchmark(View view) {
        Intent intent = new Intent(this, HelloArActivity.class);
        intent.putExtra(FILE_NUMBER, 6);
        startActivityForResult(intent, 6);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_CANCELED) {
            new AlertDialog.Builder(this).setMessage("Failed to perform test " + requestCode).show();
        } else {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(filesPath + "/fps.csv"));
                String line = reader.readLine();
                int currentPhase = 1;
                long startTime = Long.decode(line.split(",")[1]), t = 0;
                long update = 0, maxInput = 0, renderBackground = 0, renderObjects = 0, total = 0;
                int i = 0;
                while (true) {
                    if (line != null) {
                        String[] times = line.split(",");
                        int phase = Integer.decode(times[0]);
                        if (phase != currentPhase) {
                            float fps = 1000.f * (i - 1) / (t - startTime);
                            TextView results = new TextView(this);
                            results.setText(
                                    "FPS and Runtimes - Test " + requestCode + " Phase " + currentPhase + "\n"
                                            + "FPS: " + fps + "\n"
                                            + "Update: " + (float) update / i + "\n"
                                            + "Max Input: " + maxInput + "\n"
                                            + "Render Background: " + (float) renderBackground / i + "\n"
                                            + "Render Objects: " + (float) renderObjects / i + "\n"
                                            + "Total Runtime: " + (float) total / i + "\n");
                            resultsDisplay.addView(results);
                            startTime = Long.decode(times[1]);
                            currentPhase = phase;
                            update = 0;
                            maxInput = 0;
                            renderBackground = 0;
                            renderObjects = 0;
                            total = 0;
                            i = 0;
                        }
                        t = Long.decode(times[1]);
                        update += Integer.decode(times[2]);
                        maxInput = Math.max(maxInput, Integer.decode(times[3]));
                        renderBackground += Integer.decode(times[4]);
                        renderObjects += Integer.decode(times[5]);
                        total += Integer.decode(times[6]);
                        i++;
                    } else {
                        float fps = 1000.f * (i - 1) / (t - startTime);
                        TextView results = new TextView(this);
                        results.setText(
                                "FPS and Runtimes - Test " + requestCode + " Phase " + currentPhase + "\n"
                                        + "FPS: " + fps + "\n"
                                        + "Update: " + (float) update / i + "\n"
                                        + "Max Input: " + maxInput + "\n"
                                        + "Render Background: " + (float) renderBackground / i + "\n"
                                        + "Render Objects: " + (float) renderObjects / i + "\n"
                                        + "Total Runtime: " + (float) total / i + "\n");
                        resultsDisplay.addView(results);
                        break;
                    }
                    line = reader.readLine();
                }
            } catch (FileNotFoundException e) {
                new AlertDialog.Builder(this).setMessage("Failed to perform test " + requestCode).show();
            } catch (IOException e) {
                new AlertDialog.Builder(this).setMessage("Failed to perform test " + requestCode).show();
            } catch (Exception e) {
                new AlertDialog.Builder(this).setMessage(e.getMessage()).show();
            }
        }

        if (requestCode < NUM_TESTS - 1) {
            Intent intent = new Intent(this, HelloArActivity.class);
            intent.putExtra(FILE_NUMBER, requestCode + 1);
            startActivityForResult(intent, requestCode + 1);
        } else if (requestCode == NUM_TESTS - 1) {
            Intent intent = new Intent(this, AugmentedFacesActivity.class);
            intent.putExtra(FILE_NUMBER, requestCode + 1);
            startActivityForResult(intent, requestCode + 1);
        }
    }
}