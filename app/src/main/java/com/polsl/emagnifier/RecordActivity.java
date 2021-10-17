package com.polsl.emagnifier;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RecordActivity extends AppCompatActivity{


    SurfaceHolder holder;

    ConcurrentHashMap<Rect,String> _rectList = new ConcurrentHashMap<Rect, String>();
    LinkedHashSet<String> stringSet= new LinkedHashSet<>();
    LinkedHashSet<String> tempSet= new LinkedHashSet<>();

    int rotation;
    CameraSource cameraSource;
    SurfaceOverlay surfaceOverlay;
    SpeechSynthesis speechSynthesis;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_record);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
        }
        cameraSource= new CameraSource(this,this, holder);
        speechSynthesis = new SpeechSynthesis(this,this);
        OrientationEventListener orientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
            cameraSource.setOrientation(orientation);
            }
        };
        orientationEventListener.enable();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            cameraSource.startCamera();
        }
        TextView textView = findViewById(R.id.text);
        FloatingActionButton recordButton = findViewById(R.id.recordBtnRecord);
        recordButton.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onClick(View v) {
                    if(!cameraSource.recordBtnClicked) {
                        cameraSource.recordBtnClicked = true;
                        tempSet= new LinkedHashSet<String>();
                        tempSet=cameraSource.RecordAndProcess();
                        recordButton.setImageResource(android.R.drawable.ic_media_pause);
                    }
                    else {
                        cameraSource.recordBtnClicked = false;
                        textView.setText("");
                        StringBuilder recorderStringBuilder = new StringBuilder();
                        stringSet=tempSet;
                        recorderStringBuilder = cameraSource.checkSimilarity(stringSet);
                        ArrayList<String> splitString=cameraSource.spiltParagraph(1,recorderStringBuilder.toString());
                        Set<String> splitStringSet=new LinkedHashSet<>(splitString);
                        StringBuilder sb = new StringBuilder();
                        for (String s : splitStringSet) {
                            sb.append(s);
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
    }

    protected void onPause() {
        super.onPause();
        if(speechSynthesis.tts !=null){
            speechSynthesis.tts.stop();
            speechSynthesis.tts.shutdown();
        }
    }
}
