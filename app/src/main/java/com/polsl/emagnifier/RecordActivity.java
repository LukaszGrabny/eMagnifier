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
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.Float.max;

public class RecordActivity extends AppCompatActivity implements SurfaceHolder.Callback, TextToSpeech.OnInitListener {

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
    ConcurrentHashMap<Rect,String> _rectList = new ConcurrentHashMap<Rect, String>();
    boolean recordBtnClicked;
    LinkedHashSet<String> stringSet= new LinkedHashSet<>();
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

    void startCamera(){
        mCameraView = findViewById(R.id.previewViewRecord);
        imgResolution= new Size(mCameraView.getWidth(), mCameraView.getHeight());
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

         cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    RecordActivity.this.bindPreview(cameraProvider);
                } catch (ExecutionException | InterruptedException | CameraAccessException e) {
                    // No errors need to be handled for this feature.
                    // This should never be reached.
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }
    /**
     * Binding to camera
     */
    private void bindPreview(ProcessCameraProvider cameraProvider) throws CameraAccessException {
        imgResolution= new Size(mCameraView.getWidth(), mCameraView.getHeight());
        Log.d("imgres",String.valueOf(imgResolution.toString()));

        @SuppressLint("RestrictedApi") Preview preview = new Preview.Builder()
                .setTargetResolution(imgResolution)
                .setTargetAspectRatioCustom(new Rational(9, 16))
                .build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(mCameraView.createSurfaceProvider());
        ImageAnalysis.Builder builder = new ImageAnalysis.Builder();
        Camera2Interop.Extender ext = new Camera2Interop.Extender<>(builder);
        ext.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
        ext.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<Integer>(60, 120));
        builder.setTargetResolution(imgResolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
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
                                textView=findViewById(R.id.textRecord);
                                textView.setMovementMethod(new ScrollingMovementMethod());
                                //getting decoded text
                                StringBuilder stringBuilder =  new StringBuilder();
                                String text=firebaseVisionText.getText(); //scrollowanie
                                //Setting the decoded text in the textview
                                /* textView.setText(text);*/
                                //for getting blocks and line elements
                                int color = R.color.white;
                                // ArrayList<Rect> rectList = new ArrayList<Rect>();
                                for (FirebaseVisionText.TextBlock block: firebaseVisionText.getTextBlocks()) {
                                    String blockText = block.getText();
                                    _rectList.put(block.getBoundingBox(),blockText);

                                }
                                DrawBoundingBox(color,_rectList, rotationDegrees);

                                Runnable runnable = new Runnable() {
                                    @Override
                                    public void run() {
                                        StringBuilder stringBuilder = new StringBuilder();
                                        image.close();
                                        _rectList=new ConcurrentHashMap<Rect, String>();
                                    }
                                };
                                final Handler handler = new Handler();
                                handler.postDelayed(runnable,1300);
                            }
                        })
                        .addOnFailureListener(
                                new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        // Task failed with an exception
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
        setContentView(R.layout.activity_record);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
        }
        // this.onRequestPermissionsResult(100, new String[]{Manifest.permission.CAMERA},)
        //Start Camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
        tts = new TextToSpeech(this,this);
        //Create the bounding box
        surfaceView = RecordActivity.this.findViewById(R.id.overlayRecord);
        surfaceView.setZOrderOnTop(true);
        holder = surfaceView.getHolder();
        holder.setFormat(PixelFormat.TRANSPARENT);
        holder.addCallback(this);

        TextView dialogTextView = (TextView) findViewById(R.id.dialogTView);
        FloatingActionButton recordButton = findViewById(R.id.recordBtnRecord);
        recordButton.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onClick(View v) {
                    if(!recordBtnClicked) {
                        recordBtnClicked = true;
                        textView.setText("");
                        RecordAndProcess();
                        recordButton.setImageResource(android.R.drawable.ic_media_pause);
                    }
                    else {
                        recordBtnClicked = false;
                        textView.setText("");
                        StringBuilder recorderStringBuilder = new StringBuilder();

                         recorderStringBuilder = checkSimilarity(stringSet);

                         ArrayList<String> dummyString=spiltParagraph(1,recorderStringBuilder.toString());
                        Set<String> set2=new LinkedHashSet<>(dummyString);
                        StringBuilder sb = new StringBuilder();
                        for (String s : set2) {
                            sb.append(s);
                            /*if(!s.contentEquals(""))  sb.append(System.getProperty("line.separator"));*/
                            if(s.contentEquals("."))  sb.append(System.getProperty("line.separator"));
                        }
                        textView.setText(sb.toString());
                        textView.setTextIsSelectable(true);
                        textView.setFocusableInTouchMode(true);
                        textView.setFocusable(true);
                        recordButton.setImageResource(android.R.drawable.presence_video_online);
                        stringSet.removeAll(stringSet);
                        sb.setLength(0);
                    }
            }
        });
        FloatingActionButton readButton= findViewById(R.id.speakBtnRecord);
        readButton.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(textView.getText().toString().length()<TextToSpeech.getMaxSpeechInputLength()){

                    speakOut(textView.getText().toString());
                }
                else{
                    Collection<String> chunks = splitStringBySize(textView.getText().toString(),TextToSpeech.getMaxSpeechInputLength());
                    Log.d("chunk", String.valueOf(chunks.size()));
                    speakOutLong(chunks);
                }
            }
        }));
    }
    private ArrayList<String> spiltParagraph(int splitAfterWords, String someLargeText) {
        String[] para = someLargeText.split(" ");
        ArrayList<String> data = new ArrayList<>();
        for (int i = 0; i < para.length; i += splitAfterWords) {
            if (i + (splitAfterWords - 1) < para.length) {
                StringBuilder compiledString = new StringBuilder();
                for (int f = i; f <= i + (splitAfterWords - 1); f++) {
                    compiledString.append(para[f] + " ");
                }
                data.add(compiledString.toString());
            }
        }
        return data;
    }
    private static Collection<String> splitStringBySize(String str, int size) {
        ArrayList<String> split = new ArrayList<>();
        for (int i = 0; i <= str.length() / size; i++) {
            split.add(str.substring(i * size, Math.min((i + 1) * size, str.length())));
        }
        return split;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private StringBuilder checkSimilarity(Set<String> input) {
        int similarStrings = 0;
        String[] split1 = new String[0];
        String[] split2 = new String[0];
        String[] inputArray= input.toArray(new String[input.size()]);
        for (int j=0;j< inputArray.length-1;j++)
        {
           split1=inputArray[j].split(" ");
           split2=inputArray[j+1].split(" ");
            for (int i = 0; i < split1.length; i++) {
                for(int k = 0; k < split2.length; k++)
                {
                    if (split1[i].contains(split2[k])) similarStrings++;
                }
            }
            if(split1.length> split2.length){
                if (similarStrings>split2.length/3) inputArray[j+1]="";
            }else{
                if (similarStrings>split1.length/3) inputArray[j]="";
            }
            similarStrings=0;
        }
        StringBuilder sb = new StringBuilder();
        for (String s : inputArray) {
            sb.append(s);
            if(!s.contentEquals(""))  sb.append(System.getProperty("line.separator"));
        }
        return sb;
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
    private void  RecordAndProcess(){
        new Thread(new Runnable() {
            public void run() {
                Log.d("thread","jestem");
                while(recordBtnClicked){
                    for(Map.Entry<Rect,String> entry : _rectList.entrySet()) {
                        if (entry.getValue() != null) {
                        stringSet.add(entry.getValue());
                        }
                    }
                }
            }
        }).start();
    }

    private void DrawBoundingBox(int color, ConcurrentHashMap<Rect, String> rectHashMap, int rotationDegrees) {
        if(canvas!=null) {
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
            for (Map.Entry<Rect, String> entry : _rectList.entrySet()) {
                Rect rect = entry.getKey();
                String line = entry.getValue();
                paint2.setTextSize((float) (rect.height() * 0.9));
                canvas.drawRect(rect, paint);
                canvas.drawText(line, rect.left, rect.bottom, paint2);
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
        touchX=(int) (touchX/scaleFactor);
        int touchY = (int) event.getY();
        touchY=(int) (touchY/scaleFactor);

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

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) { ;
    }

    @Override
    public void onInit(int status) {

    }
}
