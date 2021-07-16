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
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.core.examples.java.ml

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
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper
import com.google.ar.core.exceptions.*
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException


class MainActivity : AppCompatActivity() {
  val TAG = "MainActivity"
  lateinit var arCoreSessionHelper: ARCoreSessionLifecycleHelper

  lateinit var renderer: AppRenderer
  lateinit var view: MainActivityView

  private val MP4_VIDEO_MIME_TYPE = "video/mp4"
  private val REQUEST_MP4_SELECTOR = 1

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    arCoreSessionHelper = ARCoreSessionLifecycleHelper(this)
    // When session creation or session.resume fails, we display a message and log detailed information.
    arCoreSessionHelper.exceptionCallback = { exception ->
      val message = when (exception) {
        is UnavailableArcoreNotInstalledException,
        is UnavailableUserDeclinedInstallationException -> "Please install ARCore"
        is UnavailableApkTooOldException -> "Please update ARCore"
        is UnavailableSdkTooOldException -> "Please update this app"
        is UnavailableDeviceNotCompatibleException -> "This device does not support AR"
        is CameraNotAvailableException -> "Camera not available. Try restarting the app."
        else -> "Failed to create AR session: $exception"
      }
      Log.e(TAG, message, exception)
      Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    arCoreSessionHelper.beforeSessionResume = { session ->
      session.configure(
        session.config.apply {
          // To get the best image of the object in question, enable autofocus.
          focusMode = Config.FocusMode.AUTO
          if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            depthMode = Config.DepthMode.AUTOMATIC
          }
        }
      )

      val filter = CameraConfigFilter(session)
        .setFacingDirection(CameraConfig.FacingDirection.BACK)
      val configs = session.getSupportedCameraConfigs(filter)
      val sort = compareByDescending<CameraConfig> { it.imageSize.width }
        .thenByDescending { it.imageSize.height }
      session.setCameraConfig(configs.sortedWith(sort)[0])

      if (requestingPlayback) {
        while (playbackFilePath == null) {
          continue
        }
        session.setPlaybackDataset(playbackFilePath)
      }
    }
    lifecycle.addObserver(arCoreSessionHelper)

    renderer = AppRenderer(this)
    lifecycle.addObserver(renderer)
    view = MainActivityView(this, renderer)
    setContentView(view.root)
    renderer.bindView(view)
    lifecycle.addObserver(view)

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
    val videoCollection: Uri
    videoCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      MediaStore.Video.Media.getContentUri(
        MediaStore.VOLUME_EXTERNAL_PRIMARY
      )
    } else {
      MediaStore.Video.Media.EXTERNAL_CONTENT_URI
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


  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    arCoreSessionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus)
  }
}