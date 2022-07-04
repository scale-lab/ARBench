# MARAbenchmark
Benchmark for Mobile Augmented Reality Applications. For citations, please use this information:\
S. Chetoui, R. Shah, S. Abdelaziz, A. Golas, F. Hijaz and S. Reda
[ARBench: Augmented Reality Benchmark For Mobile Devices](https://ieeexplore.ieee.org/abstract/document/9804625/)
IEEE Symposium on Performance Analysis of Systems and Software (ISPASS), 2022.

## Getting Started

### Prerequisites
 * An ARCore compatible device running [Google Play Services for AR](https://play.google.com/store/apps/details?id=com.google.ar.core) (ARCore) 1.24 or later
 * Android Studio 4.1 or later

### Building and Running
1. Open the app you would like to run with Android Studio
2. Choose a target device to run the app on
3. Click on Run

More info: https://developer.android.com/studio/run/

## AR Apps
The benchmark app will playback recordings of the AR apps below and then output the results at the end.

### Augmented Faces
Overlays 3D models and textures on a face.

### Augmented Image
Detects and augments 2D images in the scene using ARCore.

### Augmented Object Recognition
Uses machine learning to identify objects in the scene then uses ARCore to label each object.

### Augmented Object Generation
Allows for placement and manipulation of 3D models on detected AR plane surfaces.

### Camera Translator
Detects text from the live camera feed when tapped then translates the text to Spanish and displays the translated text over the screen.
