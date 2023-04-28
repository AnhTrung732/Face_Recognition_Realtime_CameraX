package com.example.face_recognition_realtime_camerax;

import static android.content.ContentValues.TAG;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.media.Image;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.face_recognition_realtime_camerax.CustomImageView.CustomImageView;

import com.example.face_recognition_realtime_camerax.mobilefacenet.MobileFaceNet;
import com.example.face_recognition_realtime_camerax.mtcnn.Align;
import com.example.face_recognition_realtime_camerax.mtcnn.Box;
import com.example.face_recognition_realtime_camerax.mtcnn.MTCNN;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;


public class MainActivity extends AppCompatActivity implements ImageAnalysis.Analyzer {

    private final List<Pair<Rect, String>> mRectangles = new ArrayList<>();
    private MTCNN mtcnn;
    private MobileFaceNet mfn;


    public static Bitmap bitmap;
    private Bitmap bitmapCrop;

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    public CustomImageView recognizeView;
    PreviewView previewView;
    SwitchCompat detectSwitch;
    public Boolean mBoolean = false;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        recognizeView = findViewById(R.id.recognizeView);
        previewView = findViewById(R.id.previewView);
        detectSwitch = findViewById(R.id.detectSwitch);

        try {
            mtcnn = new MTCNN(getAssets());
            mfn = new MobileFaceNet(getAssets());
        } catch (IOException e) {
            Log.d("ERROR LOAD MODEL :", String.valueOf(e));
            finish();
        }

        detectSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (isChecked) {
                    mBoolean = true;
                    recognizeView.setVisibility(View.VISIBLE);
                } else {
                    mBoolean = false;
                    recognizeView.setVisibility(View.INVISIBLE);
                }
            }
        });

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                startCameraX(cameraProvider);
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }, getExecutor());
    }

    private void startCameraX(ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll();
        // Camera Selector Usecase
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();
        //Preview use case
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());


        //Image analysis use case
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                //.setTargetResolution(new Size(640, 360))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        //imageAnalysis.setAnalyzer(getExecutor(), this);
        imageAnalysis.setAnalyzer(getExecutor(), this);
        //Use image analysis

        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageAnalysis);
    }

    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        mRectangles.clear();
        if (mBoolean)
        {
            imageProxy.close();
            Bitmap bitmap = previewView.getBitmap();;
            assert bitmap != null;
            processImageData(bitmap);
        }
        else
        {
            imageProxy.close();
        }
    }

    private void processImageData(Bitmap bitmap) {
        assert bitmap != null;
        Bitmap bitmapTemp = bitmap.copy(bitmap.getConfig(), false);

        Vector<Box> boxes = mtcnn.detectFaces(bitmapTemp, bitmapTemp.getWidth() / 15); // 只有这句代码检测人脸，下面都是根据Box在图片中裁减出人脸

        for (Box box : boxes) {
            bitmapTemp = Align.face_align(bitmapTemp, box.landmark);
            box.toSquareShape();
            box.limitSquare(bitmapTemp.getWidth(), bitmapTemp.getHeight());
            Rect rect = box.transform2Rect();
            mRectangles.add(new Pair<>(rect, "Test"));
        }
        recognizeView.setRectangles(mRectangles);
    }
}