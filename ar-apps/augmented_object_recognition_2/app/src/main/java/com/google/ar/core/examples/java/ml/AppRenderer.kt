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

import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.*
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper
import com.google.ar.core.examples.java.common.samplerender.*
import com.google.ar.core.examples.java.common.samplerender.arcore.BackgroundRenderer
import com.google.ar.core.examples.java.common.samplerender.arcore.PlaneRenderer
import com.google.ar.core.examples.java.common.samplerender.arcore.SpecularCubemapFilter
import com.google.ar.core.examples.java.ml.classification.DetectedObjectResult
import com.google.ar.core.examples.java.ml.classification.GoogleCloudVisionDetector
import com.google.ar.core.examples.java.ml.classification.MLKitObjectDetector
import com.google.ar.core.examples.java.ml.classification.ObjectDetector
import com.google.ar.core.examples.java.ml.render.LabelRender
import com.google.ar.core.examples.java.ml.render.PointCloudRender
import com.google.ar.core.exceptions.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.util.*

/**
 * Renders the HelloAR application into using our example Renderer.
 */
class AppRenderer(val activity: MainActivity) : DefaultLifecycleObserver, SampleRender.Renderer,
    CoroutineScope by MainScope() {
    companion object {
        val TAG = "HelloArRenderer"
    }

    lateinit var view: MainActivityView

    val displayRotationHelper = DisplayRotationHelper(activity)
    lateinit var backgroundRenderer: BackgroundRenderer
    val pointCloudRender = PointCloudRender()
    val labelRenderer = LabelRender()
    private var planeRenderer: PlaneRenderer? = null
    private var virtualSceneFramebuffer: Framebuffer? = null

    private val Z_NEAR = 0.1f
    private val Z_FAR = 100f

    private var pointCloudVertexBuffer: VertexBuffer? = null
    private var pointCloudMesh: Mesh? = null
    private var pointCloudShader: Shader? = null

    val arLabeledAnchors = Collections.synchronizedList(mutableListOf<ARLabeledAnchor>())
    var scanButtonWasPressed = false

    val mlKitAnalyzer = MLKitObjectDetector(activity)
    val gcpAnalyzer = GoogleCloudVisionDetector(activity)

    private val CUBEMAP_RESOLUTION = 16
    private val CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES = 32

    var currentAnalyzer: ObjectDetector = gcpAnalyzer

    private var dfgTexture: Texture? = null
    private var cubemapFilter: SpecularCubemapFilter? = null

    private var virtualObjectMesh: Mesh? = null
    private var virtualObjectShader: Shader? = null
    private val anchors = ArrayList<Anchor>()

    private var lastPointCloudTimestamp: Long = 0

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val modelViewMatrix = FloatArray(16) // view x model
    private val viewProjectionMatrix = FloatArray(16)
    private val modelViewProjectionMatrix = FloatArray(16) // projection x view x model
    private val sphericalHarmonicsCoefficients = FloatArray(9 * 3)
    private val viewInverseMatrix = FloatArray(16)
    private val worldLightDirection = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f)
    private val viewLightDirection = FloatArray(4) // view x world light direction

    // See the definition of updateSphericalHarmonicsCoefficients for an explanation of these
    // constants.
    private val sphericalHarmonicFactors = floatArrayOf(
        0.282095f,
        -0.325735f,
        0.325735f,
        -0.325735f,
        0.273137f,
        -0.273137f,
        0.078848f,
        -0.273137f,
        0.136569f
    )


    override fun onResume(owner: LifecycleOwner) {
        displayRotationHelper.onResume()
    }

    override fun onPause(owner: LifecycleOwner) {
        displayRotationHelper.onPause()
    }

    fun bindView(view: MainActivityView) {
        this.view = view

        view.scanButton.setOnClickListener {
            // frame.acquireCameraImage is dependent on an ARCore Frame, which is only available in onDrawFrame.
            // Use a boolean and check its state in onDrawFrame to interact with the camera image.
            scanButtonWasPressed = true
            view.setScanningActive(true)
            hideSnackbar()
        }

        view.useCloudMlSwitch.setOnCheckedChangeListener { _, isChecked ->
            currentAnalyzer = if (isChecked) gcpAnalyzer else mlKitAnalyzer
        }

        val gcpConfigured = gcpAnalyzer.credentials != null
        view.useCloudMlSwitch.isChecked = gcpConfigured
        view.useCloudMlSwitch.isEnabled = gcpConfigured
        currentAnalyzer = if (gcpConfigured) gcpAnalyzer else mlKitAnalyzer

        if (!gcpConfigured) {
            //showSnackbar("Google Cloud Vision isn't configured (see README). The Cloud ML switch will be disabled.")
        }

        view.resetButton.setOnClickListener {
            arLabeledAnchors.clear()
            view.resetButton.isEnabled = false
            hideSnackbar()
        }
    }

    override fun onSurfaceCreated(render: SampleRender) {
        planeRenderer = PlaneRenderer(render)
        backgroundRenderer = BackgroundRenderer(render).apply {
            setUseDepthVisualization(render, false)
        }
        pointCloudRender.onSurfaceCreated(render)
        labelRenderer.onSurfaceCreated(render)
        virtualSceneFramebuffer = Framebuffer(render, 1, 1);

        cubemapFilter = SpecularCubemapFilter(
            render,
            CUBEMAP_RESOLUTION,
            CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES
        )

        // Load DFG lookup table for environmental lighting
        // Load DFG lookup table for environmental lighting
        dfgTexture = Texture(
            render,
            Texture.Target.TEXTURE_2D,
            Texture.WrapMode.CLAMP_TO_EDGE,  /*useMipmaps=*/
            false
        )
        // The dfg.raw file is a raw half-float texture with two channels.
        // The dfg.raw file is a raw half-float texture with two channels.
        val dfgResolution = 64
        val dfgChannels = 2
        val halfFloatSize = 2

        val buffer =
            ByteBuffer.allocateDirect(dfgResolution * dfgResolution * dfgChannels * halfFloatSize)
        view.activity.assets.open("models/dfg.raw").use { `is` -> `is`.read(buffer.array()) }
        // SampleRender abstraction leaks here.
        // SampleRender abstraction leaks here.
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dfgTexture!!.textureId)
        GLError.maybeThrowGLException("Failed to bind DFG texture", "glBindTexture")
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,  /*level=*/
            0,
            GLES30.GL_RG16F,  /*width=*/
            dfgResolution,  /*height=*/
            dfgResolution,  /*border=*/
            0,
            GLES30.GL_RG,
            GLES30.GL_HALF_FLOAT,
            buffer
        )
        GLError.maybeThrowGLException("Failed to populate DFG texture", "glTexImage2D")

        // Point cloud
        pointCloudShader = Shader.createFromAssets(
            render, "shaders/point_cloud.vert", "shaders/point_cloud.frag",  /*defines=*/null
        )
            .setVec4(
                "u_Color", floatArrayOf(31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f)
            )
            .setFloat("u_PointSize", 5.0f)
        // four entries per vertex: X, Y, Z, confidence
        // four entries per vertex: X, Y, Z, confidence
        pointCloudVertexBuffer =
            VertexBuffer(render,  /*numberOfEntriesPerVertex=*/4,  /*entries=*/null)
        val pointCloudVertexBuffers = arrayOf<VertexBuffer>(pointCloudVertexBuffer!!)
        pointCloudMesh = Mesh(
            render, Mesh.PrimitiveMode.POINTS,  /*indexBuffer=*/null, pointCloudVertexBuffers
        )


        // Virtual object to render (ARCore pawn)
        val virtualObjectAlbedoTexture = Texture.createFromAsset(
            render,
            "models/pawn_albedo.png",
            Texture.WrapMode.CLAMP_TO_EDGE,
            Texture.ColorFormat.SRGB
        )
        val virtualObjectPbrTexture = Texture.createFromAsset(
            render,
            "models/pawn_roughness_metallic_ao.png",
            Texture.WrapMode.CLAMP_TO_EDGE,
            Texture.ColorFormat.LINEAR
        )
        virtualObjectMesh = Mesh.createFromAsset(render, "models/pawn.obj")
        virtualObjectShader = Shader.createFromAssets(
            render,
            "shaders/environmental_hdr.vert",
            "shaders/environmental_hdr.frag",  /*defines=*/
            object : HashMap<String?, String?>() {
                init {
                    put(
                        "NUMBER_OF_MIPMAP_LEVELS",
                        Integer.toString(cubemapFilter!!.numberOfMipmapLevels)
                    )
                }
            })
            .setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture)
            .setTexture("u_RoughnessMetallicAmbientOcclusionTexture", virtualObjectPbrTexture)
            .setTexture("u_Cubemap", cubemapFilter!!.filteredCubemapTexture)
            .setTexture("u_DfgTexture", dfgTexture)
    }

    override fun onSurfaceChanged(render: SampleRender?, width: Int, height: Int) {
        displayRotationHelper.onSurfaceChanged(width, height)
        virtualSceneFramebuffer!!.resize(width, height)
    }

    var objectResults: List<DetectedObjectResult>? = null

    override fun onDrawFrame(render: SampleRender) {
        val session = activity.arCoreSessionHelper.sessionCache ?: return
        session.setCameraTextureNames(intArrayOf(backgroundRenderer.cameraColorTexture.textureId))

        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        displayRotationHelper.updateSessionIfNeeded(session)

        if (activity.appState == MainActivity.AppState.Playingback
            && session.playbackStatus == PlaybackStatus.FINISHED
        ) {
            activity.runOnUiThread(activity.view::stopPlayingback)
            return
        }

        val frame = try {
            session.update()
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "Camera not available during onDrawFrame", e)
            showSnackbar("Camera not available. Try restarting the app.")
            return
        }

        // Get camera and projection matrices.
        val camera = frame.camera
        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.01f, 100.0f)
        Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)


        // Update BackgroundRenderer state to match the depth settings.
        backgroundRenderer.setUseDepthVisualization(
            render, false
        )
        backgroundRenderer.setUseOcclusion(render, false)
        // BackgroundRenderer.updateDisplayGeometry must be called every frame to update the coordinates
        // used to draw the background camera image.
        backgroundRenderer.updateDisplayGeometry(frame)

//        // Handle one tap per frame.
//        handleTap(frame, camera)

        // -- Draw background
        if (frame.timestamp != 0L) {
            // Suppress rendering if the camera did not produce the first frame yet. This is to avoid
            // drawing possible leftover data from previous sessions if the texture is reused.
            backgroundRenderer.drawBackground(render)
        }

        frame.acquirePointCloud().use { pointCloud ->
            if (pointCloud.timestamp > lastPointCloudTimestamp) {
                pointCloudVertexBuffer!!.set(pointCloud.points)
                lastPointCloudTimestamp = pointCloud.timestamp
            }
            Matrix.multiplyMM(
                modelViewProjectionMatrix,
                0,
                projectionMatrix,
                0,
                viewMatrix,
                0
            )
            pointCloudShader!!.setMat4("u_ModelViewProjection", modelViewProjectionMatrix)
            render.draw(pointCloudMesh, pointCloudShader)
        }

        // Handle tracking failures.
        if (camera.trackingState != TrackingState.TRACKING) {
            return
        }

        // Draw point cloud.
        frame.acquirePointCloud().use { pointCloud ->
            pointCloudRender.drawPointCloud(render, pointCloud, viewProjectionMatrix)
        }

        // Visualize planes.
        planeRenderer!!.drawPlanes(
            render,
            session.getAllTrackables(Plane::class.java),
            camera.displayOrientedPose,
            projectionMatrix
        )

        // Update lighting parameters in the shader

        // -- Draw occluded virtual objects

        // Update lighting parameters in the shader
        updateLightEstimation(frame.lightEstimate, viewMatrix)

        // Visualize anchors created by touch.
        render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f)

        // Frame.acquireCameraImage must be used on the GL thread.
        // Check if the button was pressed last frame to start processing the camera image.
        if (session.playbackStatus == PlaybackStatus.OK) {
            if (!frame.getUpdatedTrackData(view.SCAN_TRACK_ID).isEmpty()) {
                scanButtonWasPressed = true
            }
        }
        if (scanButtonWasPressed) {
            scanButtonWasPressed = false
            val cameraImage = frame.tryAcquireCameraImage()
            if (cameraImage != null) {
                // Call our ML model on an IO thread.
                launch(Dispatchers.IO) {
                    val cameraId = session.cameraConfig.cameraId
                    val imageRotation =
                        displayRotationHelper.getCameraSensorToDisplayRotation(cameraId)
                    objectResults = currentAnalyzer.analyze(cameraImage, imageRotation)
                    cameraImage.close()
                }
            }
            if (session.recordingStatus == RecordingStatus.OK) {
                val payload = ByteBuffer.allocate(1)
                payload.put(1)
                try {
                    frame.recordTrackData(view.SCAN_TRACK_ID, payload)
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "Error in recording scan input into external data track.", e)
                }
            }
        }

        /** If results were completed this frame, create [Anchor]s from model results. */
        val objects = objectResults
        if (objects != null) {
            objectResults = null
            Log.i(TAG, "$currentAnalyzer got objects: $objects")
            val anchors = objects.mapNotNull { obj ->
                val (atX, atY) = obj.centerCoordinate
                val anchor =
                    createAnchor(atX.toFloat(), atY.toFloat(), frame) ?: return@mapNotNull null
                Log.i(TAG, "Created anchor ${anchor.pose} from hit test")
                ARLabeledAnchor(anchor, obj.label)
            }
            arLabeledAnchors.addAll(anchors)
            view.post {
                view.resetButton.isEnabled = arLabeledAnchors.isNotEmpty()
                view.setScanningActive(false)
//        when {
//          objects.isEmpty() && currentAnalyzer == mlKitAnalyzer && !mlKitAnalyzer.hasCustomModel() ->
//            showSnackbar("Default ML Kit classification model returned no results. " +
//              "For better classification performance, see the README to configure a custom model.")
//          objects.isEmpty() ->
//            showSnackbar("Classification model returned no results.")
//          anchors.size != objects.size ->
//            showSnackbar("Objects were classified, but could not be attached to an anchor. " +
//              "Try moving your device around to obtain a better understanding of the environment.")
//        }
            }
        }

        // Draw labels at their anchor position.
        for (arDetectedObject in arLabeledAnchors) {
            //println(arDetectedObject.label);
            val anchor = arDetectedObject.anchor
            if (anchor.trackingState != TrackingState.TRACKING) continue

            // Get the current pose of an Anchor in world space. The Anchor pose is updated
            // during calls to session.update() as ARCore refines its estimate of the world.
            anchor.pose.toMatrix(modelMatrix, 0)

            // Calculate model/view/projection matrices
            Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

            // Update shader properties and draw
            virtualObjectShader!!.setMat4("u_ModelView", modelViewMatrix)
            virtualObjectShader!!.setMat4("u_ModelViewProjection", modelViewProjectionMatrix)
            render.draw(virtualObjectMesh, virtualObjectShader, virtualSceneFramebuffer)

            labelRenderer.draw(
                render,
                viewProjectionMatrix,
                anchor.pose,
                camera.pose,
                arDetectedObject.label,
                virtualSceneFramebuffer
            )
        }

        // Compose the virtual scene with the background.
        backgroundRenderer.drawVirtualScene(
            render,
            virtualSceneFramebuffer,
            Z_NEAR,
            Z_FAR
        )
    }

    /** Update state based on the current frame's light estimation.  */
    private fun updateLightEstimation(lightEstimate: LightEstimate, viewMatrix: FloatArray) {
        if (lightEstimate.state != LightEstimate.State.VALID) {
            virtualObjectShader!!.setBool("u_LightEstimateIsValid", false)
            return
        }
        virtualObjectShader!!.setBool("u_LightEstimateIsValid", true)
        Matrix.invertM(viewInverseMatrix, 0, viewMatrix, 0)
        virtualObjectShader!!.setMat4("u_ViewInverse", viewInverseMatrix)
        updateMainLight(
            lightEstimate.environmentalHdrMainLightDirection,
            lightEstimate.environmentalHdrMainLightIntensity,
            viewMatrix
        )
        updateSphericalHarmonicsCoefficients(
            lightEstimate.environmentalHdrAmbientSphericalHarmonics
        )
        cubemapFilter!!.update(lightEstimate.acquireEnvironmentalHdrCubeMap())
    }

    private fun updateMainLight(
        direction: FloatArray,
        intensity: FloatArray,
        viewMatrix: FloatArray
    ) {
        // We need the direction in a vec4 with 0.0 as the final component to transform it to view space
        worldLightDirection[0] = direction[0]
        worldLightDirection[1] = direction[1]
        worldLightDirection[2] = direction[2]
        Matrix.multiplyMV(viewLightDirection, 0, viewMatrix, 0, worldLightDirection, 0)
        virtualObjectShader!!.setVec4("u_ViewLightDirection", viewLightDirection)
        virtualObjectShader!!.setVec3("u_LightIntensity", intensity)
    }

    private fun updateSphericalHarmonicsCoefficients(coefficients: FloatArray) {
        // Pre-multiply the spherical harmonics coefficients before passing them to the shader. The
        // constants in sphericalHarmonicFactors were derived from three terms:
        //
        // 1. The normalized spherical harmonics basis functions (y_lm)
        //
        // 2. The lambertian diffuse BRDF factor (1/pi)
        //
        // 3. A <cos> convolution. This is done to so that the resulting function outputs the irradiance
        // of all incoming light over a hemisphere for a given surface normal, which is what the shader
        // (environmental_hdr.frag) expects.
        //
        // You can read more details about the math here:
        // https://google.github.io/filament/Filament.html#annex/sphericalharmonics
        require(coefficients.size == 9 * 3) { "The given coefficients array must be of length 27 (3 components per 9 coefficients" }

        // Apply each factor to every component of each coefficient
        for (i in 0 until 9 * 3) {
            sphericalHarmonicsCoefficients[i] =
                coefficients[i] * sphericalHarmonicFactors[i / 3]
        }
        virtualObjectShader!!.setVec3Array(
            "u_SphericalHarmonicsCoefficients", sphericalHarmonicsCoefficients
        )
    }


    /**
     * Utility method for [Frame.acquireCameraImage] that maps [NotYetAvailableException] to `null`.
     */
    fun Frame.tryAcquireCameraImage() = try {
        acquireCameraImage()
    } catch (e: NotYetAvailableException) {
        null
    } catch (e: Throwable) {
        throw e
    }

    private fun showSnackbar(message: String): Unit =
        activity.view.snackbarHelper.showError(activity, message)

    private fun hideSnackbar() = activity.view.snackbarHelper.hide(activity)

    /**
     * Temporary arrays to prevent allocations in [createAnchor].
     */
    private val convertFloats = FloatArray(4)
    private val convertFloatsOut = FloatArray(4)

    /** Create an anchor using (x, y) coordinates in the [Coordinates2d.IMAGE_PIXELS] coordinate space. */
    fun createAnchor(xImage: Float, yImage: Float, frame: Frame): Anchor? {
        // IMAGE_PIXELS -> VIEW
        convertFloats[0] = xImage
        convertFloats[1] = yImage
        frame.transformCoordinates2d(
            Coordinates2d.IMAGE_PIXELS,
            convertFloats,
            Coordinates2d.VIEW,
            convertFloatsOut
        )

        // Conduct a hit test using the VIEW coordinates
        val hits = frame.hitTest(convertFloatsOut[0], convertFloatsOut[1])
        val result = hits.getOrNull(0) ?: return null
        return result.trackable.createAnchor(result.hitPose)
    }

    var fileNumber = 1
    var fileName = "recording-$fileNumber"

    @Throws(
        CameraNotAvailableException::class,
        PlaybackFailedException::class,
        UnavailableSdkTooOldException::class,
        UnavailableDeviceNotCompatibleException::class,
        UnavailableArcoreNotInstalledException::class,
        UnavailableApkTooOldException::class
    )
    fun onPlayback() {
        activity.arCoreSessionHelper.onPause(activity)
        activity.arCoreSessionHelper.onResume(activity)
        val session = activity.arCoreSessionHelper.sessionCache ?: return;
        val destination: String =
            File(activity.getExternalFilesDir(null), fileName + ".mp4").getAbsolutePath()
        // Switch to a different dataset.
        displayRotationHelper.onPause()
        view.surfaceView.onPause()
        session.pause()// Pause the playback of the first dataset.
        // Specify a different dataset to use.
        session.setPlaybackDataset(destination)
        session.resume()
        view.surfaceView.onResume()
        displayRotationHelper.onResume()
        // Start playback from the beginning of the new dataset.
    }
}

data class ARLabeledAnchor(val anchor: Anchor, val label: String)