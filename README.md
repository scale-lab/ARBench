# MARAbenchmark
Benchmark for Mobile Augmented Reality Applications. For citations, please use this information:\
S. Chetoui, R. Shah, S. Abdelaziz, A. Golas, F. Hijaz and S. Reda
[ARBench: Augmented Reality Benchmark For Mobile Devices](https://ieeexplore.ieee.org/abstract/document/9804625/)
IEEE Symposium on Performance Analysis of Systems and Software (ISPASS), 2022.

## Getting Started with ARBench

### Prerequisites
 * An ARCore compatible device running [Google Play Services for AR](https://play.google.com/store/apps/details?id=com.google.ar.core) (ARCore) 1.24 or later
 * Android Studio 4.1 or later
 * Download the app assets using the provided script:
```python download_assets.py```

### Building and Running
Most apps have their own README with more detailed instructions on how to run and configure them. These instructions contain the barebone steps to run the apps.
1. Open the app you would like to run with Android Studio
2. Choose a target device to run the app on
3. Click on Run

More info: https://developer.android.com/studio/run/

## AR Apps
The benchmark app will playback recordings of the AR apps below and then output the results at the end. Instructions on how to configure most of the apps can be found in [ar-apps](https://github.com/scale-lab/ARBench/tree/main/ar-apps) folder

### Augmented Faces
Overlays 3D models and textures on a face.

### [Augmented Image](https://github.com/scale-lab/ARBench/tree/main/ar-apps/augmented_image)
Detects and augments 2D images in the scene using ARCore.

### [Augmented Object Recognition](https://github.com/scale-lab/ARBench/tree/main/ar-apps/augmented_object_recognition)
Uses machine learning to identify objects in the scene then uses ARCore to label each object.

### Augmented Object Generation
Allows for placement and manipulation of 3D models on detected AR plane surfaces.

### [Camera Translator](https://github.com/scale-lab/ARBench/tree/main/ar-apps/camera_translator)
Detects text from the live camera feed when tapped then translates the text to Spanish and displays the translated text over the screen.

### [Geospatial](https://github.com/scale-lab/ARBench/tree/main/ar-apps/geospatial)
Uses the ARCore Geospatial API as well as the device's camera and onboard GPS sensors to place location based AR anchors.