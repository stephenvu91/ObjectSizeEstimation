# Object Size Estimation

## Getting Started

An Android application that estimates the size (height and width) of a target object
using the device’s camera by referencing a known-sized object within the camera frame. The
app utilizes machine learning for object detection.

## Environment Setup

-   Make sure you have JDK 11 installed and your JAVA_HOME configured.
-   Download and Install Android Studio.  [https://developer.android.com/studio/index.html](https://developer.android.com/studio/index.html)
-   Open Android Studio, install appropriate Android SDK and SDK Tools with the SDK Manager.
-   Setup your terminal environment, add  `ANDROID_HOME`  to your PATH.
    ```
    export ANDROID_HOME={YOUR_PATH}
    export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools
    ```

## Usage guide

-   Launch the app
-   Enable camera access permission if asked
-   Point the camera at any object to see its size

## Assumptions/Limitations

-   Assume camera fiscal point (POV) is always 0.87

## Enhancements

-   Detect object directly from ImageProxy instead of converting to Bitmap.
-   Get camera fiscal point correctly instead of using hardcoded value.
-   Object detection still does not work reliably on identifying object correctly so still need more investigation if it is because of the model or the input image. 
-   Add unit and UI tests and test coverage.
-   Setup buildkite or similar CI/CD for continuous integration.
-   Setup production environment such as shrink resources and proguard.
