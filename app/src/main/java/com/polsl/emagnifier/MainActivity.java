package com.polsl.emagnifier;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.Float.max;


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
    float scaleFactor;
    HashMap<Rect,String> _rectList = new HashMap<Rect, String>();
    Preview preview;
    ProcessCameraProvider cameraProvider;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private @Nullable
    TextToSpeech tts = null;
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
                    cameraProvider = cameraProviderFuture.get();
                    MainActivity.this.bindPreview(cameraProvider);
                } catch (ExecutionException | InterruptedException | CameraAccessException e) {
                    // No errors need to be handled for this feature.
                    // This should never be reached.
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     *
     * Binding to camera
     */
    @SuppressLint("RestrictedApi")
    private void bindPreview(ProcessCameraProvider cameraProvider) throws CameraAccessException {
        imgResolution= new Size(mCameraView.getWidth(), mCameraView.getHeight());
        Log.d("imgres",String.valueOf(imgResolution.toString()));

        preview = new Preview.Builder()
                .setTargetResolution(imgResolution)
                .setTargetAspectRatioCustom(new Rational(9, 16))
                .build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(mCameraView.createSurfaceProvider());

        //Image Analysis Function
        //Set static size according to your device or write a dynamic function for it
        @SuppressLint("RestrictedApi") ImageAnalysis imageAnalysis =
                new ImageAnalysis.Builder()
                        .setTargetResolution(imgResolution)
                        .setTargetAspectRatioCustom(new Rational(9,16))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
        imageAnalysis.setAnalyzer(executor, new ImageAnalysis.Analyzer() {
            @SuppressLint("UnsafeExperimentalUsageError")
            @Override
            public void analyze(@NonNull ImageProxy image) {
                //changing normal degrees into Firebase rotation
                int rotationDegrees = degreesToFirebaseRotation(image.getImageInfo().getRotationDegrees());
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
                //initializing FirebaseVisionTextRecognizer object
                FirebaseVisionTextRecognizer detector = FirebaseVision.getInstance()
                        .getOnDeviceTextRecognizer();
                //Passing FirebaseVisionImage Object created from the bitmap
                Task<FirebaseVisionText> result =  detector.processImage(FirebaseVisionImage.fromBitmap(bitmap))
                        .addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
                            @RequiresApi(api = Build.VERSION_CODES.O)
                            @Override
                            public void onSuccess(FirebaseVisionText firebaseVisionText) {
                                // Task completed successfully
                                // ...
                                textView=findViewById(R.id.text);
                                //getting decoded text
                                StringBuilder stringBuilder =  new StringBuilder();
                                String text=firebaseVisionText.getText();
                                //Setting the decoded text in the textview
                               /* textView.setText(text);*/
                                //for getting blocks and line elements
                                int color = R.color.white;
                               // ArrayList<Rect> rectList = new ArrayList<Rect>();
                                for (FirebaseVisionText.TextBlock block: firebaseVisionText.getTextBlocks()) {
                                    String blockText = block.getText();
                                    for (FirebaseVisionText.Line line: block.getLines()) {
                                        stringBuilder.append(line.getText());
                                        _rectList.put(line.getBoundingBox(),line.getText());
//                                        for (FirebaseVisionText.Element element: line.getElements()) {
//                                            String elementText = element.getText();
//
//                                        }
                                    }
                                }
                                DrawBoundingBox(color,_rectList, rotationDegrees);
                                //textView.setText(stringBuilder);   //TEKST DO EDITTEXTU
                                //Opoźnienie odświeżania detektora
                                Runnable runnable = new Runnable() {
                                    @Override
                                    public void run() {
                                        StringBuilder stringBuilder = new StringBuilder();
                                        image.close();
                                        _rectList=new HashMap<Rect, String>();
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        //Button for text to speech
        Button readButton= (Button) findViewById(R.id.readBtn);
        readButton.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {

               speakOut(textView.getText().toString());
            }
        }));
        ImageCapture imageCapture =
                new ImageCapture.Builder()
                        .setTargetRotation(mCameraView.getDisplay().getRotation())
                        .build();
//Button for taking photo
        Button takePhotoButton= (Button) findViewById(R.id.takePhotoBtn);
        takePhotoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
                File file = new File(getBatchDirectoryName(), mDateFormat.format(new Date())+ ".jpg");
                ImageCapture.OutputFileOptions outputFileOptions =
                        new ImageCapture.OutputFileOptions.Builder(file) .build();
                imageCapture.takePicture(executor,
                        /*new ImageCapture.OnImageSavedCallback() {
                            @Override
                            public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {

                            }
                            @Override
                            public void onError(ImageCaptureException error) {
                                // insert your code here.
                            }
                        },*/
                        new ImageCapture.OnImageCapturedCallback(){
                            @Override
                            public void onCaptureSuccess(ImageProxy imageProxy) {
                                @SuppressLint("UnsafeExperimentalUsageError")
                                Image capturedImage= imageProxy.getImage();
                                imageProxy.close();
                            //TODO: SAVING PICTURE
                            }

                        });

                }

        });

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

    private void DrawBoundingBox(int color, HashMap<Rect,String> rectHashMap,int rotationDegrees) {

        canvas = holder.lockCanvas();
        canvas.drawColor(0, PorterDuff.Mode.CLEAR);
        canvas.rotate((float) rotationDegrees);
        //border's properties
        paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        paint.setStrokeWidth(5);
        Paint paint2 = new Paint();
        paint2.setColor(color);

 //set text size
        for(Map.Entry<Rect,String> entry : _rectList.entrySet()) {
            Rect rect = entry.getKey();
            String line = entry.getValue();
            paint2.setTextSize((float) (rect.height()*0.9));
            canvas.drawRect(rect,paint);
            canvas.drawText(line,rect.left,rect.bottom,paint2);
        }
        //TODO: niebieski kolorek zamiast tekstu
       // rectArrayList.forEach(x->canvas.drawRect(x, paint));

//        canvas.drawRect(rect, paint);
        holder.unlockCanvasAndPost(canvas);
    }
    @SuppressLint({"ResourceAsColor", "RestrictedApi"})
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int touchX = (int) event.getX();
        touchX=(int) (touchX/scaleFactor);
        int touchY = (int) event.getY();
        touchY=(int) (touchY/scaleFactor);
        //val bitmap = viewFinder.getBitmap();
        // ivPreview.setImageBitmap(bitmap);// blokowanie preview
        // CameraX.unbindAll()
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
                /*try {
                    MainActivity.this.bindPreview(cameraProvider);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }*/
                break;
            case MotionEvent.ACTION_CANCEL:
            //startCamera();
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

    @Override
    public void onInit(int status) {
        if(status == TextToSpeech.SUCCESS){

        }
    }
    public String getBatchDirectoryName() {

        String app_folder_path = "";
        app_folder_path = Environment.getExternalStorageDirectory().toString() + "/images";
        File dir = new File(app_folder_path);
        if (!dir.exists() && !dir.mkdirs()) {
        }
        return app_folder_path;
    }
}
