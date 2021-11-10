package com.polsl.emagnifier;

import android.app.Dialog;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;


public class ShowActivity extends AppCompatActivity{
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
    CameraSource cameraSource;
    SurfaceOverlay surfaceOverlay;
    SpeechSynthesis speechSynthesis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_show);
        textView = findViewById(R.id.text);
        cameraSource= new CameraSource(this,this);
        speechSynthesis = new SpeechSynthesis(this);

        cameraSource.startCamera();

        OrientationEventListener orientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
            cameraSource.setOrientation(orientation);
            }
        };
        orientationEventListener.enable();
       // TextView dialogTextView = (TextView) findViewById(R.id.dialogTView);
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
                cameraSource.fillAllWords();
            }
        }));
    }
    public static class TextDialog  extends DialogFragment {
        public String textToDialog;
        static public TextDialog newInstance(String textToDialog){
            TextDialog textDialog= new TextDialog();
            Bundle args = new Bundle();
            args.putString("tekst", textToDialog);
            textDialog.setArguments(args);
            return textDialog;
        }
        @Override
        public @NotNull Dialog onCreateDialog(Bundle savedInstanceState) {
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        cameraSource.passEvent(event);
        return false;
    }

}
