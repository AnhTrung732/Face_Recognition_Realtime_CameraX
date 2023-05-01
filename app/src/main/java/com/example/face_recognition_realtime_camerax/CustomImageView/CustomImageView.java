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
        Paint boxPaint = new Paint();
        boxPaint.setColor(Color.parseColor("#4D90caf9"));
        boxPaint.setStyle(Paint.Style.FILL);


        //        paint.setStyle(Paint.Style.FILL);
    }

    public void setRectangles(List<Pair<Rect, String>> rectangles) {
        mRectangles = rectangles;
        invalidate(); // Redraw the view when the rectangle list is updated
    }


    @SuppressLint("DrawAllocation")
    @Override
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);


        Paint boxPaint = new Paint();
        boxPaint.setColor(Color.parseColor("#4D90caf9"));
        boxPaint.setStyle(Paint.Style.FILL);

        Paint textPaint = new Paint();

        textPaint.setStrokeWidth(2.0f);
        textPaint.setTextSize(20f);
        textPaint.setColor(Color.CYAN);


        if (mRectangles.size() > 0)
        {
            for (Pair<Rect, String> rectangle : mRectangles) {
                canvas.drawRoundRect(new RectF(rectangle.first), 16f, 16f, boxPaint);
                canvas.drawText(rectangle.second, rectangle.first.left, rectangle.first.top, textPaint);

            }
        }

    }
}
