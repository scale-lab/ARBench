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
package com.google.ar.core.codelabs.hellogeospatial

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.codelabs.hellogeospatial.helpers.ARCoreSessionLifecycleHelper
import com.google.ar.core.codelabs.hellogeospatial.helpers.GeoPermissionsHelper
import com.google.ar.core.codelabs.hellogeospatial.helpers.HelloGeoView
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper
import com.google.ar.core.examples.java.common.samplerender.SampleRender
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

class HelloGeoActivity : AppCompatActivity() {
  companion object {
    private const val TAG = "HelloGeoActivity"
  }

  lateinit var arCoreSessionHelper: ARCoreSessionLifecycleHelper
  lateinit var view: HelloGeoView
  lateinit var renderer: HelloGeoRenderer

  private val MP4_VIDEO_MIME_TYPE = "video/mp4"
  private val REQUEST_MP4_SELECTOR = 1

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Setup ARCore session lifecycle helper and configuration.
    arCoreSessionHelper = ARCoreSessionLifecycleHelper(this)
    // If Session creation or Session.resume() fails, display a message and log detailed
    // information.
    arCoreSessionHelper.exceptionCallback =
      { exception ->
        val message =
          when (exception) {
            is UnavailableUserDeclinedInstallationException ->
              "Please install Google Play Services for AR"
            is UnavailableApkTooOldException -> "Please update ARCore"
            is UnavailableSdkTooOldException -> "Please update this app"
            is UnavailableDeviceNotCompatibleException -> "This device does not support AR"
            is CameraNotAvailableException -> "Camera not available. Try restarting the app."
            else -> "Failed to create AR session: $exception"
          }
        Log.e(TAG, "ARCore threw an exception", exception)
        view.snackbarHelper.showError(this, message)
      }

    // Configure session features.
    arCoreSessionHelper.beforeSessionResume = ::configureSession
    lifecycle.addObserver(arCoreSessionHelper)

    // Set up the Hello AR renderer.
    renderer = HelloGeoRenderer(this)
    lifecycle.addObserver(renderer)

    // Set up Hello AR UI.
    view = HelloGeoView(this)
    lifecycle.addObserver(view)
    setContentView(view.root)

    // Sets up an example renderer using our HelloGeoRenderer.
    SampleRender(view.surfaceView, renderer, assets)
  }

  // Configure the session, setting the desired options according to your usecase.
  fun configureSession(session: Session) {
    session.configure(
      session.config.apply {
        geospatialMode = Config.GeospatialMode.ENABLED
      }
    )
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    results: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, results)
    if (!GeoPermissionsHelper.hasGeoPermissions(this)) {
      // Use toast instead of snackbar here since the activity will exit.
      Toast.makeText(this, "Camera and location permissions are needed to run this application", Toast.LENGTH_LONG)
        .show()
      if (!GeoPermissionsHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".
        GeoPermissionsHelper.launchPermissionSettings(this)
      }
      finish()
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus)
  }

  enum class AppState {
    Idle, Recording, Playingback
  }

  // Tracks app's specific state changes.
  var appState = AppState.Idle
  var requestingPlayback : Boolean = false
  var playbackFilePath : String?  = null

  fun updateRecordButton() {
    val buttonView = findViewById<View>(R.id.record_button)
    val button = buttonView as Button
    when (appState) {
      AppState.Idle -> {
        button.text = "Record"
        button.visibility = View.VISIBLE
      }
      AppState.Recording -> {
        button.text = "Stop"
        button.visibility = View.VISIBLE
      }
      AppState.Playingback -> button.visibility = View.INVISIBLE
    }
  }

  fun updatePlaybackButton() {
    val buttonView = findViewById<View>(R.id.playback_button)
    val button = buttonView as Button
    when (appState) {
      AppState.Idle -> {
        button.text = "Playback"
        button.visibility = View.VISIBLE
      }
      AppState.Playingback -> {
        button.text = "Stop"
        button.visibility = View.VISIBLE
      }
      AppState.Recording -> button.visibility = View.INVISIBLE
    }
  }

  fun onClickRecord(button: View?) {
    Log.d(TAG, "onClickRecord")
    when (appState) {
      AppState.Idle -> {
        val hasStarted: Boolean = view.startRecording()
        if (hasStarted) appState = AppState.Recording
      }
      AppState.Recording -> {
        val hasStopped: Boolean = view.stopRecording()
        if (hasStopped) appState = AppState.Idle
      }
    }
    updateRecordButton()
    updatePlaybackButton()
  }

  fun onClickPlayback(button: View?) {
    Log.d(TAG, "onClickPlayback")
    when (appState) {
      AppState.Idle -> {
        val hasStarted: Boolean = selectFileToPlayback()
        Log.d(TAG, String.format("onClickPlayback start: selectFileToPlayback %b", hasStarted))
      }
      AppState.Playingback -> {
        val hasStopped: Boolean = view.stopPlayingback()
        Log.d(TAG, String.format("onClickPlayback stop: hasStopped %b", hasStopped))
      }
      else -> {
      }
    }

    // Update the UI for the "Record" and "Playback" buttons.
    updateRecordButton()
    updatePlaybackButton()
  }

  private fun selectFileToPlayback(): Boolean {
    // Start file selection from Movies directory.
    // Android 10 and above requires VOLUME_EXTERNAL_PRIMARY to write to MediaStore.
    requestingPlayback = true
    val videoCollection: Uri;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      videoCollection = MediaStore.Video.Media.getContentUri(
        MediaStore.VOLUME_EXTERNAL_PRIMARY
      )
    } else {
      videoCollection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    // Create an Intent to select a file.
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)

    // Add file filters such as the MIME type, the default directory and the file category.
    intent.type = MP4_VIDEO_MIME_TYPE // Only select *.mp4 files
    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, videoCollection) // Set default directory
    intent.addCategory(Intent.CATEGORY_OPENABLE) // Must be files that can be opened
    this.startActivityForResult(intent, REQUEST_MP4_SELECTOR)
    return true
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    // Check request status. Log an error if the selection fails.
    super.onActivityResult(requestCode, resultCode, data)
    if (resultCode != RESULT_OK || requestCode != REQUEST_MP4_SELECTOR) {
      Log.e(TAG,"onActivityResult select file failed")
      return
    }
    val mp4Uri = data?.data
    Log.d(TAG, String.format("onActivityResult result is %s", mp4Uri))

    // Copy to app internal storage to get a file path.
    val localFilePath: String = copyToInternalFilePath(mp4Uri)

    // Begin playback.
    playbackFilePath = localFilePath
    view.startPlayingback(playbackFilePath);
  }

  private fun copyToInternalFilePath(contentUri: Uri?): String {
    // Create a file path in the app's internal storage.
    val tempPlaybackFilePath = File(getExternalFilesDir(null), "temp-playback.mp4").absolutePath

    // Copy the binary content from contentUri to tempPlaybackFilePath.
    try {
      if (contentUri != null) {
        this.contentResolver.openInputStream(contentUri).use { inputStream ->
          FileOutputStream(tempPlaybackFilePath).use { tempOutputFileStream ->
            val buffer = ByteArray(1024 * 1024) // 1MB
            var bytesRead = inputStream!!.read(buffer)
            while (bytesRead != -1) {
              tempOutputFileStream.write(buffer, 0, bytesRead)
              bytesRead = inputStream.read(buffer)
            }
          }
        }
      }
    } catch (e: FileNotFoundException) {
      Log.e(TAG,"copyToInternalFilePath FileNotFoundException", e)
    } catch (e: IOException) {
      Log.e(TAG,"copyToInternalFilePath IOException", e)
    }

    // Return the absolute file path of the copied file.
    return tempPlaybackFilePath
  }

}
