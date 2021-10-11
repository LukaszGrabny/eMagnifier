package com.polsl.emagnifier;

import android.Manifest;
import android.annotation.SuppressLint;
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
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.view.Display;
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
import androidx.annotation.Nullable;
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

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, TextToSpeech.OnInitListener{
    TextView textView;
    PreviewView mCameraView;
    SurfaceHolder holder;
    SurfaceView surfaceView;
    Canvas canvas;
    Paint paint;
    int cameraHeight, cameraWidth, xOffset, yOffset, boxWidth, boxHeight;
    private static final int MY_CAMERA_REQUEST_CODE = 100;
    public Size imgResolution;
    Float[] scaleFactorWidthHeight= new Float[2];
    LinkedHashMap<Rect,String> _rectList = new LinkedHashMap<Rect, String>();

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private @Nullable
    TextToSpeech tts = null;
    int rotation;
    int rotationDegrees;
    /**
     *Responsible for converting the rotation degrees from CameraX into the one compatible with Firebase ML
     */

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


    /**
     * Starting Camera
     */
    void startCamera(){
            mCameraView = findViewById(R.id.previewView);
        imgResolution= new Size(mCameraView.getWidth(), mCameraView.getHeight());
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    MainActivity.this.bindPreview(cameraProvider);
                } catch (ExecutionException | InterruptedException | CameraAccessException e) {
                    // No errors need to be handled for this feature.
                    // This should never be reached.
                }
            }
        }, ContextCompat.getMainExecutor(this));
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
                rotationDegrees = degreesToFirebaseRotation(image.getImageInfo().getRotationDegrees());

                if (image == null || image.getImage() == null) {
                    return;
                }
                //Getting a FirebaseVisionImage object using the Image object and rotationDegrees
                final Image mediaImage = image.getImage();

                FirebaseVisionImage images = FirebaseVisionImage.fromMediaImage(mediaImage, rotationDegrees);
                //Getting bitmap from FirebaseVisionImage Object
                Bitmap bmp=images.getBitmap();
                Log.d("bmp",String.valueOf(bmp.getHeight()));
                Log.d("bmp",String.valueOf(bmp.getWidth()));
                Log.d("mCamera width",String.valueOf(mCameraView.getWidth()));
                Log.d("mCamera height",String.valueOf(mCameraView.getHeight()));

                scaleFactorWidthHeight[0] = ((float) bmp.getWidth() / (float) mCameraView.getWidth());
                scaleFactorWidthHeight[1] = ((float) bmp.getHeight() / (float) mCameraView.getHeight());
                Log.d("asd",String.valueOf(scaleFactorWidthHeight[0]));
                Log.d("asd",String.valueOf(scaleFactorWidthHeight[1]));
                Bitmap resizedBitmap =
                        Bitmap.createScaledBitmap(
                                bmp,
                                (int) ((bmp.getWidth() / ((float) bmp.getWidth() / (float) mCameraView.getWidth()))),
                                (int) (bmp.getHeight() / ((float) bmp.getHeight() / (float) mCameraView.getHeight())),
                                true);

                Bitmap bitmap = Bitmap.createBitmap(resizedBitmap);
                Log.d("asd",String.valueOf(bitmap.getWidth()));
                Log.d("asd",String.valueOf(bitmap.getHeight()));
                Log.d("asd2",String.valueOf(surfaceView.getWidth()));
                Log.d("asd2",String.valueOf(surfaceView.getHeight()));
                FirebaseVisionTextRecognizer detector = FirebaseVision.getInstance()
                        .getOnDeviceTextRecognizer();
                //Passing FirebaseVisionImage Object created from the bitmap
                Task<FirebaseVisionText> result =  detector.processImage(FirebaseVisionImage.fromBitmap(bitmap))
                        .addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
                            @Override
                            public void onSuccess(@NonNull @NotNull FirebaseVisionText firebaseVisionText) {
                                textView=findViewById(R.id.text);
                                textView.setMovementMethod(new ScrollingMovementMethod());

                                StringBuilder stringBuilder =  new StringBuilder();
                                String text=firebaseVisionText.getText();


                                for (FirebaseVisionText.TextBlock block: firebaseVisionText.getTextBlocks()) {

                                        _rectList.put(block.getBoundingBox(),block.getText());
//                                        for (FirebaseVisionText.Element element: line.getElements()) {
//                                            String elementText = element.getText();
//                                        }
                                }
                                DrawBoundingBox(rotationDegrees);

                                //Opoźnienie odświeżania detektora
                                Runnable runnable = new Runnable() {
                                    @Override
                                    public void run() {
                                        image.close();
                                        _rectList=new LinkedHashMap<Rect, String>();
                                    }
                                };
                                final Handler handler = new Handler();
                                handler.postDelayed(runnable,1500);
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
        Log.d("dobmp",String.valueOf(preview.getAttachedSurfaceResolution()));
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        OrientationEventListener orientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                ConstraintLayout ll=  findViewById(R.id.linearLayout);
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
        setContentView(R.layout.activity_main);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
        }
        tts = new TextToSpeech(this,this);
       // this.onRequestPermissionsResult(100, new String[]{Manifest.permission.CAMERA},)
        //Start Camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
        //Create the bounding box
        surfaceView = findViewById(R.id.overlay);
        surfaceView.setZOrderOnTop(true);
        holder = surfaceView.getHolder();
        holder.setFormat(PixelFormat.TRANSPARENT);
        holder.addCallback(this);

        FloatingActionButton readButton= findViewById(R.id.readBtn);
        readButton.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(textView.getText().toString().length()>4000){
                    speakOut(textView.getText().toString());
                }
                else{

                    Collection<String> chunks = splitStringBySize(textView.getText().toString(),3999);
                    speakOutLong(chunks);
                }
            }
        }));
        FloatingActionButton readAllButton= findViewById(R.id.readAllBtn);
        readAllButton.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<Rect, String> entry : _rectList.entrySet()) {
                    sb.append(entry.getValue());
                    if(!isDimensionSwapped())
                        sb.append("\n");
                    else
                        sb.append(" ");
                }
                textView.setText(sb.toString());
            }
        }));


    }
    private static Collection<String> splitStringBySize(String str, int size) {
        ArrayList<String> split = new ArrayList<>();
        for (int i = 0; i <= str.length() / size; i++) {
            split.add(str.substring(i * size, Math.min((i + 1) * size, str.length())));
        }
        return split;
    }
    public void onPause(){
        if(tts !=null){
            tts.stop();
            tts.shutdown();
        }
        super.onPause();
    }
    private void speakOut(String text) {
        tts.setLanguage(new Locale("pl", "PL"));
        tts.speak(text.toLowerCase(new Locale("pl", "PL")),TextToSpeech.QUEUE_FLUSH,null,"");
    }
    private void speakOutLong(Collection<String> text) {
        tts.setLanguage(new Locale("pl", "PL"));
        for (String s : text){
        tts.speak(s.toLowerCase(new Locale("pl", "PL")),TextToSpeech.QUEUE_ADD,null,"");
        }
    }

    private void DrawBoundingBox(int rotationDegrees) {

            int colorBackground = Color.parseColor("#33e0f7fa");
            int colorWhite = Color.parseColor("#8CFFFFFF");
            canvas = holder.lockCanvas();
            if(canvas!=null) {
                canvas.drawColor(colorBackground, PorterDuff.Mode.CLEAR);
                canvas.rotate((float) rotationDegrees);
                //border's properties
                paint = new Paint();
                paint.setStyle(Paint.Style.FILL_AND_STROKE);
                paint.setColor(colorBackground);
                paint.setStrokeWidth(3);
/*        Paint paint2 = new Paint();
        paint2.setColor(colorWhite);*/

                //set text size
                for (Map.Entry<Rect, String> entry : _rectList.entrySet()) {
                    Rect rect = entry.getKey();
              /*  int rectLeft= (int) (rect.left/scaleFactorWidthHeight[0]);
                int rectTop= (int) (rect.top/scaleFactorWidthHeight[1]);
                int rectRight= (int) (rect.right/scaleFactorWidthHeight[0]);
                int rectBot= (int) (rect.bottom/scaleFactorWidthHeight[1]);
                rect.set(rectLeft,rectTop,rectRight,rectBot);*/
                    canvas.drawRect(rect, paint);
                }
                // rectArrayList.forEach(x->canvas.drawRect(x, paint));

//        canvas.drawRect(rect, paint);
                holder.unlockCanvasAndPost(canvas);
            }

    }
    @SuppressLint("ResourceAsColor")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int touchX = (int) event.getX();
        touchX=(int) (touchX/scaleFactorWidthHeight[0]);
        int touchY = (int) event.getY();
        touchY=(int) (touchY/scaleFactorWidthHeight[1]);

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
                for(Map.Entry<Rect,String> entry : _rectList.entrySet()) {
                    Rect rect = entry.getKey();
                    String line = entry.getValue();
                    Log.d("asd","dawaj");

                }
                break;
            case MotionEvent.ACTION_MOVE:
                System.out.println("Sliding your finger around on the screen.");
                break;
        }
        return true;
    }

    /**
     * Callback functions for the surface Holder
     */

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    @Override
    public void onInit(int status) {
        if(status == TextToSpeech.SUCCESS){
        }
    }
}
