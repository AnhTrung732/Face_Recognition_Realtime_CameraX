# Face_Recognition_Realtime_CameraX
- This is an Android app for face recognition using the FaceNet model. 
- The original code was written in Kotlin, but has been converted to Java. 
- Additionally, the original coroutine code has been replaced with threads. 
- MTCNN is used for face detection instead of Google ML Kit.
- Image analysis is directly processed in preview view rather than using image proxy.

## Installation

To install the app, follow these steps:

1. Clone or download the repository
2. Open the project in Android Studio
3. Build and run the app on an Android device or emulator

## Demo

https://user-images.githubusercontent.com/93305749/235473691-d339c9af-9045-4bfc-956e-dbd8adaa9017.mp4


## Credits

This app is based on the [FaceRecognition_With_FaceNet_Android](https://github.com/shubham0204/FaceRecognition_With_FaceNet_Android) repository by Shubham Gupta. MTCNN (https://github.com/ipazc/mtcnn) is used for face detection, which is a third-party library.


## Important Resources  

- [FaceNet: A Unified Embedding for Face Recognition and Clustering](https://arxiv.org/abs/1503.03832)
- [MTCNN](https://github.com/ipazc/mtcnn)) for face detection.  
- [TensorFlow Lite Android](https://www.tensorflow.org/lite)  
- [TensorFlow Lite Android Support Library](https://github.com/tensorflow/tensorflow/tree/master/tensorflow/lite/experimental/support/java)  
- [CameraX](https://developer.android.com/training/camerax)
