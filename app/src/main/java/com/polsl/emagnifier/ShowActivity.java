package com.polsl.emagnifier;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.Float.max;


public class ShowActivity extends AppCompatActivity implements SurfaceHolder.Callback{
    TextView textView;
    PreviewView mCameraView;
    SurfaceHolder holder;
    SurfaceView surfaceView;
    Canvas canvas;
    Paint paint;
    int cameraHeight, cameraWidth, xOffset, yOffset, boxWidth, boxHeight;
    private static final int MY_CAMERA_REQUEST_CODE = 100;
    public Size imgResolution;
    float scaleFactor;
    LinkedHashMap<Rect,String> _rectList = new LinkedHashMap<Rect, String>();
    int rotation;

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private int degreesToFirebaseRotation(int degrees) {
        switch (degrees) {
            case 0:
                return FirebaseVisionImageMetadata.ROTATION_0;
            case 90:
                return FirebaseVisionImageMetadata.ROTATION_90;
            case 180:
                return FirebaseVisionImageMetadata.ROTATION_180;
            case 270:
                return FirebaseVisionImageMetadata.ROTATION_270;
            default:
                throw new IllegalArgumentException(
                        "Rotation must be 0, 90, 180, or 270.");
        }
    }

    void startCamera(){
        mCameraView = findViewById(R.id.previewViewShow);
        imgResolution= new Size(mCameraView.getWidth(), mCameraView.getHeight());
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    ShowActivity.this.bindPreview(cameraProvider);
                } catch (ExecutionException | InterruptedException | CameraAccessException e) {
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint("RestrictedApi")
    private void bindPreview(ProcessCameraProvider cameraProvider) throws CameraAccessException {
        boolean swappedDimensions=isDimensionSwapped();
        Rational rational;
        Preview preview = null;
        if(!swappedDimensions) {
            imgResolution = new Size(mCameraView.getWidth(), mCameraView.getHeight());
            rational = new Rational(9, 16);
            preview = new Preview.Builder()
                    .setTargetResolution(imgResolution)
                    .setTargetAspectRatioCustom(rational)
                    .build();
        }
        else {
            imgResolution = new Size(mCameraView.getHeight(), mCameraView.getWidth());
            rational=new Rational(21, 9);
             preview = new Preview.Builder()
                    .setTargetResolution(imgResolution)
                    .setTargetAspectRatioCustom(rational)
                    .build();
        }
        Log.d("imgres",String.valueOf(imgResolution.toString()));

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(mCameraView.createSurfaceProvider());
        ImageAnalysis.Builder builder = new ImageAnalysis.Builder();
        Camera2Interop.Extender ext = new Camera2Interop.Extender<>(builder);
        ext.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
        ext.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<Integer>(60, 80));
        Log.d("bmp",String.valueOf(imgResolution.getWidth()));
        Log.d("bmp",String.valueOf(imgResolution.getHeight()));
        Log.d("dobmp",String.valueOf(rotation));
        Log.d("dobmp",String.valueOf(preview.getAttachedSurfaceResolution()));
        if(swappedDimensions) {
            builder.setTargetAspectRatioCustom(new Rational(16, 9))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetRotation(rotation)
                    .build();
        }
        else {
            builder.setTargetAspectRatioCustom(new Rational(16, 9))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(imgResolution)
                    .setTargetRotation(rotation)
                    .build();
        }

        @SuppressLint("RestrictedApi") ImageAnalysis imageAnalysis = builder.build();
        imageAnalysis.setAnalyzer(executor, new ImageAnalysis.Analyzer() {
            @SuppressLint("UnsafeExperimentalUsageError")
            @Override
            public void analyze(@NonNull ImageProxy image) {
                //changing normal degrees into Firebase rotation
                int rotationDegrees = degreesToFirebaseRotation(image.getImageInfo().getRotationDegrees());
                if (image == null || image.getImage() == null) {
                    return;
                }

                final Image mediaImage = image.getImage();

                FirebaseVisionImage images = FirebaseVisionImage.fromMediaImage(mediaImage, rotationDegrees);
                Bitmap bmp=images.getBitmap();
                Log.d("bmp",String.valueOf(bmp.getHeight()));
                Log.d("bmp",String.valueOf(bmp.getWidth()));
                Log.d("mCamera width",String.valueOf(mCameraView.getWidth()));
                Log.d("mCamera height",String.valueOf(mCameraView.getHeight()));
                scaleFactor = max(
                        (float) bmp.getWidth() / (float) mCameraView.getWidth(),
                        (float) bmp.getHeight() / (float) mCameraView.getHeight());
                Log.d("asd",String.valueOf(scaleFactor));
                Bitmap resizedBitmap =
                        Bitmap.createScaledBitmap(
                                bmp,
                                (int) ((bmp.getWidth() / ((float) bmp.getWidth() / (float) mCameraView.getWidth()))), //*1.05
                                (int) (bmp.getHeight() / ((float) bmp.getHeight() / (float) mCameraView.getHeight())),
                                true);

                Bitmap bitmap = Bitmap.createBitmap(resizedBitmap);
                Log.d("asd",String.valueOf(bitmap.getWidth()));
                Log.d("asd",String.valueOf(bitmap.getHeight()));
                FirebaseVisionTextRecognizer detector = FirebaseVision.getInstance()
                        .getOnDeviceTextRecognizer();
                Task<FirebaseVisionText> result =  detector.processImage(FirebaseVisionImage.fromBitmap(bitmap))
                        .addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
                            @RequiresApi(api = Build.VERSION_CODES.O)
                            @Override
                            public void onSuccess(FirebaseVisionText firebaseVisionText) {

                                textView=findViewById(R.id.textShow);
                                textView.setMovementMethod(new ScrollingMovementMethod());
                                StringBuilder stringBuilder =  new StringBuilder();

                                for (FirebaseVisionText.TextBlock block: firebaseVisionText.getTextBlocks()) {
                                    for (FirebaseVisionText.Line line: block.getLines()) {
                                        stringBuilder.append(line.getText());
                                        _rectList.put(line.getBoundingBox(),line.getText());
                                    }
                                }
                                DrawBoundingBox(rotationDegrees);
                                Runnable runnable = new Runnable() {
                                    @Override
                                    public void run() {
                                        StringBuilder stringBuilder = new StringBuilder();
                                        image.close();
                                        _rectList=new LinkedHashMap<Rect, String>();
                                    }
                                };
                                final Handler handler = new Handler();
                                handler.postDelayed(runnable,2500);
                            }
                        })
                        .addOnFailureListener(
                                new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        // Task failed with an exception
                                        // ...
                                        Log.e("Error",e.toString());
                                        image.close();
                                    }
                                });
            }
        });
        Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, imageAnalysis,preview);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Zezwolono na dostęp do aparatu", Toast.LENGTH_LONG).show();
                startCamera();
            } else {
                Toast.makeText(this, "Nie zezwolono na dostęp do aparatu", Toast.LENGTH_LONG).show();
            }
        }
    }
    private boolean isDimensionSwapped(){
        boolean swapped=false;
        Display display = ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
        int orientation=display.getRotation();
        if(orientation== Surface.ROTATION_0)
        {rotation=Surface.ROTATION_0;}
        if(orientation== Surface.ROTATION_90)
        {swapped=true;rotation=Surface.ROTATION_90;}
        if(orientation== Surface.ROTATION_180)
        { rotation=Surface.ROTATION_180;}
        if(orientation== Surface.ROTATION_270)
        {swapped=true; rotation=Surface.ROTATION_270;}
        return swapped;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        OrientationEventListener orientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                ConstraintLayout ll=  findViewById(R.id.Constraint2Show);
                ConstraintSet set = new ConstraintSet();
                set.clone(ll);
                // Monitors orientation values to determine the target rotation value
                if (orientation >= 45 && orientation < 135) {
                    rotation = Surface.ROTATION_270;
                    set.setDimensionRatio(R.layout.activity_main,"1:3");
                } else if (orientation >= 135 && orientation < 225) {
                    rotation = Surface.ROTATION_180;
                } else if (orientation >= 225 && orientation < 315) {
                    rotation = Surface.ROTATION_90;
                    set.setDimensionRatio(R.layout.activity_main,"1:3");
                } else {
                    rotation = Surface.ROTATION_0;
                }
                set.applyTo(ll);
            }
        };

        orientationEventListener.enable();
        setContentView(R.layout.activity_show);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
        surfaceView = ShowActivity.this.findViewById(R.id.overlayShow);
        surfaceView.setZOrderOnTop(true);
        holder = surfaceView.getHolder();
        holder.setFormat(PixelFormat.TRANSPARENT);
        holder.addCallback(this);

        TextView dialogTextView = (TextView) findViewById(R.id.dialogTView);
        FloatingActionButton showButton=  findViewById(R.id.showBtnShow);
        showButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String textToDialog = textView.getText().toString();
                DialogFragment newFragment = TextDialog.newInstance(textToDialog);
                newFragment.show(getSupportFragmentManager(), "tekst");
            }
        });
        FloatingActionButton readAllButton= findViewById(R.id.readAllBtn);
        readAllButton.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<Rect, String> entry : _rectList.entrySet()) {
                    sb.append(entry.getValue());
                    if((!isDimensionSwapped()))
                        sb.append("\n");
                    else
                        sb.append(" ");
                }
                textView.setText(sb.toString());
            }
        }));
    }
    public static class TextDialog  extends DialogFragment {
        public String textToDialog;
        static public TextDialog newInstance(String textToDialog){
            TextDialog textDialog= new TextDialog();
            // Supply num input as an argument.
            Bundle args = new Bundle();
            args.putString("tekst", textToDialog);
            textDialog.setArguments(args);
            return textDialog;
        }
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Wykryty tekst")
                    .setView(R.layout.dialog_view);
            LayoutInflater inflater = getActivity().getLayoutInflater();
            textToDialog= getArguments().getString("tekst");
            View content =  inflater.inflate(R.layout.dialog_view, null);
            builder.setView(content);
            TextView dialogTextView = content.findViewById(R.id.dialogTView);
            dialogTextView.setText(textToDialog);
            dialogTextView.setMovementMethod(new ScrollingMovementMethod());
            return builder.create();
        }
    }

    private void DrawBoundingBox(int rotationDegrees) {

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
    @SuppressLint("ResourceAsColor")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int touchX = (int) event.getX();
        int touchY = (int) event.getY();

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
                break;
        }
        return true;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }
}
