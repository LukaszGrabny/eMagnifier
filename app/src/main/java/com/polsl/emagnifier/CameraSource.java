package com.polsl.emagnifier;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;

import com.ddyos.unicode.exifinterface.UnicodeExifInterface;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static android.content.Context.WINDOW_SERVICE;

public class CameraSource{

    protected Activity activity;
    PreviewView mCameraView;
    private final Context context;
    Size imgResolution;
    ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    int rotation;
    int rotationDegrees;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    Float[] scaleFactorWidthHeight= new Float[2];
    TextView textView;
    ConcurrentHashMap<Rect,String> _rectCon = new ConcurrentHashMap<Rect,String>();
    LinkedHashMap<Rect,String>_rectList = new LinkedHashMap<>();
    SurfaceHolder holder;
    SurfaceOverlay surfaceOverlay;
    public boolean recordBtnClicked;
    ImageCapture imageCapture=null;


    public CameraSource (Activity activity, Context context, SurfaceHolder holder) {
        this.activity = activity;
        this.context = context;
        this.holder = holder;
        surfaceOverlay= new SurfaceOverlay(activity);
    }

    void startCamera(){
            mCameraView = activity.findViewById(R.id.previewView);
            imgResolution = new Size(mCameraView.getWidth(), mCameraView.getHeight());
            cameraProviderFuture = ProcessCameraProvider.getInstance(context);

            cameraProviderFuture.addListener(new Runnable() {
                @Override
                public void run() {
                    try {
                        ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                        bindPreview(cameraProvider);
                    } catch (ExecutionException | InterruptedException | CameraAccessException e) {
                        // No errors need to be handled for this feature.
                        // This should never be reached.
                    }
                }
            }, ContextCompat.getMainExecutor(context));

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

                FirebaseVisionTextRecognizer detector = FirebaseVision.getInstance()
                        .getOnDeviceTextRecognizer();
                //Passing FirebaseVisionImage Object created from the bitmap
                Task<FirebaseVisionText> result =  detector.processImage(FirebaseVisionImage.fromBitmap(bitmap))
                        .addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
                            @Override
                            public void onSuccess(@NonNull @NotNull FirebaseVisionText firebaseVisionText) {
                                textView=activity.findViewById(R.id.text);
                                if(textView!=null) {
                                    textView.setMovementMethod(new ScrollingMovementMethod());
                                }
                                StringBuilder stringBuilder =  new StringBuilder();
                                String text=firebaseVisionText.getText();


                                for (FirebaseVisionText.TextBlock block: firebaseVisionText.getTextBlocks()) {

                                    _rectList.put(block.getBoundingBox(),block.getText());
                                    _rectCon.put(block.getBoundingBox(),block.getText());
                                }
                                surfaceOverlay.DrawBoundingBox(rotationDegrees, _rectList);

                                Runnable runnable = new Runnable() {
                                    @Override
                                    public void run() {
                                        image.close();
                                        _rectList = new LinkedHashMap<Rect, String>();
                                        _rectCon = new ConcurrentHashMap<Rect, String>();
                                    }
                                };
                                final Handler handler = new Handler();
                                handler.postDelayed(runnable, 1500);
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
        imageCapture = new ImageCapture.Builder()
                .setTargetRotation(rotation)
                .build();
        Camera camera = cameraProvider.bindToLifecycle((AppCompatActivity) context, cameraSelector, imageAnalysis,preview, imageCapture);
        Log.d("dobmp",String.valueOf(preview.getAttachedSurfaceResolution()));
    }
    public boolean isDimensionSwapped(){
        boolean swapped=false;
        Display display = ((WindowManager)activity.getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
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
    public void fillAllWords() {
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

    public void passEvent(MotionEvent event) {
        surfaceOverlay.onTouch(event,_rectList);
    }

     ArrayList<String> spiltParagraph(int splitAfterWords, String someLargeText) {
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

    @RequiresApi(api = Build.VERSION_CODES.O)
    public StringBuilder checkSimilarity(Set<String> input) {
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

    public LinkedHashSet<String> RecordAndProcess(){
        LinkedHashSet<String> stringSet= new LinkedHashSet<String>();
        new Thread(new Runnable() {
            public void run() {
              //  textView.setText("");
                while(recordBtnClicked){
                    for(Map.Entry<Rect,String> entry : _rectCon.entrySet()) {
                        if (entry.getValue() != null) {
                            stringSet.add(entry.getValue());
                        }
                    }
                }
            }
        }).start();
        return stringSet;
    }

    public void setOrientation(int orientation){
        ConstraintLayout ll=  activity.findViewById(R.id.Constraintlayout);
        ConstraintSet set = new ConstraintSet();
        set.clone(ll);
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

    @RequiresApi(api = Build.VERSION_CODES.P)
    public void takePhoto(){
        File file = new File(activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "Image_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(file).build();

        if (imageCapture != null) {
            imageCapture.takePicture(outputFileOptions, activity.getMainExecutor(), new ImageCapture.OnImageSavedCallback() {

                @Override
                public void onImageSaved(@NonNull @NotNull ImageCapture.OutputFileResults outputFileResults) {
                    Toast.makeText(activity,"Zapisano zdjęcie jako"+ file.getAbsolutePath(), Toast.LENGTH_SHORT).show();

                    StringBuilder sb = new StringBuilder();

                    for (Map.Entry<Rect, String> entry : _rectList.entrySet()) {
                        sb.append(entry.getValue());
                        sb.append("\n");
                    }
                    UnicodeExifInterface oldExif = null; //https://github.com/ddyos/UnicodeExifInterface
                    try {
                        oldExif = new UnicodeExifInterface(file.getAbsolutePath());
                        byte[] strToUnicode = sb.toString().getBytes();
                        String unicodeStr = new String(strToUnicode, StandardCharsets.UTF_8);
                        oldExif.setAttribute(UnicodeExifInterface.TAG_USER_COMMENT, unicodeStr);
                        oldExif.saveAttributes();
                        Log.d("tag2",oldExif.getAttribute(UnicodeExifInterface.TAG_USER_COMMENT));
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.d("jestem","cos sie popsulo");
                    }
                }

                @Override
                public void onError(@NonNull @NotNull ImageCaptureException exception) {
                    Toast.makeText(activity,"Nie udało się zapisać zdjęcia: "+ file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
