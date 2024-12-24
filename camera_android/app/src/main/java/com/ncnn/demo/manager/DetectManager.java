package com.ncnn.demo.manager;

import android.app.Activity;
import android.content.Intent;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.ncnn.demo.impl.IResultAction;
import com.ncnn.demo.impl.OnActivityResultCallback;
import com.ncnn.demo.ui.activity.PreviewActivity;
import com.ncnn.demo.ui.fragment.RequestManagerFragment;
import com.ncnn.demo.ui.fragment.SupportRequestManagerFragment;


public class DetectManager {
    Activity mActivity;
    private static final String TAG = DetectManager.class.getSimpleName();

    // subthread, because on some phones, turning on the camera is a time-consuming operation that takes 300-400 milliseconds
    private DetectManager(Activity activity) {
        this.mActivity = activity;
    }

    public static DetectManager with(Activity activity) {
        return new DetectManager(activity);
    }


    public void startDetect() {
        IResultAction resultAction;
        if (mActivity instanceof FragmentActivity) {
            resultAction = getSupportRequestManagerFragment(((FragmentActivity) mActivity).getSupportFragmentManager());
        } else {
            resultAction = getRequestManagerFragment(mActivity.getFragmentManager());
        }
        Intent intent = new Intent(mActivity, PreviewActivity.class);

        resultAction.startActivityForResult(intent, new OnActivityResultCallback() {
            @Override
            public void onActivityResult(int resultCode, Intent data) {


            }
        });
    }

    private SupportRequestManagerFragment getSupportRequestManagerFragment(final FragmentManager fm) {
        SupportRequestManagerFragment current = (SupportRequestManagerFragment) fm.findFragmentByTag(TAG);
        if (current == null) {
            current = new SupportRequestManagerFragment();
            fm.beginTransaction().add(current, TAG).commitAllowingStateLoss();
            fm.executePendingTransactions();
        }
        return current;
    }

    private RequestManagerFragment getRequestManagerFragment(final android.app.FragmentManager fm) {
        RequestManagerFragment current = (RequestManagerFragment) fm.findFragmentByTag(TAG);
        if (current == null) {
            current = new RequestManagerFragment();
            fm.beginTransaction().add(current, TAG).commitAllowingStateLoss();
            fm.executePendingTransactions();
        }
        return current;
    }
}
