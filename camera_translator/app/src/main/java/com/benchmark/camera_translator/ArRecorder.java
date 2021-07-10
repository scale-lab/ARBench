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
 * Copyright 2017 Google LLC
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

package com.benchmark.camera_translator;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.loader.content.CursorLoader;

import com.google.ar.core.RecordingConfig;
import com.google.ar.core.RecordingStatus;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.RecordingFailedException;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ArRecorder {

    private static final String TAG = ArRecorder.class.getSimpleName();
    private final String MP4_VIDEO_MIME_TYPE = "video/mp4";
    private final int REQUEST_WRITE_EXTERNAL_STORAGE = 1;

    private Session session;
    private GLSurfaceView surfaceView;

    public ArRecorder(Session session, GLSurfaceView surfaceView) {
        this.session = session;
        this.surfaceView = surfaceView;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public boolean startRecording(Session session, Activity context) {
        String mp4FilePath = createMp4File(context);
        if (mp4FilePath == null)
            return false;

        Log.d(TAG, "startRecording at: " + mp4FilePath);

        pauseARCoreSession();

        // Configure the ARCore session to start recording.
        RecordingConfig recordingConfig = new RecordingConfig(session)
                .setMp4DatasetFilePath(mp4FilePath)
                .setAutoStopOnPause(true);

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

    public void pauseARCoreSession() {
        surfaceView.onPause();
        session.pause();
    }

    public boolean resumeARCoreSession() {
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            Log.e(TAG, "CameraNotAvailableException in resumeARCoreSession", e);
            return false;
        }

        surfaceView.onResume();
        return true;
    }

    public boolean stopRecording(Activity context) {
        TextView textView = context.findViewById(R.id.translated_text);
        textView.setText("");

        try {
            session.stopRecording();
        } catch (RecordingFailedException e) {
            Log.e(TAG, "stopRecording - Failed to stop recording", e);
            return false;
        }

        return session.getRecordingStatus() == RecordingStatus.NONE;
    }

    private String createMp4File(Activity context) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (!checkAndRequestStoragePermission(context)) {
                Log.i(TAG, String.format(
                        "Didn't createMp4File. No storage permission, API Level = %d",
                        Build.VERSION.SDK_INT));
                return null;
            }
        }

        @SuppressLint("SimpleDateFormat") SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String mp4FileName = "arcore-" + dateFormat.format(new Date()) + ".mp4";

        ContentResolver resolver = context.getContentResolver();

        Uri videoCollection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            videoCollection = MediaStore.Video.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            videoCollection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        }

        ContentValues newMp4FileDetails = new ContentValues();
        newMp4FileDetails.put(MediaStore.Video.Media.DISPLAY_NAME, mp4FileName);
        newMp4FileDetails.put(MediaStore.Video.Media.MIME_TYPE, MP4_VIDEO_MIME_TYPE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            newMp4FileDetails.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        } else {
            File mp4FileDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            String absoluteMp4FilePath = new File(mp4FileDir, mp4FileName).getAbsolutePath();
            newMp4FileDetails.put(MediaStore.Video.Media.DATA, absoluteMp4FilePath);
        }

        Uri newMp4FileUri = resolver.insert(videoCollection, newMp4FileDetails);

        if (newMp4FileUri == null) {
            Log.e(TAG, String.format("Failed to insert Video entity in MediaStore. API Level = %d", Build.VERSION.SDK_INT));
            return null;
        }

        if (!testFileWriteAccess(newMp4FileUri, context)) {
            return null;
        }

        String filePath = getMediaFilePath(newMp4FileUri, context);
        Log.d(TAG, String.format("createMp4File = %s, API Level = %d", filePath, Build.VERSION.SDK_INT));

        return filePath;
    }

    private boolean testFileWriteAccess(Uri contentUri, Context context) {
        try (java.io.OutputStream ignored = context.getContentResolver().openOutputStream(contentUri)) {
            Log.d(TAG, String.format("Success in testFileWriteAccess %s", contentUri.toString()));
            return true;
        } catch (java.io.FileNotFoundException e) {
            Log.e(TAG, String.format("FileNotFoundException in testFileWriteAccess %s", contentUri.toString()), e);
        } catch (java.io.IOException e) {
            Log.e(TAG, String.format("IOException in testFileWriteAccess %s", contentUri.toString()), e);
        }

        return false;
    }

    private String getMediaFilePath(Uri mediaStoreUri, Context context) {
        String[] projection = { MediaStore.Images.Media.DATA };

        CursorLoader loader = new CursorLoader(context, mediaStoreUri, projection, null, null, null);
        Cursor cursor = loader.loadInBackground();
        assert cursor != null;
        cursor.moveToFirst();

        int data_column_index = cursor.getColumnIndexOrThrow(projection[0]);
        String data_result = cursor.getString(data_column_index);
        cursor.close();

        return data_result;
    }

    public boolean checkAndRequestStoragePermission(Activity context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(context,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_EXTERNAL_STORAGE);
            return false;
        }

        return true;
    }
}
