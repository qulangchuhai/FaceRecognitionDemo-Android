package com.ncnn.demo.ui.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ncnn.demo.manager.DetectManager;
import com.ncnn.demo.R;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.start_detect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (haveCameraPermission()) {
                    startDetect();
                } else {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, 1000);
                }
            }
        });
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (haveCameraPermission()) {
            startDetect();
        } else {
            Toast.makeText(this, getString(R.string.app_open_camera_permission_tip), Toast.LENGTH_LONG).show();
        }
    }

    private boolean haveCameraPermission() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void startDetect() {
        DetectManager.with(this).startDetect();
    }
}
