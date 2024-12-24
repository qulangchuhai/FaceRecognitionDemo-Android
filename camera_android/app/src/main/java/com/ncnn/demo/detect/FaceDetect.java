package com.ncnn.demo.detect;

public class FaceDetect {
    static {
        System.loadLibrary("Face");
    }

    public native boolean loadModel(String modelDir);

    public native FaceInfoList faceDetectPoint(byte[] imageDate, int imageWidth, int imageHeight);

    public native void release();
}
