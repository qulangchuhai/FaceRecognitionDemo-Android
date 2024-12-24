package com.ncnn.demo;

import android.app.Application;

import com.ncnn.demo.init.FaceDetectInitManager;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        FaceDetectInitManager.init(this);
    }
}
