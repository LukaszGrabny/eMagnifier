package com.polsl.emagnifier;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
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
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
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

import java.util.HashMap;
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
                                textView=findViewById(R.id.textShow);
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
                                //Opoźnienie odświeżania detektora
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show);

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
        //Create the bounding box
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

                // Create the fragment and show it as a dialog.
                String textToDialog = textView.getText().toString();
                DialogFragment newFragment = TextDialog.newInstance(textToDialog);
                newFragment.show(getSupportFragmentManager(), "tekst");

            }
        });
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
            // Use the Builder class for convenient dialog construction
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
        // rectArrayList.forEach(x->canvas.drawRect(x, paint));

//        canvas.drawRect(rect, paint);
        holder.unlockCanvasAndPost(canvas);
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
}
