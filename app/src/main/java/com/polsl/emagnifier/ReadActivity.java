package com.polsl.emagnifier;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
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
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class ReadActivity extends AppCompatActivity {

    PreviewView mCameraView;
    SurfaceHolder holder;
    SurfaceView surfaceView;
    private static final int MY_CAMERA_REQUEST_CODE = 100;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private @Nullable
    TextToSpeech tts = null;
    int rotation;
    CameraSource cameraSource;
    SurfaceOverlay surfaceOverlay;
    SpeechSynthesis speechSynthesis;
    TextView textView;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Zezwolono na dostęp do aparatu", Toast.LENGTH_LONG).show();
                cameraSource.startCamera();
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

        setContentView(R.layout.activity_read);

        textView = findViewById(R.id.text);
        cameraSource= new CameraSource(this,this, holder);
        speechSynthesis = new SpeechSynthesis(this,this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            cameraSource.startCamera();
        }
        OrientationEventListener orientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
            cameraSource.setOrientation(orientation);
            }
        };

        orientationEventListener.enable();
        FloatingActionButton readButton= findViewById(R.id.readBtn);
        readButton.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(textView.getText().toString().length()>4000){
                    speechSynthesis.speakOut(textView.getText().toString());
                }
                else{

                    Collection<String> chunks = SpeechSynthesis.splitStringBySize(textView.getText().toString(),3999);
                    speechSynthesis.speakOutLong(chunks);
                }
            }
        }));
        FloatingActionButton readAllButton= findViewById(R.id.readAllBtn);
        readAllButton.setOnClickListener((v -> cameraSource.fillAllWords()));
    }

    public void onPause(){
        if(speechSynthesis.tts !=null){
            speechSynthesis.tts.stop();
            speechSynthesis.tts.shutdown();
        }
        super.onPause();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        cameraSource.passEvent(event);
        return false;
    }
}
