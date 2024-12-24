package com.ncnn.demo.init;

import android.app.Application;

public class FaceDetectInitManager {
    public static Application context;
    public static void init(Application application) {
        context = application;
    }


}
