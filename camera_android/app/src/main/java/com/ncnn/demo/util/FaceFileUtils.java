package com.ncnn.demo.util;


import com.ncnn.demo.init.FaceDetectInitManager;

import java.io.File;

public class FaceFileUtils {

    private static class SingletonHolder {
        private static final FaceFileUtils INSTANCE = new FaceFileUtils();
    }

    public static final FaceFileUtils getInstance() {
        return FaceFileUtils.SingletonHolder.INSTANCE;
    }


    private File modelDir;

    public File getFile() {
        if (modelDir == null) {
            modelDir = FaceDetectInitManager.context.getExternalFilesDir("faceDetect");
            if (modelDir == null) {
                modelDir = new File(FaceDetectInitManager.context.getFilesDir().getAbsolutePath() + File.separator + "faceDetect" + File.separator);
            }
        }
        return modelDir;
    }
}
