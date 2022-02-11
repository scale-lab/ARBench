# Source Code

The source code for ARBench can be found in the folder java/benchmark. Details of the subdirectories and their contents follow:

- **augmented_faces**: Source code for Augmented Faces
  - AugmentedFaceRenderer.java: Interface used for rendering
  - AugmentedFacesActivity.java: Augmented Faces activity launched by main benchmark application
- **augmented_image**: Source code for Augmented Image
  - AugmentedImageRenderer.java: Interface used for rendering
  - Augmented Image activity launched by main benchmark application
- **augmented_object_generation**: Source code for Object Generation
  - AugmentedObjectGenerationActivity.java: Object Generation activity launched by main benchmark application
- **augmented_object_recognition**: Source code for Object Recognition
  - classification: Object detection and classification using Google ML Kit
  - render: Interfaces for rendering
  - ARCoreSessionLifecycle.kt: Lifecycle object for Object Recognition activity
  - AppRenderer.kt: Main rendering functions
  - AugmentedObjectRecognitionActivity.kt: Object Recognition activity launched by main benchmark application
  - AugmentedObjectRecognitionActivityView.kt: Android View associated with activity
  - YuvToRgbConverter.kt: Convert image format
- **benchmark**: Benchmark Application
  - ActivityRecording.java: Metadata for mp4 recordings
  - BenchmarkActivity.java: Main activity that launches other applications and displays results
  - CameraPreview.java: Run camera in background to emulate AR camera usage
- **camera_translator**: OCR and translation app (not currently included in benchmark)
- **common**: Common classes
  - **helpers**: ARCore helper classes
  - **rendering**: ARCore classes for rendering background, point clouds etc.
  - **samplerender**: Renderer attached to OpenGL context
    - SampleRender.java: Attached to GLSurfaceView for onscreen rendering
    - OffscreenRender.java: Creates EGL context for offscreen rendering
