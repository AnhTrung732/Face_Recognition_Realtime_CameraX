package com.example.face_recognition_realtime_camerax;
import android.Manifest;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.LifecycleOwner;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.method.ScrollingMovementMethod;
import android.util.Pair;
import android.util.Size;
import android.view.View;
import android.view.WindowInsets;
import android.widget.TextView;
import android.widget.Toast;

import com.example.face_recognition_realtime_camerax.CustomImageView.CustomImageView;
import com.example.face_recognition_realtime_camerax.databinding.ActivityMainBinding;
import com.example.face_recognition_realtime_camerax.model.mobilefacenet.FaceNet;
import com.example.face_recognition_realtime_camerax.model.mobilefacenet.ModelInfo;
import com.example.face_recognition_realtime_camerax.model.mobilefacenet.Models;
import com.example.face_recognition_realtime_camerax.model.mtcnn.MTCNN;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private final boolean isSerializedDataStored = false;

    // Serialized data will be stored ( in app's private storage ) with this filename.
    private final String SERIALIZED_DATA_FILENAME = "image_data";

    // Shared Pref key to check if the data was stored.
    private final String SHARED_PREF_IS_DATA_STORED_KEY = "is_data_stored";

    private ActivityMainBinding activityMainBinding;

    private PreviewView previewView;
    private CustomImageView recognizeView;
    private FrameAnalyser frameAnalyser;
    private FaceNet faceNetModel;
    private MTCNN mtcnn;
    private FileReader fileReader;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private SharedPreferences sharedPreferences;
    private FileReader.ProcessCallback fileReaderCallback;

// <----------------------- User controls --------------------------->

    // Use the device's GPU to perform faster computations.
// Refer https://www.tensorflow.org/lite/performance/gpu
    private final boolean useGpu = true;

    // Use XNNPack to accelerate inference.
// Refer https://blog.tensorflow.org/2020/07/accelerating-tensorflow-lite-xnnpack-integration.html
    private final boolean useXNNPack = true;

    // You may the change the models here.
// Use the model configs in Models.kt
// Default is Models.FACENET ; Quantized models are faster
    private final ModelInfo modelInfo = Models.FACENET;

    // Camera Facing
    private final int cameraFacing = CameraSelector.LENS_FACING_BACK;
// <---------------------------------------------------------------->


    @SuppressLint("StaticFieldLeak")
    static TextView logTextView ;

    public static void setMessage(String message) {
        logTextView.setText(message);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Remove the status bar to have a full screen experience
        // See this answer on SO -> https://stackoverflow.com/a/68152688/10878733
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Objects.requireNonNull(getWindow().getDecorView().getWindowInsetsController())
                    .hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
        activityMainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(activityMainBinding.getRoot());

        previewView = activityMainBinding.previewView;
        logTextView = activityMainBinding.logTextview;
        recognizeView = activityMainBinding.recognizeView;
        logTextView.setMovementMethod(new ScrollingMovementMethod());
        // Necessary to keep the Overlay above the PreviewView so that the boxes are visible.
        CustomImageView customImageView = activityMainBinding.recognizeView;


        try {
            faceNetModel = new FaceNet(this, modelInfo, useGpu, useXNNPack);
            mtcnn = new MTCNN(getAssets());
        } catch (IOException e) {
            Logger.log("Model load error :" +  String.valueOf(e));
        }
        Logger.log("Load model info:"  + modelInfo.getName());
        frameAnalyser = new FrameAnalyser(this, recognizeView, previewView, faceNetModel, mtcnn);
        fileReader = new FileReader(faceNetModel, mtcnn);


        // We'll only require the CAMERA permission from the user.
        // For scoped storage, particularly for accessing documents, we won't require WRITE_EXTERNAL_STORAGE or
        // READ_EXTERNAL_STORAGE permissions. See https://developer.android.com/training/data-storage
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
        } else {
            startCameraPreview();
        }

        SharedPreferences sharedPreferences = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
        boolean isSerializedDataStored = sharedPreferences.getBoolean(SHARED_PREF_IS_DATA_STORED_KEY, false);

        // If serialized data is not stored, show dialog to select images directory
        if (!isSerializedDataStored) {
            Logger.log("No serialized data was found. Select the images directory.");
            showSelectDirectoryDialog();
        }
        // Otherwise, show a dialog asking user if they want to load existing data or rescan for new data
        else {
            AlertDialog alertDialog = new AlertDialog.Builder(this)
                    .setTitle("Serialized Data")
                    .setMessage("Existing image data was found on this device. Would you like to load it?")
                    .setCancelable(false)
                    .setNegativeButton("LOAD", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            try {
                                frameAnalyser.faceList = loadSerializedImageData();
                            } catch (IOException | ClassNotFoundException e) {
                                e.printStackTrace();
                            }
                            Logger.log("Serialized data loaded.");
                        }
                    })
                    .setPositiveButton("RESCAN", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            launchChooseDirectoryIntent();
                        }
                    })
                    .create();
            alertDialog.show();
        }

        fileReaderCallback = new FileReader.ProcessCallback() {
            public void onProcessCompleted(ArrayList<Pair<String, float[]>> data, int numImagesWithNoFaces) throws IOException {
                frameAnalyser.faceList = data;
                saveSerializedImageData(data);
                Logger.log("Images parsed. Found " + numImagesWithNoFaces + " images with no faces.");
            }
        };

        // ------------------------------------------------------------------------------------------------ //


}
    // Request camera permission using a permission launcher

    private void requestCameraPermission() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }


    private void showSelectDirectoryDialog() {
        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle("Select Images Directory")
                .setMessage("As mentioned in the project's README file, please select a directory which contains the images.")
                .setCancelable(false)
                .setPositiveButton("SELECT", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        launchChooseDirectoryIntent();
                    }
                })
                .create();
        alertDialog.show();
    }

    private void launchChooseDirectoryIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        // startForActivityResult is deprecated.
        // See this SO thread -> https://stackoverflow.com/questions/62671106/onactivityresult-method-is-deprecated-what-is-the-alternative
        directoryAccessLauncher.launch(intent);
    }

    private final ActivityResultLauncher<Intent> directoryAccessLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                assert result.getData() != null;
                Uri dirUri = result.getData().getData();
                if (dirUri == null) {
                    //return@registerForActivityResult;
                }
                Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                        dirUri,
                        DocumentsContract.getTreeDocumentId(dirUri)
                );
                DocumentFile tree = DocumentFile.fromTreeUri(this, childrenUri);
                ArrayList<Pair<String, Bitmap>> images = new ArrayList<>();
                boolean errorFound = false;
                assert tree != null;
                if (tree.listFiles().length > 0) {
                    for (DocumentFile doc : tree.listFiles()) {
                        if (doc.isDirectory() && !errorFound) {
                            String name = doc.getName();
                            for (DocumentFile imageDocFile : doc.listFiles()) {
                                try {
                                    images.add(Pair.create(name, getFixedBitmap(imageDocFile.getUri())));
                                } catch (Exception e) {
                                    errorFound = true;
                                    Logger.log("Could not parse an image in " + name +
                                            " directory. Make sure that the file structure is as described in the README of the project and then restart the app.");
                                    break;
                                }
                            }
                            Logger.log("Found " + doc.listFiles().length + " images in " + name + " directory");
                        } else {
                            errorFound = true;
                            Logger.log("The selected folder should contain only directories. Make sure that the file structure is " +
                                    "as described in the README of the project and then restart the app.");
                        }
                    }
                } else {
                    errorFound = true;
                    Logger.log("The selected folder doesn't contain any directories. Make sure that the file structure is " +
                            "as described in the README of the project and then restart the app.");
                }
                if (!errorFound) {
                    fileReader.run(images, fileReaderCallback);
                    Logger.log("Detecting faces in " + images.size() + " images ...");
                } else {
                    AlertDialog alertDialog = new AlertDialog.Builder(this)
                            .setTitle("Error while parsing directory")
                            .setMessage("There were some errors while parsing the directory. Please see the log below. Make sure that the file structure is " +
                                    "as described in the README of the project and then tap RESELECT")
                            .setCancelable(false)
                            .setPositiveButton("RESELECT", (dialog, which) -> {
                                dialog.dismiss();
                                launchChooseDirectoryIntent();
                            })
                            .setNegativeButton("CANCEL", (dialog, which) -> {
                                dialog.dismiss();
                                finish();
                            })
                            .create();
                    alertDialog.show();
                }
            }
    );


    // Start camera preview by binding to camera provider instance and setting up preview and image analysis
    private void startCameraPreview() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                ProcessCameraProvider cameraProvider = null;
                try {
                    cameraProvider = cameraProviderFuture.get();
                    bindPreview(cameraProvider);
                } catch (ExecutionException | InterruptedException e) {
                    // Handle the error
                    e.printStackTrace();
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(cameraFacing)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageFrameAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                //.setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build();
        imageFrameAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), frameAnalyser);

        cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, preview, imageFrameAnalysis);
    }

    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    startCameraPreview();
                } else {
                    AlertDialog alertDialog = new AlertDialog.Builder(this)
                            .setTitle("Camera Permission")
                            .setMessage("The app couldn't function without the camera permission.")
                            .setCancelable(false)
                            .setPositiveButton("ALLOW", (dialog, which) -> {
                                dialog.dismiss();
                                requestCameraPermission();
                            })
                            .setNegativeButton("CLOSE", (dialog, which) -> {
                                dialog.dismiss();
                                finish();
                            })
                            .create();
                    alertDialog.show();
                }
            });


    private Bitmap getFixedBitmap(Uri imageFileUri) throws IOException {
        Bitmap imageBitmap = BitmapUtils.getBitmapFromUri(getContentResolver(), imageFileUri);
        ExifInterface exifInterface = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            exifInterface = new ExifInterface(getContentResolver().openInputStream(imageFileUri));
        }
        assert exifInterface != null;
        int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                imageBitmap = BitmapUtils.rotateBitmap(imageBitmap, 90f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                imageBitmap = BitmapUtils.rotateBitmap(imageBitmap, 180f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                imageBitmap = BitmapUtils.rotateBitmap(imageBitmap, 270f);
                break;
            default:
                break;
        }
        return imageBitmap;
    }

    private ArrayList<Pair<String, float[]>> loadSerializedImageData() throws IOException, ClassNotFoundException {
        File serializedDataFile = new File(getFilesDir(), SERIALIZED_DATA_FILENAME);
        ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(serializedDataFile));
        ArrayList<Pair<String, float[]>> data = (ArrayList<Pair<String, float[]>>) objectInputStream.readObject();
        objectInputStream.close();
        return data;
    }


    private void saveSerializedImageData(ArrayList<Pair<String, float[]>> data) throws IOException {
        File serializedDataFile = new File(getFilesDir(), SERIALIZED_DATA_FILENAME);
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(serializedDataFile));
        objectOutputStream.writeObject(data);
        objectOutputStream.flush();
        objectOutputStream.close();
        sharedPreferences.edit().putBoolean(SHARED_PREF_IS_DATA_STORED_KEY, true).apply();
    }


}

