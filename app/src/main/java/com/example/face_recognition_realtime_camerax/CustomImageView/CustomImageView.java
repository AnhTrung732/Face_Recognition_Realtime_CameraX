package com.example.face_recognition_realtime_camerax.CustomImageView;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

public class CustomImageView extends androidx.appcompat.widget.AppCompatImageView {
    private List<Pair<Rect,String>> mRectangles = new ArrayList<>();


    public CustomImageView(Context context) {
        super(context);
        init();
    }

    public CustomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // Configure your paint object here
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStrokeWidth(10f);
        paint.setStyle(Paint.Style.STROKE);
        //        paint.setStyle(Paint.Style.FILL);
    }

    public void setRectangles(List<Pair<Rect, String>> rectangles) {
        mRectangles = rectangles;
        invalidate(); // Redraw the view when the rectangle list is updated
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Paint paint = new Paint();
        Paint paint2 = new Paint();

        paint.setColor(Color.RED);
        paint.setStrokeWidth(2f);
        paint.setStyle(Paint.Style.STROKE);

        paint2.setColor(Color.BLUE);
        paint2.setTextSize(35);

//        new Handler(Looper.getMainLooper()).post(new Runnable() {
//            @Override
//            public void run() {
//                // Code to execute on the UI thread
//            }
//        });
        for (Pair<Rect, String> rectangle : mRectangles) {
            canvas.drawRect(rectangle.first, paint);
            canvas.drawText(rectangle.second, rectangle.first.left, rectangle.first.top, paint2);
        }

    }
}
