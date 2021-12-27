/*
 * Copyright 2020 Google LLC
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
package benchmark.common.samplerender;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;

import java.nio.IntBuffer;

import android.opengl.EGL14;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGL;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

import benchmark.common.samplerender.Framebuffer;
import benchmark.common.samplerender.GLError;
import benchmark.common.samplerender.Mesh;
import benchmark.common.samplerender.Shader;

/** Renders frames offscreen as frequently as possible. */
public class OffscreenRender extends SampleRender {
  private static final String TAG = OffscreenRender.class.getSimpleName();

  private Renderer renderer;
  private Thread renderingThread;

  // EGL Context Information
  EGL10 mEGL;
  EGLDisplay mEGLDisplay;
  EGLConfig mEGLConfig;
  EGLContext mEGLContext;
  EGLSurface mEGLSurface;
  GL10 mGL;

  private static int EGL_OPENGL_ES2_BIT = 4;
  private static int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

  private boolean running = true;

  /**
   * Constructs and renders to an offscreen EGL context.
   *
   * @param renderer Renderer implementation to receive callbacks
   * @param assetManager AssetManager for loading Android resources
   */
  public OffscreenRender(SurfaceView surfaceView, Renderer renderer, AssetManager assetManager) {
    super(assetManager);

    renderingThread = new Thread() {
      public void run() {
        setupEGL(surfaceView.getHolder());
        GLES30.glEnable(GLES30.GL_BLEND);
        GLError.maybeThrowGLException("Failed to enable blending", "glEnable");
        renderer.onSurfaceCreated(OffscreenRender.this);
        renderer.onSurfaceChanged(OffscreenRender.this, viewportWidth, viewportHeight);
        loop();
        shutdownEGL();
      }
    };

    viewportWidth = surfaceView.getWidth();
    viewportHeight = surfaceView.getHeight();

    this.renderer = renderer;

    renderingThread.start();
  }

  private void setupEGL(SurfaceHolder holder) {
    int[] version = new int[2];
    int[] confAttr = new int[] {
            EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_ALPHA_SIZE, 8,
            EGL10.EGL_DEPTH_SIZE, 16,
            EGL10.EGL_NONE
    };
    int[] ctxAttr = new int[] {
            EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL10.EGL_NONE
    };
    int[] surfaceAttr = new int[] {
            EGL10.EGL_WIDTH, viewportWidth,
            EGL10.EGL_HEIGHT, viewportHeight,
            EGL10.EGL_NONE
    };
    mEGL = (EGL10) EGLContext.getEGL();
    mEGLDisplay = mEGL.eglGetDisplay(mEGL.EGL_DEFAULT_DISPLAY);
    mEGL.eglInitialize(mEGLDisplay, version);
    Log.i(TAG, "EGL init with version " + version[0] + "." + version[1]);

    EGLConfig[] config = new EGLConfig[1];
    int[] num_config = new int[1];
    mEGL.eglChooseConfig(mEGLDisplay, confAttr, config, 1, num_config);
    if (num_config[0] <= 0) {
      throw new IllegalArgumentException("No matching EGL configs");
    }
    mEGLConfig = config[0];

    mEGLContext = mEGL.eglCreateContext(mEGLDisplay, mEGLConfig, EGL10.EGL_NO_CONTEXT, ctxAttr);

//    mEGLSurface = mEGL.eglCreatePbufferSurface(mEGLDisplay, mEGLConfig, surfaceAttr);
    mEGLSurface = mEGL.eglCreateWindowSurface(mEGLDisplay, mEGLConfig, holder, null);
    mEGL.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext);

    mGL = (GL10) mEGLContext.getGL();
  }

  private void loop() {
    clear(null,0f, 0f, 0f, 1f);
    long lastPreviewFrameTime = System.currentTimeMillis();
    while (running) {
        renderer.onDrawFrame(this);
        long currentFrameTime = System.currentTimeMillis();
        // Display onscreen preview at approx 30fps
        if (currentFrameTime - lastPreviewFrameTime > 33) {
          mEGL.eglSwapBuffers(mEGLDisplay, mEGLSurface);
          lastPreviewFrameTime = currentFrameTime;
        }
    }
  }

  public int getViewportWidth() { return viewportWidth; }
  public int getViewportHeight() { return viewportHeight; }

  public void stop() {
    running = false;
  }

  private void shutdownEGL() {
    mEGL.eglMakeCurrent(mEGLDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
    mEGL.eglDestroyContext(mEGLDisplay, mEGLContext);
    mEGL.eglDestroySurface(mEGLDisplay, mEGLSurface);
    mEGL.eglTerminate(mEGLDisplay);
  }
}
