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
