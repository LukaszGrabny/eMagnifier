package com.polsl.emagnifier;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;


public class MenuActivity extends AppCompatActivity {
    private static final int MY_CAMERA_REQUEST_CODE = 100;
    private static final int MY_MEMORY_REQUEST_CODE = 150;
    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_menu);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_DENIED) {
            requestPermissions(new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE}, 150);
        }

        Button btnRead = findViewById(R.id.toReadBtn);
        Button btnShow = findViewById(R.id.toShowBtn);
        Button btnRecord = findViewById(R.id.toRecordBtn);
        Button btnSaveFile = findViewById(R.id.toSaveFileBtn);

        btnRead.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent= new Intent(MenuActivity.this, ReadActivity.class);
                startActivity(intent);
            }
        });
        btnShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent= new Intent(MenuActivity.this, ShowActivity.class);
                startActivity(intent);
            }
        });
        btnRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent= new Intent(MenuActivity.this, RecordActivity.class);
                startActivity(intent);
            }
        });
        btnSaveFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent= new Intent(MenuActivity.this, SaveFileActivity.class);
                startActivity(intent);
            }
        });
       /* btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MenuActivity.this, SettingsActivity.class);
                startActivity(intent);

            }
        });*/
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Zezwolono na dostęp do aparatu", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Nie zezwolono na dostęp do aparatu", Toast.LENGTH_LONG).show();
                this.finishAffinity();
                System.exit(0);
            }
        }
        if (requestCode == MY_MEMORY_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Zezwolono na zapis plików na urządzeniu", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Nie zezwolono na zapis plików na urządzeniu", Toast.LENGTH_LONG).show();
                this.finishAffinity();
                System.exit(0);
            }
        }
    }


}