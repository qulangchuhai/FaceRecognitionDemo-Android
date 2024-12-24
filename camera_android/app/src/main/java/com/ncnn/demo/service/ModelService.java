package com.ncnn.demo.service;

import android.os.Handler;
import android.os.Looper;

import com.ncnn.demo.init.FaceDetectInitManager;
import com.ncnn.demo.util.FaceFileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ModelService {

    public static final ArrayList<String> MODEL_NAMES = new ArrayList<>();

    static {
        MODEL_NAMES.add("yolov5n-0.5.bin");
        MODEL_NAMES.add("yolov5n-0.5.param");
    }
    private static ModelService sInstance = new ModelService();
    private OnLoadModelListener mOnLoadModelListener;
    private AtomicInteger mDownloadModelCount;
    private Handler mMainHandler;

    private volatile int mLoadStatus = 0; // 0初始 1加载中 2加载完成

    private ModelService() {
        mMainHandler = new Handler(Looper.getMainLooper());

        mDownloadModelCount = new AtomicInteger(0);
    }

    public static ModelService getInstance() {
        return sInstance;
    }

    public void loadModel(OnLoadModelListener loadModelListener) {

        mOnLoadModelListener = loadModelListener; // 取巧的做法

        if (mLoadStatus == 2) {
            runInUIThread(new Runnable() {
                @Override
                public void run() {
                    if (mOnLoadModelListener != null) {
                        mOnLoadModelListener.onLoadSuccessed();
                    }
                }
            });
            return;
        }

        mDownloadModelCount.set(0);
        mLoadStatus = 1;

        File modelDir = FaceFileUtils.getInstance().getFile();

        if (modelDir.exists()) {
            ArrayList<String> unDownloadModels = new ArrayList<>();
            for (String name : MODEL_NAMES) {
                File file = new File(modelDir, name);
                if (file.exists()) {
                    notifyDataChanged();
                    continue;
                }
                unDownloadModels.add(name);
            }
            if (!unDownloadModels.isEmpty()) {
                fetchModel(unDownloadModels);
            }
        } else {
            modelDir.mkdirs();
            fetchModel(MODEL_NAMES);
        }
    }

    private void fetchModel(final ArrayList<String> modelNames) {
        _fetchModel(modelNames);
    }

    private void _fetchModel(ArrayList<String> modelNames) {
        for (String name : modelNames) {
            writeToLocal(name);
        }
    }

    private void writeToLocal(String name) {
        File modelDir = FaceFileUtils.getInstance().getFile();
        FileOutputStream os = null;
        InputStream myInput = null;
        try {
            if (!modelDir.exists()) {
                modelDir.mkdirs();
            }
            File file = new File(modelDir, name);
            if (file.exists()) {
                file.delete();
            }
            file.createNewFile();

            os = new FileOutputStream(file);
            myInput = FaceDetectInitManager.context.getAssets().open("faceDetect/" + name);
            byte[] buffer = new byte[1024];
            int length = myInput.read(buffer);
            while (length > 0) {
                os.write(buffer, 0, length);
                length = myInput.read(buffer);
            }
            os.flush();
            myInput.close();
            os.close();
            notifyDataChanged();
        } catch (Exception e) {
            runInUIThread(new Runnable() {
                @Override
                public void run() {
                    if (mOnLoadModelListener != null) {
                        mOnLoadModelListener.onLoadFailed();
                    }
                }
            });
        } finally {
            if (os != null) {
                try {
                    os.close();
                    os = null;
                } catch (Exception e) {

                }
            }
            if (myInput != null) {
                try {
                    myInput.close();
                    myInput = null;
                } catch (Exception e) {

                }
            }
        }

    }

    private void notifyDataChanged() {
        runInUIThread(new Runnable() {
            @Override
            public void run() {
                if (mDownloadModelCount.incrementAndGet() ==MODEL_NAMES.size()) {
                    mLoadStatus = 2;
                    if (mOnLoadModelListener != null) {
                        mOnLoadModelListener.onLoadSuccessed();
                    }
                }
            }
        });
    }


    private void runInUIThread(Runnable r) {
        if (mMainHandler != null && r != null) {
            mMainHandler.post(r);
        }
    }

    public interface OnLoadModelListener {
        void onLoadSuccessed();

        void onLoadFailed();
    }

}

