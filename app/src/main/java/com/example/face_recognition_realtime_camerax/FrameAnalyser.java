package com.example.face_recognition_realtime_camerax;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;

import com.example.face_recognition_realtime_camerax.CustomImageView.CustomImageView;
import com.example.face_recognition_realtime_camerax.model.mobilefacenet.FaceNet;
import com.example.face_recognition_realtime_camerax.model.mobilefacenet.ModelInfo;
import com.example.face_recognition_realtime_camerax.model.mtcnn.Align;
import com.example.face_recognition_realtime_camerax.model.mtcnn.Box;
import com.example.face_recognition_realtime_camerax.model.mtcnn.MTCNN;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Vector;
import java.util.stream.Collectors;

import kotlinx.coroutines.Dispatchers;


// Analyser class to process frames and produce detections.
class FrameAnalyser implements ImageAnalysis.Analyzer {



    private final HashMap<String,ArrayList<Float>> nameScoreHashmap = new HashMap<>();
    private final CustomImageView recognizeView;
    private final PreviewView previewView;
    private float[] subject;

    // Used to determine whether the incoming frame should be dropped or processed.
    private boolean isProcessing = false;
    private final FaceNet faceNetModel;
    private final MTCNN mtcnn;
    // Store the face embeddings in a ( String , FloatArray ) ArrayList.
    // Where String -> name of the person and FloatArray -> Embedding of the face.
    ArrayList<Pair<String,float[]>> faceList = new ArrayList<>();
    List<Pair<Rect, String>> rectangles = new ArrayList<>();

    // <-------------- User controls --------------------------->

    // Use any one of the two metrics, "cosine" or "l2"
    private final String metricToBeUsed = "l2";

    // <-------------------------------------------------------->


    FrameAnalyser(Context context,
                  CustomImageView recognizeView,
                  PreviewView previewView,
                  FaceNet faceNetModel,
                  MTCNN mtcnn
    ){
        this.recognizeView = recognizeView;
        this.previewView = previewView;
        this.faceNetModel = faceNetModel;
        this.mtcnn = mtcnn;
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Override
    public void analyze(ImageProxy image) {
        // If the previous frame is still being processed, then skip this frame
        if (isProcessing || faceList.size() == 0) {
            image.close();
        }
        else {
            isProcessing = true;
            // Rotated bitmap for the FaceNet model
//            Bitmap frameBitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
//            frameBitmap.copyPixelsFromBuffer(image.getPlanes()[0].getBuffer());
//            frameBitmap = BitmapUtils.rotateBitmap(frameBitmap, image.getImageInfo().getRotationDegrees());
            previewView.post(new Runnable() {
                @Override
                public void run() {
                    Bitmap frameBitmap = previewView.getBitmap();
                    assert frameBitmap != null;
                    processFrameImage(frameBitmap);
                }
            });
            image.close();
        }
    }
    private float[] getEmbedding(Bitmap image, Rect bbox) {
        return faceNetModel.getFaceEmbedding(BitmapUtils.cropRectFromBitmap(image, bbox));
    }
    private void processFrameImage(Bitmap frameBitmap) {

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                    rectangles.clear();
                    Vector<Box> boxes = mtcnn.detectFaces(frameBitmap, frameBitmap.getWidth() / 15);
                    if (boxes != null && boxes.size() > 0) {
                        for (Box box : boxes) {
                            try {
                                Bitmap bitmapTemp = Align.face_align(frameBitmap, box.landmark);
                                box.toSquareShape();
                                box.limitSquare(bitmapTemp.getWidth(), bitmapTemp.getHeight());
                                Rect rect = box.transform2Rect();
                                Bitmap croppedBitmap = BitmapUtils.cropRectFromBitmap(frameBitmap, rect);

                                subject = faceNetModel.getFaceEmbedding(croppedBitmap);

                                // Perform clustering ( grouping )
                                // Store the clusters in a HashMap. Here, the key would represent the 'name'
                                // of that cluster and ArrayList<Float> would represent the collection of all
                                // L2 norms/ cosine distances.
                                for (int i = 0; i < faceList.size(); i++) {
                                    // If this cluster ( i.e an ArrayList with a specific key ) does not exist,
                                    // initialize a new one.
                                    if (nameScoreHashmap.get(faceList.get(i).first) == null) {
                                        // Compute the L2 norm and then append it to the ArrayList.
                                        ArrayList<Float> p = new ArrayList<Float>();
                                        if (metricToBeUsed.equals("cosine")) {
                                            p.add(cosineSimilarity(subject, faceList.get(i).second));
                                        } else {
                                            p.add(l2Norm(subject, faceList.get(i).second));
                                        }
                                        nameScoreHashmap.put(faceList.get(i).first, p);
                                    }
                                    // If this cluster exists, append the L2 norm/cosine score to it.
                                    else {
                                        if (metricToBeUsed.equals("cosine")) {
                                            nameScoreHashmap.get(faceList.get(i).first)
                                                    .add(cosineSimilarity(subject, faceList.get(i).second));
                                        } else {
                                            Objects.requireNonNull(nameScoreHashmap.get(faceList.get(i).first))
                                                    .add(l2Norm(subject, faceList.get(i).second));
                                        }
                                    }
                                }
                                // Compute the average of all scores norms for each cluster.
                                ArrayList<Float> avgScores = new ArrayList<Float>();
                                for (ArrayList<Float> scores : nameScoreHashmap.values()) {
                                    float[] scoresArray = new float[scores.size()];
                                    for (int i = 0; i < scores.size(); i++) {
                                        scoresArray[i] = scores.get(i);
                                    }
                                    float sum = 0;
                                    for (float score : scoresArray) {
                                        sum += score;
                                    }
                                    float avgScore = sum / scoresArray.length;
                                    avgScores.add(avgScore);
                                }
                                Logger.log("Average score for each user: " + nameScoreHashmap);

                                String[] names = nameScoreHashmap.keySet().toArray(new String[0]);
                                nameScoreHashmap.clear();


                                // Calculate the minimum L2 distance from the stored average L2 norms.
                                ModelInfo model = faceNetModel.modelInfo;
                                assert model != null;
                                String bestScoreUserName = "";
                                if (metricToBeUsed.equals("cosine")) {
                                    // In case of cosine similarity, choose the highest value.
                                    float value = model.getCosineThreshold();
                                    Toast.makeText(new MainActivity(),String.valueOf(value),Toast.LENGTH_SHORT).show();
                                    if (Collections.max(avgScores) > faceNetModel.modelInfo.getCosineThreshold()) {
                                        bestScoreUserName = names[avgScores.indexOf(Collections.max(avgScores))];
                                    } else {
                                        bestScoreUserName = "Unknown";
                                    }
                                } else {
                                    // In case of L2 norm, choose the lowest value.
                                    String name = model.getName();
                                    float value = model.l2Threshold;
                                    if (Collections.min(avgScores) > value) {
                                        bestScoreUserName = "Unknown";
                                    } else {
                                        bestScoreUserName = names[avgScores.indexOf(Collections.min(avgScores))];
                                    }
                                }
                                Logger.log("Person identified as " + bestScoreUserName);
                                rectangles.add(new Pair<>(rect,bestScoreUserName));
                            }
                            catch (Exception e) {
                                Logger.log("Exception in FrameAnalyser: " + e.getMessage());
                                continue; // continue with the next iteration of the loop
                                }
                        }
                    }
                    new Handler(Looper.getMainLooper()).post(() -> {
                        // Clear the BoundingBoxOverlay and set the new results ( boxes ) to be displayed.
                        recognizeView.setRectangles(rectangles);
                        isProcessing = false;
                    });
                }
        });

    }

    // Compute the cosine of the angle between x1 and x2.
    private float cosineSimilarity( float[] x1 , float[] x2 ) {
        float dot = 0;
        float mag1 = 0;
        float mag2 = 0;
        for (int i = 0; i < x1.length; i++) {
            dot += x1[i] * x2[i];
            mag1 += x1[i] * x1[i];
            mag2 += x2[i] * x2[i];
        }
        mag1 = (float) Math.sqrt(mag1);
        mag2 = (float) Math.sqrt(mag2);
        return dot / (mag1 * mag2);
    }

    // Compute the L2 norm of ( x2 - x1 )
    private Float l2Norm( float[] x1, float[] x2 ) {
        float sum = 0;
        for (int i = 0; i < x1.length; i++) {
            sum += Math.pow((x1[i] - x2[i]), 2);
        }
        return (float) Math.sqrt(sum);
    }
}


