package com.polsl.emagnifier;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.speech.tts.TextToSpeech;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.fragment.app.DialogFragment;

import com.ddyos.unicode.exifinterface.UnicodeExifInterface;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class SaveFileActivity extends AppCompatActivity{
    TextView textView;
    PreviewView mCameraView;
    SurfaceHolder holder;
    SurfaceView surfaceView;
    Canvas canvas;
    Paint paint;

    public Size imgResolution;
    Float[] scaleFactorWidthHeight= new Float[2];
    LinkedHashMap<Rect,String> _rectList = new LinkedHashMap<Rect, String>();

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private @Nullable
    TextToSpeech tts = null;
    int rotation;
    int rotationDegrees;
    ImageCapture imageCapture= null;
    CameraSource cameraSource;
    SurfaceOverlay surfaceOverlay;
    SpeechSynthesis speechSynthesis;
    private static final int PICK_IMAGE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_save_file);

        cameraSource= new CameraSource(this,this);
        speechSynthesis = new SpeechSynthesis(this);
        cameraSource.startCamera();


        FloatingActionButton readButton= findViewById(R.id.saveImgBtn);
        readButton.setOnClickListener((new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.P)
            @Override
            public void onClick(View v) {
                cameraSource.takePhoto();
            }
        }));
        FloatingActionButton pickImageButton = findViewById(R.id.galleryBtn);
        pickImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openGallery();
            }
        });
    }
    private void openGallery() {
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
        Intent gallery =
                new Intent(Intent.ACTION_GET_CONTENT,
                        android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI);
                gallery.setDataAndType(Uri.fromFile(this.getExternalFilesDir(Environment.DIRECTORY_PICTURES)), "image/*");
        gallery.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(gallery, "Wybierz zdjęcie"),PICK_IMAGE);
       // startActivityForResult(gallery, PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == PICK_IMAGE) {
            Uri imageUri = data.getData();
            //prev.setImageURI(imageUri);
            Log.d("uri2",imageUri.toString());
            DialogFragment newFragment = SaveFileActivity.ImageDialog.newInstance(imageUri);
            newFragment.show(getSupportFragmentManager(), "tekst");
        }
    }
    public static class ImageDialog extends DialogFragment {
        public  Uri imageUri;
        static public SaveFileActivity.ImageDialog newInstance(Uri imageUri){
            SaveFileActivity.ImageDialog imageDialog= new SaveFileActivity.ImageDialog();
            Bundle args = new Bundle();
            args.putString("uri", imageUri.toString());
            imageDialog.setArguments(args);
            return imageDialog;
        }
        @Override
        public @NotNull Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Wykryty tekst")
                    .setView(R.layout.dialog_image);
            LayoutInflater inflater = getActivity().getLayoutInflater();
            imageUri= Uri.parse(getArguments().getString("uri"));
            View content =  inflater.inflate(R.layout.dialog_image, null);
            builder.setView(content);
            ImageView imageView = content.findViewById(R.id.imageView);
            imageView.setImageURI(imageUri);
            TextView textView = content.findViewById(R.id.text);
            textView.setMovementMethod(new ScrollingMovementMethod());
            UnicodeExifInterface oldExif = null;
            try {
                File file = new File(imageUri.getPath());
                String[] pathToProcess = imageUri.getPath().split(":Android/");
                String path = "/storage/emulated/0/Android/" + pathToProcess[1];
                oldExif = new UnicodeExifInterface(path);
                Log.d("tag2",oldExif.getAttribute(UnicodeExifInterface.TAG_USER_COMMENT));
                textView.setText(oldExif.getAttribute(UnicodeExifInterface.TAG_USER_COMMENT));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return builder.create();
        }
    }
}
