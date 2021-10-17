package com.polsl.emagnifier;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

public class SurfaceOverlay implements SurfaceHolder.Callback{

    Canvas canvas;
    SurfaceHolder holder;
    protected Activity activity;
    Paint paint;
    LinkedHashMap<Rect,String> _rectList;
    TextView textView;
    SurfaceView surfaceView;

    public SurfaceOverlay(Activity activity){
        this.activity = activity;
        this.textView = activity.findViewById(R.id.text);
        surfaceView = activity.findViewById(R.id.overlay);
        surfaceView.setZOrderOnTop(true);
        holder = surfaceView.getHolder();
        holder.setFormat(PixelFormat.TRANSPARENT);
        holder.addCallback(this);
    }

    void DrawBoundingBox(int rotationDegrees, LinkedHashMap<Rect,String> _rectList ) {
        Log.d("asd2",String.valueOf(surfaceView.getWidth()));
        Log.d("asd2",String.valueOf(surfaceView.getHeight()));
        int colorBackground = Color.parseColor("#33e0f7fa");
        canvas = holder.lockCanvas();
        if(canvas!=null) {
            canvas.drawColor(colorBackground, PorterDuff.Mode.CLEAR);
            canvas.rotate((float) rotationDegrees);
            paint = new Paint();
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            paint.setColor(colorBackground);
            paint.setStrokeWidth(3);

            for (Map.Entry<Rect, String> entry : _rectList.entrySet()) {
                Rect rect = entry.getKey();
                canvas.drawRect(rect, paint);
            }
            holder.unlockCanvasAndPost(canvas);
        }
    }


    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

    }

    public void onTouch(MotionEvent event, LinkedHashMap<Rect,String> _rectList) {
        @SuppressLint("ResourceAsColor")

            int  touchX = (int) event.getX();
            int  touchY = (int) event.getY();

            switch(event.getAction()){
                case MotionEvent.ACTION_DOWN:
                    System.out.println("Touching down!");
                    for(Map.Entry<Rect,String> entry : _rectList.entrySet()) {
                        Rect rect = entry.getKey();
                        if(rect.contains(touchX,touchY)) {
                            String line = entry.getValue();
                            textView.setText(line);
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    break;
                case MotionEvent.ACTION_MOVE:
                    System.out.println("Sliding your finger around on the screen.");
                    break;
            }
    }

}

