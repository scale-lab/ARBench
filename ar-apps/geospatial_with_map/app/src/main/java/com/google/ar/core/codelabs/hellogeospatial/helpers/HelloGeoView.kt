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
package com.google.ar.core.codelabs.hellogeospatial.helpers

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.loader.content.CursorLoader
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.ar.core.*
import com.google.ar.core.codelabs.hellogeospatial.HelloGeoActivity
import com.google.ar.core.codelabs.hellogeospatial.R
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper
import com.google.ar.core.exceptions.*
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/** Contains UI elements for Hello Geo. */
class HelloGeoView(val activity: HelloGeoActivity) : DefaultLifecycleObserver {
    val TAG = "MainActivity"
    private val MP4_VIDEO_MIME_TYPE = "video/mp4"
    private val REQUEST_MP4_SELECTOR = 1

    val root: View = View.inflate(activity, R.layout.activity_main, null)
    val surfaceView: GLSurfaceView = root.findViewById<GLSurfaceView>(R.id.surfaceview)

    val session
        get() = activity.arCoreSessionHelper.session

    val snackbarHelper = SnackbarHelper()

    var mapView: MapView? = null
    val mapTouchWrapper = root.findViewById<MapTouchWrapper>(R.id.map_wrapper).apply {
        setup { screenLocation ->
            val latLng: LatLng =
                mapView?.googleMap?.projection?.fromScreenLocation(screenLocation) ?: return@setup
            activity.renderer.onMapClick(latLng)
        }
    }
    val mapFragment =
        (activity.supportFragmentManager.findFragmentById(R.id.map)!! as SupportMapFragment).also {
            it.getMapAsync { googleMap -> mapView = MapView(activity, googleMap) }
        }

    val statusText = root.findViewById<TextView>(R.id.statusText)
    fun updateStatusText(earth: Earth, cameraGeospatialPose: GeospatialPose?) {
        activity.runOnUiThread {
            val poseText = if (cameraGeospatialPose == null) "" else
                activity.getString(
                    R.string.geospatial_pose,
                    cameraGeospatialPose.latitude,
                    cameraGeospatialPose.longitude,
                    cameraGeospatialPose.horizontalAccuracy,
                    cameraGeospatialPose.altitude,
                    cameraGeospatialPose.verticalAccuracy,
                    cameraGeospatialPose.heading,
                    cameraGeospatialPose.headingAccuracy
                )
            statusText.text = activity.resources.getString(
                R.string.earth_state,
                earth.earthState.toString(),
                earth.trackingState.toString(),
                poseText
            )
        }
    }


    public fun pauseARCoreSession() {
        // Pause the GLSurfaceView so that it doesn't update the ARCore session.
        // Pause the ARCore session so that we can update its configuration.
        // If the GLSurfaceView is not paused,
        //   onDrawFrame() will try to update the ARCore session
        //   while it's paused, resulting in a crash.
        activity.renderer.displayRotationHelper.onPause()
        surfaceView.onPause()
        activity.arCoreSessionHelper.session?.pause()
    }

    fun resumeARCoreSession(): Boolean {
        val session = activity.arCoreSessionHelper.session ?: return false
        // We must resume the ARCore session before the GLSurfaceView.
        // Otherwise, the GLSurfaceView will try to update the ARCore session.
        try {
            session.resume()
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "CameraNotAvailableException in resumeARCoreSession", e)
            return false
        }
        surfaceView.onResume()
        activity.renderer.displayRotationHelper.onResume()
        return true
    }

    public fun startRecording(): Boolean {
        val session = activity.arCoreSessionHelper.session ?: return false;
        val mp4FilePath: String = createMp4File() ?: return false
        pauseARCoreSession()

        val recordingConfig = RecordingConfig(session)
            .setMp4DatasetFilePath(mp4FilePath)
            .setAutoStopOnPause(true)
        try {
            // Prepare the session for recording, but do not start recording yet.
            session.startRecording(recordingConfig)
        } catch (e: RecordingFailedException) {
            Log.e(TAG, "startRecording - Failed to prepare to start recording", e)
            return false
        }
        val canResume: Boolean = resumeARCoreSession()
        if (!canResume) return false

        // Correctness checking: check the ARCore session's RecordingState.
        val recordingStatus: RecordingStatus = session.getRecordingStatus()
        return recordingStatus == RecordingStatus.OK
    }

    public fun stopRecording(): Boolean {
        val session = activity.arCoreSessionHelper.session ?: return false
        try {
            session.stopRecording()
            Log.e(TAG, "STOPPED RECORDING")

        } catch (e: RecordingFailedException) {
            Log.e(TAG, "stopRecording - Failed to stop recording", e)
            return false
        }

        // Correctness checking: check if the session stopped recording.
        return session.recordingStatus === RecordingStatus.NONE
    }

    private fun createMp4File(): String? {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (!checkAndRequestStoragePermission()) {
                Log.i(
                    TAG, String.format(
                        "Didn't createMp4File. No storage permission, API Level = %d",
                        Build.VERSION.SDK_INT
                    )
                )
                return null
            }
        }

        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss")
        val mp4FileName = "arcore-" + dateFormat.format(Date()).toString() + ".mp4"
        val resolver: ContentResolver = root.context.contentResolver
        var videoCollection: Uri? = null
        videoCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            )
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        // Create a new Media file record.
        val newMp4FileDetails = ContentValues()
        newMp4FileDetails.put(MediaStore.Video.Media.DISPLAY_NAME, mp4FileName)
        newMp4FileDetails.put(MediaStore.Video.Media.MIME_TYPE, MP4_VIDEO_MIME_TYPE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // The Relative_Path column is only available since API Level 29.
            newMp4FileDetails.put(
                MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES
            )
        } else {
            // Use the Data column to set path for API Level <= 28.
            val mp4FileDir: File =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val absoluteMp4FilePath: String = File(mp4FileDir, mp4FileName).getAbsolutePath()
            newMp4FileDetails.put(MediaStore.Video.Media.DATA, absoluteMp4FilePath)
        }
        val newMp4FileUri: Uri? = resolver.insert(videoCollection, newMp4FileDetails)

        // Ensure that this file exists and can be written.
        if (newMp4FileUri == null) {
            Log.e(
                TAG,
                String.format(
                    "Failed to insert Video entity in MediaStore. API Level = %d",
                    Build.VERSION.SDK_INT
                )
            )
            return null
        }

        // This call ensures the file exist before we pass it to the ARCore API.
        if (!testFileWriteAccess(newMp4FileUri)) {
            return null
        }
        val filePath = getMediaFilePath(newMp4FileUri)
        Log.d(
            TAG,
            String.format("createMp4File = %s, API Level = %d", filePath, Build.VERSION.SDK_INT)
        )
        return filePath
    }

    public fun stopPlayingback(): Boolean {
        var session = activity.arCoreSessionHelper.session ?: return false

        // Correctness check, only stop playing back when the app is playing back.
        if (HelloGeoActivity().appState !== HelloGeoActivity.AppState.Playingback) return false
        pauseARCoreSession()

        // Close the current session and create a new session.
        session.close()
        try {
            activity.arCoreSessionHelper.session = Session(HelloGeoActivity().applicationContext)
        } catch (e: UnavailableArcoreNotInstalledException) {
            Log.e(TAG, "Error in return to Idle state. Cannot create new ARCore session", e)
            return false
        } catch (e: UnavailableApkTooOldException) {
            Log.e(TAG, "Error in return to Idle state. Cannot create new ARCore session", e)
            return false
        } catch (e: UnavailableSdkTooOldException) {
            Log.e(TAG, "Error in return to Idle state. Cannot create new ARCore session", e)
            return false
        } catch (e: UnavailableDeviceNotCompatibleException) {
            Log.e(TAG, "Error in return to Idle state. Cannot create new ARCore session", e)
            return false
        }

        val canResume = resumeARCoreSession()
        if (!canResume) return false

        // A new session will not have a camera texture name.
        // Manually set hasSetTextureNames to false to trigger a reset.
        //    hasSetTextureNames = false

        // Reset appState to Idle, and update the "Record" and "Playback" buttons.
        activity.appState = HelloGeoActivity.AppState.Idle
        activity.updateRecordButton()
        activity.updatePlaybackButton()
        return true
    }


    // Test if the file represented by the content Uri can be open with write access.
    private fun testFileWriteAccess(contentUri: Uri): Boolean {
        try {
            root.context.contentResolver.openOutputStream(contentUri).use { mp4File ->
                Log.d(
                    TAG,
                    java.lang.String.format(
                        "Success in testFileWriteAccess %s",
                        contentUri.toString()
                    )
                )
                return true
            }
        } catch (e: FileNotFoundException) {
            Log.e(
                TAG,
                java.lang.String.format(
                    "FileNotFoundException in testFileWriteAccess %s",
                    contentUri.toString()
                ),
                e
            )
        } catch (e: IOException) {
            Log.e(
                TAG,
                java.lang.String.format(
                    "IOException in testFileWriteAccess %s",
                    contentUri.toString()
                ),
                e
            )
        }
        return false
    }

    private val REQUEST_WRITE_EXTERNAL_STORAGE = 1
    fun checkAndRequestStoragePermission(): Boolean {
        if (ContextCompat.checkSelfPermission(
                root.context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                root.context as Activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_WRITE_EXTERNAL_STORAGE
            )
            return false
        }
        return true
    }

    // Query the Media.DATA column to get file path from MediaStore content:// Uri
    private fun getMediaFilePath(mediaStoreUri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val loader = CursorLoader(root.context, mediaStoreUri, projection, null, null, null)
        val cursor: Cursor? = loader.loadInBackground()
        cursor?.moveToFirst()
        val data_column_index: Int? = cursor?.getColumnIndexOrThrow(projection[0])
        val data_result: String? = data_column_index?.let { cursor?.getString(it) }
        if (cursor != null) {
            cursor.close()
        }
        return data_result
    }

    override fun onResume(owner: LifecycleOwner) {
        surfaceView.onResume()
    }

    override fun onPause(owner: LifecycleOwner) {
        surfaceView.onPause()
    }
}
