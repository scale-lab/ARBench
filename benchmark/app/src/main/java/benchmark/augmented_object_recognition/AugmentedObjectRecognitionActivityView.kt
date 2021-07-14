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

package benchmark.augmented_object_recognition

import android.opengl.GLSurfaceView
import android.util.Log
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import benchmark.benchmark.R
import benchmark.common.helpers.SnackbarHelper
import benchmark.common.samplerender.SampleRender
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.IOException
import java.util.*

/**
 * Wraps [R.layout.activity_augmented_object_recognition] and controls lifecycle operations for [GLSurfaceView].
 */
class AugmentedObjectRecognitionActivityView(val recognitionActivity: AugmentedObjectRecognitionActivity, renderer: AppRenderer) :
  DefaultLifecycleObserver {
  private val MP4_VIDEO_MIME_TYPE = "video/mp4"

  private val TAG = AugmentedObjectRecognitionActivityView::class.java.simpleName
  val root = View.inflate(recognitionActivity, R.layout.activity_augmented_object_recognition, null)
  val surfaceView = root.findViewById<GLSurfaceView>(R.id.surfaceview).apply {
    SampleRender(this, renderer, recognitionActivity.assets)
  }

//  val recordButton = root.findViewById<AppCompatButton>(R.id.record_button)
//  val playbackButton = root.findViewById<AppCompatButton>(R.id.playback_button)
  val snackbarHelper = SnackbarHelper().apply {
    setParentView(root.findViewById(R.id.coordinatorLayout))
    setMaxLines(6)
  }

  public val SCAN_TRACK_ID = UUID.fromString("53069eb5-21ef-4946-b71c-6ac4979216a6")
  private val SCAN_TRACK_MIME_TYPE = "application/recording-playback-scan"
  public val PHASE_TRACK_ID = UUID.fromString("53069eb5-21ef-4946-b71c-6ac4979216a7")
  private val PHASE_TRACK_MIME_TYPE = "application/recording-playback-phase"

  var logPath: String? = null
  var fpsLog: BufferedWriter? = null

  override fun onResume(owner: LifecycleOwner) {
    try {
      Log.d(
        recognitionActivity.TAG,
        "Logging FPS to " + recognitionActivity.getExternalFilesDir(null)?.getAbsolutePath() + "/fps.csv"
      )
      fpsLog =
        BufferedWriter(FileWriter(recognitionActivity.getExternalFilesDir(null)?.getAbsolutePath() + "/fps.csv"))
    } catch (e: IOException) {
      recognitionActivity.renderer.showSnackbar("Could not open file to log FPS")
    }
    surfaceView.onResume()
  }

  override fun onPause(owner: LifecycleOwner) {
    surfaceView.onPause()
  }

  fun post(action: Runnable) = root.post(action)
}