package com.ncnn.demo.ui.fragment;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.ncnn.demo.impl.IResultAction;
import com.ncnn.demo.impl.OnActivityResultCallback;


public class RequestManagerFragment extends Fragment implements IResultAction {

    private int requestForResultCode = 100;
    private OnActivityResultCallback forResultCallback;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void startActivityForResult(@NonNull Intent intent, @NonNull OnActivityResultCallback callback) {
        this.forResultCallback = callback;
        startActivityForResult(intent, requestForResultCode);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (forResultCallback == null) {
            return;
        }
        forResultCallback.onActivityResult(resultCode, data);
    }
}

