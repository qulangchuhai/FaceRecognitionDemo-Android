package com.ncnn.demo.impl;

import android.content.Intent;

import androidx.annotation.NonNull;

public interface IResultAction {
    void startActivityForResult(@NonNull Intent intent, @NonNull OnActivityResultCallback callback);
}
