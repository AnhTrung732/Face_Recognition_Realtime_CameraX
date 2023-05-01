package com.example.face_recognition_realtime_camerax;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;

import com.example.face_recognition_realtime_camerax.model.mobilefacenet.FaceNet;
import com.example.face_recognition_realtime_camerax.model.mtcnn.Align;
import com.example.face_recognition_realtime_camerax.model.mtcnn.Box;
import com.example.face_recognition_realtime_camerax.model.mtcnn.MTCNN;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Vector;


public class FileReader {
    private final FaceNet faceNetModel;
    private final MTCNN mtcnn;
    //private FaceDetector detector;
    private int numImagesWithNoFaces = 0;
    private int imageCounter = 0;
    private int numImages = 0;
    private ArrayList<Pair<String, Bitmap>> data = new ArrayList<>();
    private ProcessCallback callback;
    private final ArrayList<Pair<String, float[]>> imageData = new ArrayList<>();

    public FileReader(FaceNet faceNetModel, MTCNN mtcnn) {
        this.faceNetModel = faceNetModel;
        this.mtcnn = mtcnn;
    }

    public void run(ArrayList<Pair<String, Bitmap>> data, ProcessCallback callback) {
        numImages = data.size();
        this.data = data;
        this.callback = callback;
        scanImage(data.get(imageCounter).first, data.get(imageCounter).second);
    }

    public interface ProcessCallback {
        void onProcessCompleted(ArrayList<Pair<String, float[]>> data, int numImagesWithNoFaces)  throws IOException;
    }

    private void scanImage(String name, Bitmap image) {
        AsyncTask.execute(() -> {
            try {
                Vector<Box> boxes = mtcnn.detectFaces(image, image.getWidth() / 15);
                if (boxes != null && boxes.size() > 0) {
                    Bitmap bitmapTemp = Align.face_align(image, boxes.get(0).landmark);
                    boxes.get(0).toSquareShape();
                    boxes.get(0).limitSquare(bitmapTemp.getWidth(), bitmapTemp.getHeight());
                    Rect rect = boxes.get(0).transform2Rect();
                    new Handler(Looper.getMainLooper()).post(() -> {
                        float[] embedding = getEmbedding(image, rect);
                        imageData.add(new Pair<>(name, embedding));
                        if (imageCounter + 1 != numImages) {
                            imageCounter += 1;
                            scanImage(data.get(imageCounter).first, data.get(imageCounter).second);
                        } else {
                            try {
                                callback.onProcessCompleted(imageData, numImagesWithNoFaces);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            reset();
                        }
                    });
                }
                else {
                    numImagesWithNoFaces += 1;
                    if (imageCounter + 1 != numImages) {
                        imageCounter += 1;
                        scanImage(data.get(imageCounter).first, data.get(imageCounter).second);
                    } else {
                        callback.onProcessCompleted(imageData, numImagesWithNoFaces);
                        reset();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private float[] getEmbedding(Bitmap image, Rect bbox) {
        return faceNetModel.getFaceEmbedding(
                BitmapUtils.cropRectFromBitmap(
                        image,
                        bbox
                )
        );
    }

    private void reset() {
        imageCounter = 0;
        numImages = 0;
        numImagesWithNoFaces = 0;
        data.clear();
    }
}

