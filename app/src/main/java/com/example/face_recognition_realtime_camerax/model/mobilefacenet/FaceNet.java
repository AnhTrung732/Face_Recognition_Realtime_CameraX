package com.example.face_recognition_realtime_camerax.model.mobilefacenet;

import static java.lang.Math.max;
import static java.lang.Math.sqrt;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;


import com.example.face_recognition_realtime_camerax.Logger;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;
import org.tensorflow.lite.support.tensorbuffer.TensorBufferFloat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.IntStream;


// Utility class for FaceNet model
public class FaceNet {
    // Output embedding size
    public final int embeddingDim;
    private final Interpreter interpreter;
    private final ImageProcessor imageTensorProcessor;
    public ModelInfo modelInfo;


    public FaceNet(
            Context context,
            ModelInfo modelInfo,
            boolean useGpu,
            boolean useXNNPack) throws IOException {
        // Input image size for FaceNet model.
        this.modelInfo = modelInfo;
        int imgSize = modelInfo.getInputDims();
        // Output embedding size
        embeddingDim = modelInfo.getOutputDims();
        // Initialize TFLiteInterpreter
        Interpreter.Options interpreterOptions = new Interpreter.Options();
        // Add the GPU Delegate if supported.
        // See -> https://www.tensorflow.org/lite/performance/gpu#android
        if (useGpu) {
            CompatibilityList compatList = new CompatibilityList();
            if (compatList.isDelegateSupportedOnThisDevice()) {
                interpreterOptions.addDelegate(new GpuDelegate(compatList.getBestOptionsForThisDevice()));
            }
        } else {
            // Number of threads for computation
            interpreterOptions.setNumThreads(4);
        }
        interpreterOptions.setUseXNNPACK(useXNNPack);
        interpreterOptions.setUseNNAPI(true);

        interpreter = new Interpreter(FileUtil.loadMappedFile(context, modelInfo.getAssetsFilename()), interpreterOptions);
        Logger.log("Using " + modelInfo.getName() + " model.");

        imageTensorProcessor =
                (new ImageProcessor.Builder())
                        .add(new ResizeOp(imgSize, imgSize, ResizeOp.ResizeMethod.BILINEAR))
                        .add(new StandardizeOp())
                        .build();
    }

    // Gets an face embedding using FaceNet.
    public float[] getFaceEmbedding(Bitmap image) {
        return runFaceNet(convertBitmapToBuffer(image))[0];
    }

    private ByteBuffer convertBitmapToBuffer(Bitmap image) {
        return imageTensorProcessor.process(TensorImage.fromBitmap(image)).getBuffer();
    }

    // Run the FaceNet model.
    private float[][] runFaceNet(Object inputs) {
        long t1 = System.currentTimeMillis();
        float[][] faceNetModelOutputs = new float[1][embeddingDim];

        interpreter.run(inputs, faceNetModelOutputs);
        Log.i("Performance", "${model.name} Inference Speed in ms : ${System.currentTimeMillis()-t1}");

        return faceNetModelOutputs;
    }

    public static class StandardizeOp implements TensorOperator {
        @Override
        public TensorBuffer apply(TensorBuffer p0) {
            float[] pixels = p0.getFloatArray();
            float sum = 0;
            for (float pixel : pixels) {
                sum += pixel;
            }
            float mean = sum / pixels.length;
            float sumSquares = 0;
            for (float pixel : pixels) {
                float diff = pixel - mean;
                sumSquares += diff * diff;
            }
            float std = (float) sqrt(sumSquares / pixels.length);
            std = max(std, 1f / (float) sqrt(pixels.length));
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = (pixels[i] - mean) / std;
            }
            TensorBuffer output = TensorBuffer.createFixedSize(p0.getShape(), DataType.FLOAT32);
            output.loadArray(pixels);
            return output;
        }
    }
}
