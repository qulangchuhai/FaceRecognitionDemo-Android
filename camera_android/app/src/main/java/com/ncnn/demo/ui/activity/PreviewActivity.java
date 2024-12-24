package com.ncnn.demo.ui.activity;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.ncnn.demo.R;
import com.ncnn.demo.impl.IDetectCallback;
import com.ncnn.demo.widget.MyCameraView;

public class PreviewActivity extends AppCompatActivity implements IDetectCallback {

    MyCameraView mSmartCameraView;

    protected ImageView mMaskImageView;
    protected ImageView mFaceImageView;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);
        changeAppBrightness(255);

        findViews();
        mSmartCameraView.startDetect();
        mSmartCameraView.setDetectCallback(this);
    }

    public void changeAppBrightness(int brightness) {
        Window window = this.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        if (brightness == -1) {
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        } else {
            lp.screenBrightness = (brightness <= 0 ? 1 : brightness) / 255f;
        }
        window.setAttributes(lp);
    }

    protected void findViews() {
        mSmartCameraView = findViewById(R.id.camera_view_id);
        mMaskImageView = findViewById(R.id.mask_view);
        mFaceImageView = findViewById(R.id.face_image_view);

        View mBackView = findViewById(R.id.back_view_camera_activity);
        mBackView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

    }


    @Override
    protected void onResume() {
        if (mSmartCameraView != null) {
            mSmartCameraView.onResume();
        }
        super.onResume();
    }

    @Override
    protected void onPause() {
        if (mSmartCameraView != null) {
            mSmartCameraView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {

        if (mSmartCameraView != null) {
            mSmartCameraView.onDestory();
        }

        super.onDestroy();
    }


    @Override
    public void onSuccess(Bitmap bitmap) {
        mFaceImageView.setImageBitmap(bitmap);
    }
}
