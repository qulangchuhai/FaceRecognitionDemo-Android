package com.ncnn.demo.widget;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;


import com.ncnn.demo.detect.FaceDetect;
import com.ncnn.demo.detect.FaceInfoList;
import com.ncnn.demo.impl.IDetectCallback;
import com.ncnn.demo.service.ModelService;
import com.ncnn.demo.util.FaceFileUtils;
import com.ncnn.demo.util.ImageUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 对于 TextureView 由于其作为 View hierachy 中的一个普通 View。因此我们可以通过操作 matrix ，最后再调用 TextureView#setTransform 来改变其方向;
 * feature 优化使用libyuv处理视频数据，效率更高
 */
public class MyCameraView extends TextureView implements View.OnLayoutChangeListener, Camera.PreviewCallback {

    private static final int MIN_FPS = 15 * 1000;

    private Activity mActivity;

    private boolean isSurfaceTextureAvailable = false;
    // 界面测量尺寸
    private int mMeasureWidth;
    private int mMeasureHeight;
    // 等待开启相机
    private boolean isWaitingOpenCamera = false;
    // 相机
    private Camera mCamera;
    // 相机预览尺寸
    private Camera.Size mPreviewSize;
    // 相机旋转角度
    private int mCameraAngle;
    // 前置摄像头
    private final int mCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
    // 是否开启自动对焦
    private boolean isAutoFocusEnable = true;
    // 自动对焦间隔 默认5秒
    private long mAutoFocusInterval = 5 * 1000;
    // 对焦回调
    private Camera.AutoFocusCallback mAutoFocusCallback;
    // 处理对焦handler
    private FocusHandler mFocusHandler;
    // 第一帧
    private boolean isFirstPreviewFrame = true;
    private int mPreviewWidth;
    private int mPreviewHeight;
    // 是否正在处理
    private boolean isProcessingFrame = false;
    // 开始检测
    private volatile int mDetectStatus = 0; // 0初始状态，1准备完成，2正在识别, 3识别成功，-1识别超时
    // 读取下一帧的任务
    private Runnable mPostInferenceCallback;

    private Handler mMainHandler;
    private Handler mHandler; // 识别线程
    private HandlerThread mHandlerThread;
    private IDetectCallback mDetectCallback;
    // 保存图片
    private ExecutorService mExecutorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() + 1);

    private SurfaceTextureListener mSurfaceTextureListener;

    private int mCameraStatus; // 0缺省 1opening 2open 3预览
    private volatile FaceDetect mFaceDetect;


    public MyCameraView(Context context) {
        this(context, null);
    }

    /*
     * 在view初始化的时候就打开相机
     * */
    public MyCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mSurfaceTextureListener = new SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                isSurfaceTextureAvailable = true;
                startPreview();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                release();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        };

        if (context instanceof Activity) {
            mActivity = (Activity) context;
        }

        setSurfaceTextureListener(mSurfaceTextureListener);
        addOnLayoutChangeListener(this);
        setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // 强制聚焦
                if (mCamera == null || mCameraStatus != 3) { // 相机没有打开，获取没有进行预览
                    return;
                }
                getFocusHandler().sendForceFocusMessage();
            }
        });

        mMainHandler = new Handler(Looper.getMainLooper());

        mHandlerThread = new HandlerThread("detect", Process.THREAD_PRIORITY_FOREGROUND);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());


        open(); // 开启相机，这个可能属于一个比较耗时的操作。。。 后续优化可以放在线程中执行，webrtc就是这么做的

        mDetectStatus = 0;
        mFaceDetect = new FaceDetect();
    }


    protected synchronized void runInBackground(final Runnable r) {
        if (mHandler != null && r != null) {
            mHandler.post(r);
        }
    }

    protected synchronized void runInUIThread(Runnable r) {
        if (mMainHandler != null && r != null) {
            mMainHandler.post(r);
        }
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        int measuredWidth = getMeasuredWidth();
        int measuredHeight = getMeasuredHeight();
        boolean sizeChanged = mMeasureWidth != measuredWidth || mMeasureHeight != measuredHeight;
        if (sizeChanged) {
            mMeasureWidth = getMeasuredWidth();
            mMeasureHeight = getMeasuredHeight();
            if (isWaitingOpenCamera) {
                open();
            } else if (mCamera != null) {
                transformTexture();
            }
        }
    }

    public void setDetectCallback(IDetectCallback detectCallback) {
        mDetectCallback = detectCallback;
    }


    public void startDetect() {


        if (mDetectStatus == 0) {
            ModelService.getInstance().loadModel(new ModelService.OnLoadModelListener() {
                @Override
                public void onLoadSuccessed() {
                    loadModel();
                    mDetectStatus = 1;
                }

                @Override
                public void onLoadFailed() {

                }
            });
        }

    }

    private void loadModel() {
        File modelDir = FaceFileUtils.getInstance().getFile();
        String modelPath = modelDir.getAbsolutePath();
        try {
            //加载模型
            mFaceDetect.loadModel(modelPath);
        } catch (Exception e) {
        }
    }


    /*
     * 此方法这个逻辑int detectResult = mSmartDetector.detect(yuv, width, height);
     * 将采集到的相机数据交给SmartDetector_detect c++代码处理
     * 并会返回一个处理结果
     * */
    private void processImage(final byte[] data) {

        // 在子线程进行检测
        runInBackground(new Runnable() {
            @Override
            public void run() {

                if (mFaceDetect != null && (mDetectStatus == 1 || mDetectStatus == 2)) { // 准备就绪 或者 是识别中

                    mDetectStatus = 2;

                    byte[] yuv;
                    int width = mPreviewHeight;
                    int height = mPreviewWidth;


                    if (mCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT) { // 前置
                        yuv = ImageUtils.rotateYUVDegree270AndMirror(data, mPreviewWidth, mPreviewHeight);
                    } else { // 后置
                        yuv = ImageUtils.rotateYUVDegree90(data, mPreviewWidth, mPreviewHeight);
                    }
                    try {

                        if (mPostInferenceCallback != null) {
                            mPostInferenceCallback.run();
                        }

                        FaceInfoList faceInfoList = mFaceDetect.faceDetectPoint(yuv, width, height);
                        if (mPostInferenceCallback != null) {
                            mPostInferenceCallback.run();
                        }
                        savePic(yuv,faceInfoList);


                    } catch (Exception e) {
                    }
                }

            }
        });
    }

    private void drawPoint(FaceInfoList faceInfoList, Bitmap rgbFrameBitmap) {
        int left, top, right, bottom;
        Canvas canvas = new Canvas(rgbFrameBitmap);
        for (FaceInfoList.FaceInfo faceInfo : faceInfoList.getFaceList()) {
            Paint paint = new Paint();
            left = faceInfo.getFaceArea().getX();
            top = faceInfo.getFaceArea().getY();
            right = faceInfo.getFaceArea().getX() + faceInfo.getFaceArea().getWidth();
            bottom = faceInfo.getFaceArea().getY() + faceInfo.getFaceArea().getHeight();
            paint.setColor(Color.BLUE);
            paint.setStyle(Paint.Style.STROKE);//不填充
            paint.setStrokeWidth(5);  //线的宽度
            canvas.drawRect(left, top, right, bottom, paint);

            float[] points = new float[faceInfo.getPointList().size() * 2];
            for (int k = 0; k < faceInfo.getPointList().size(); k++) {
                points[k * 2] = faceInfo.getPointList().get(k).getX();
                points[k * 2 + 1] = faceInfo.getPointList().get(k).getY();
            }
            paint.setColor(Color.GREEN);
            paint.setStrokeWidth(12);
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawPoints(points, paint);//画点

        }
    }


    private void savePic(final byte[] data, FaceInfoList faceInfoList) {

        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {

                int width = mPreviewHeight;
                int height = mPreviewWidth;
                int[] rgbBytes = new int[width * height];
                ImageUtils.convertYUV420SPToARGB8888(data, width, height, rgbBytes);
                Bitmap rgbFrameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                rgbFrameBitmap.setPixels(rgbBytes, 0, width, 0, 0, width, height);

                if (faceInfoList != null && faceInfoList.getFaceList().size() > 0) {
                    drawPoint(faceInfoList, rgbFrameBitmap);
                }

                if (mDetectCallback != null) {
                    runInUIThread(new Runnable() {
                        @Override
                        public void run() {
                            mDetectCallback.onSuccess(rgbFrameBitmap);
                        }
                    });
                }

            }
        });
    }

    /*
     *  获取实时的相机数据，交给processImage方法处理
     * */
    @Override
    public void onPreviewFrame(final byte[] data, Camera camera) {
        // 这个时候拿到的是YUV数据，YUV420SP ==> NV21

        if (isProcessingFrame || mCamera == null) { // 正在处理，直接丢弃后面的数据, 超时也不做处理
            return;
        }

        if (isFirstPreviewFrame) {
            Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
            mPreviewWidth = previewSize.width;
            mPreviewHeight = previewSize.height;
            isFirstPreviewFrame = false;
        }

        isProcessingFrame = true; // 开始处理数据

        mPostInferenceCallback =
                new Runnable() {
                    @Override
                    public void run() {

                        if (data == null || mCamera == null) {
                            return;
                        }

                        mCamera.addCallbackBuffer(data); // 必须调用  否则 onPreviewFrame 这个方法不会被回调 感觉和 isProcessingFrame 这个标记起到了同样的作用
                        isProcessingFrame = false;
                    }
                };
        processImage(data);
    }

    public void onResume() {
        startPreview();
    }

    public void onPause() {
        stopPreview();
    }

    public void onDestory() {
        release();
    }


    private int getViewWidth() {
        return mMeasureWidth;
    }

    private int getViewHeight() {
        return mMeasureHeight;
    }

    private void transformTexture() { // 进行一次缩放处理，保证不变形，问题可能会有白边/黑边问题
        if (this.mPreviewSize != null) {
            float viewWidth = (float) this.getViewWidth();
            float viewHeight = (float) this.getViewHeight();
            float ratio = this.getRatio(this.mPreviewSize);
            RectF toRect = new RectF(0.0F, 0.0F, viewHeight, viewWidth);
            RectF fromRect;
            if (this.isPortrait()) {
                fromRect = new RectF(0.0F, 0.0F, viewHeight, ratio * viewHeight);
            } else {
                fromRect = new RectF(0.0F, 0.0F, viewWidth / ratio, viewWidth);
            }

            Matrix matrix = new Matrix();
            matrix.setRectToRect(fromRect, toRect, Matrix.ScaleToFit.FILL);
            this.setTransform(matrix);
        }
    }

    private void open() { // 打开相机
        try {

            if (mMeasureWidth <= 0) {
                isWaitingOpenCamera = true;
                return;
            }

            isWaitingOpenCamera = false;

            if (mCameraStatus == 0) {
                mCameraStatus = 1;
                mCamera = Camera.open(mCameraId); // 开启前置摄像头
                Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                Camera.getCameraInfo(mCameraId, cameraInfo);
                Camera.Parameters params = mCamera.getParameters();
                mPreviewSize = calBestPreviewSize(params);
                params.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
                int[] fps = calBestFpsRange(params);
                params.setPreviewFpsRange(fps[0], fps[1]); // 防止厂商修改底层代码，所以设置一下帧率范围，比如部分OV厂商低于15FPS屏幕暗
                mCameraAngle = getCameraAngle(mCameraId);
                mCamera.setDisplayOrientation(mCameraAngle);
                mCamera.setParameters(params);
                transformTexture();
                mCameraStatus = 2;

                if (isSurfaceTextureAvailable) {
                    startPreview();
                }

            }
        } catch (Exception e) { // try  catch 防止厂商修改底层代码，
            Log.e("smart", "open: " + e.toString());
        }
    }

    private void startPreview() {
        if (mCamera != null && mCameraStatus == 2 && isSurfaceTextureAvailable) { // 相机正常开启
            try {
                mCamera.setPreviewTexture(getSurfaceTexture());
                // setPreviewCallback 只要预览处于活动状态，就会重复调用回调。这种方法可以随时调用，保证预览是实时的。
                // setPreviewCallbackWithBuffer 在摄像头开启时增加一个回调函数,在每一帧出现时调用.通过addCallbackBuffer(byte[])使用一个缓存容器来显示这些数据.
                // 通过内存复用来提高预览的效率,但是如果没有调用这个方法addCallbackBuffer(byte[])，帧回调函数就不会被调用，也就是说在每一次回调函数调用后都必须调用addCallbackBuffer(byte[])
                mCamera.setPreviewCallbackWithBuffer(this);
                Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
                mCamera.addCallbackBuffer(new byte[ImageUtils.getYUVByteSize(previewSize.width, previewSize.height)]);
                mCamera.startPreview();
                mCameraStatus = 3;
                getFocusHandler().sendAutoFocusMessage();
            } catch (Exception e) {
            }
        }
    }

    private void stopPreview() {
        if (mCamera != null && mCameraStatus == 3) {
            try {
                mCamera.stopPreview();
                mCamera.setPreviewCallbackWithBuffer(null);
                mCameraStatus = 2;
            } catch (Exception e) {
            }
        }
    }

    private void release() {
        isSurfaceTextureAvailable = false;
        isFirstPreviewFrame = true;
        mDetectStatus = 0;
        if (mCamera != null) {
            getFocusHandler().removeFocusMessage();
            mCamera.stopPreview();
            mCamera.setPreviewCallbackWithBuffer(null);
            mCamera.release();
            mCamera = null;
            mCameraStatus = 0;
        }

        if (mFaceDetect != null) {
            try {
                mFaceDetect.release();
                mFaceDetect = null;
            } catch (Exception e) {
            }
        }

    }

    /**
     * 是否是竖屏
     */
    private boolean isPortrait() {
        return getResources().getConfiguration().orientation == 1;
    }

    private int getPreviewWidth(Camera.Size size) {
        return isPortrait() ? size.height : size.width;
    }

    private int getPreviewHeight(Camera.Size size) {
        return isPortrait() ? size.width : size.height;
    }

    private float getRatio(Camera.Size size) {
        return (float) getPreviewWidth(size) / (float) getPreviewHeight(size);
    }

    /**
     * 计算最佳帧率范围
     */
    private int[] calBestFpsRange(Camera.Parameters camPara) {

        List<int[]> allSupportedFps = camPara.getSupportedPreviewFpsRange();

        int[] bestFps = new int[2];

        for (int[] supportFps : allSupportedFps) {
            if (supportFps[1] < MIN_FPS) {
                continue;
            }

            bestFps = supportFps;

        }

        if (bestFps[1] <= 0) {
            bestFps = allSupportedFps.get(0);
        }
        return bestFps;
    }

    /**
     * 计算最佳尺寸
     */
    private Camera.Size calBestPreviewSize(Camera.Parameters camPara) {
        List<Camera.Size> allSupportedSize = camPara.getSupportedPreviewSizes(); // 获取当前相机支持的预览尺寸
        Camera.Size bestSize = null; // 记录最佳尺寸

        final float screenRatio = (float) getViewWidth() / (float) getViewHeight(); // 计算当前view的宽高比

        Collections.sort(allSupportedSize, new Comparator<Camera.Size>() { // 对当前所有支持的尺寸进行排序 越接近1 越靠近前
            public int compare(Camera.Size size1, Camera.Size size2) {
                float ratio1 = getRatio(size1);
                float ratio2 = getRatio(size2);
                float diff1 = Math.abs(screenRatio - ratio1);
                float diff2 = Math.abs(screenRatio - ratio2);
                return Float.compare(diff1, diff2);
            }
        });

        List<Camera.Size> appropriateSizeList = new ArrayList(); // 适合的尺寸集合

        float proximateRatio = 0.0F;
        if (allSupportedSize.size() > 0) {
            proximateRatio = getRatio(allSupportedSize.get(0));
        }

        for (Camera.Size size : allSupportedSize) {
            float ratio = getRatio(size);
            float diff = Math.abs(ratio - proximateRatio);
            if ((double) diff < 0.1D) {
                appropriateSizeList.add(size);
            }
        }

        Collections.sort(appropriateSizeList, new Comparator<Camera.Size>() { // 对合适的尺寸进行排序
            int differSize(Camera.Size size) {
                return Math.abs(getViewWidth() - getPreviewWidth(size) + (getViewHeight() - getPreviewHeight(size)));
            }

            public int compare(Camera.Size o1, Camera.Size o2) {
                float sizeSum1 = (float) differSize(o1);
                float sizeSum2 = (float) differSize(o2);
                return Float.compare(sizeSum1, sizeSum2);
            }
        });
        if (appropriateSizeList.size() > 0) {
            bestSize = appropriateSizeList.get(0);
        } else if (allSupportedSize.size() > 0) {
            bestSize = allSupportedSize.get(0);
        }
        return bestSize;
    }

    /**
     * 获取相机旋转角度
     */
    private int getCameraAngle(int cameraId) {
        int rotateAngle = 90;
        if (mActivity != null) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, info); // info.orientation 相机图像方向
            // 获取当前屏幕方向
            int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
            int degrees = 0;
            switch (rotation) {
                case Surface.ROTATION_0:
                    degrees = 0;
                    break;
                case Surface.ROTATION_90:
                    degrees = 90;
                    break;
                case Surface.ROTATION_180:
                    degrees = 180;
                    break;
                case Surface.ROTATION_270:
                    degrees = 270;
            }

            if (1 == cameraId) { // 前置摄像头需要做一次镜像翻转处理
                rotateAngle = (info.orientation + degrees) % 360;
                rotateAngle = (360 - rotateAngle) % 360;
            } else {
                rotateAngle = (info.orientation - degrees + 360) % 360;
            }
        }

        return rotateAngle;
    }

    private Camera.AutoFocusCallback getAutoFocusCallback() {
        if (mAutoFocusCallback == null) {
            mAutoFocusCallback = new Camera.AutoFocusCallback() {
                public void onAutoFocus(boolean success, Camera camera) {
                    getFocusHandler().sendAutoFocusMessage();
                }
            };
        }

        return mAutoFocusCallback;
    }

    private synchronized FocusHandler getFocusHandler() {
        if (mFocusHandler == null) {
            mFocusHandler = new FocusHandler(Looper.getMainLooper());
        }
        return mFocusHandler;
    }


    private final class FocusHandler extends Handler {

        private static final int MESSAGE_AUTO_FOCUS = 0x1001;
        private static final int MESSAGE_FORCE_FOCUS = 0x1002;

        FocusHandler(Looper looper) {
            super(looper);
        }

        void sendAutoFocusMessage() { // 发送自动对焦
            sendEmptyMessageDelayed(MESSAGE_AUTO_FOCUS, mAutoFocusInterval);
        }

        void sendForceFocusMessage() { // 发送手动强制对焦
            sendEmptyMessage(MESSAGE_FORCE_FOCUS);
        }

        void removeFocusMessage() {
            removeMessages(MESSAGE_AUTO_FOCUS);
            removeMessages(MESSAGE_FORCE_FOCUS);
        }

        private void performFocus() {
            try {
                mCamera.autoFocus(getAutoFocusCallback());
            } catch (Exception e) { // 避免傻屌厂商乱改 相机操作尽量try catch一下
            }
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_AUTO_FOCUS: // 自动对焦
                    if (mCamera != null && mCameraStatus == 3 && isAutoFocusEnable) { // 相机打开，预览，开启了自动对焦模式
                        performFocus();
                    }
                    break;
                case MESSAGE_FORCE_FOCUS: // 手动点击以后强制手动对焦
                    if (mCamera != null && mCameraStatus == 3) { // 相机打开，预览
                        performFocus();
                    }
                    break;
            }
        }
    }
}
