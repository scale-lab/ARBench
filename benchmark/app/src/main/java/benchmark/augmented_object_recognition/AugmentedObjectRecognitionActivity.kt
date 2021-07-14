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

package benchmark.augmented_object_recognition;

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import benchmark.benchmark.BenchmarkActivity
import benchmark.common.helpers.FullScreenHelper
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.exceptions.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream


class AugmentedObjectRecognitionActivity : AppCompatActivity() {
  val TAG = "AugmentedObjectActivity"
  lateinit var arCoreSessionHelper: ARCoreSessionLifecycleHelper

  lateinit var renderer: AppRenderer
  lateinit var viewRecognition: AugmentedObjectRecognitionActivityView

  private val MP4_VIDEO_MIME_TYPE = "video/mp4"
  private val REQUEST_MP4_SELECTOR = 1

  var fileName: String? = null
  var currentPhase = 1

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
    viewRecognition = AugmentedObjectRecognitionActivityView(this, renderer)
    setContentView(viewRecognition.root)
    renderer.bindView(viewRecognition)
    arCoreSessionHelper.bindView(viewRecognition)
    lifecycle.addObserver(viewRecognition)

    val intent = intent
    val activityNumber = intent.getIntExtra(BenchmarkActivity.ACTIVITY_NUMBER, 0)
    fileName = BenchmarkActivity.ACTIVITY_RECORDINGS[activityNumber].recordingFileName
    val f = File(getExternalFilesDir(null).toString() + "/" + fileName)
    if (!f.exists()) try {
      val `is`: InputStream = assets.open("recordings/$fileName")
      var len: Int
      val buffer = ByteArray(1024)
      val fos = FileOutputStream(f)
      while (`is`.read(buffer).also { len = it } > 0) {
        fos.write(buffer, 0, len)
      }
      `is`.close()
      fos.close()
    } catch (e: Exception) {
      throw RuntimeException(e)
    }
  }

  enum class AppState {
    Idle, Recording, Playingback
  }

  // Tracks app's specific state changes.
  var appState = AppState.Idle
  var requestingPlayback : Boolean = false
  var playbackFilePath : String?  = null

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