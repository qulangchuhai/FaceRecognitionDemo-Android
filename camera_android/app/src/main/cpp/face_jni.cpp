
#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <opencv2/core.hpp>
#include <opencv2/imgcodecs.hpp>
#include <opencv2/highgui.hpp>

using namespace std;

#include "net.h"
#include "yoloface.h"


//#define IMAGE_HEIGHT 480
//#define IMAGE_WIDTH  640

#define TAG "FaceReconSo"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,TAG,__VA_ARGS__)
static YoloFace *g_yoloface;
//sdk是否初始化成功
bool detection_sdk_init_ok = false;


struct BoundingBox {
    float left, top, right, bottom;
};

extern "C" {

//初始化模型
JNIEXPORT jboolean
JNICALL

Java_com_ncnn_demo_detect_FaceDetect_loadModel(JNIEnv *env, jobject instance,
                                                        jstring faceDetectionModelPath_) {
    LOGD("JNI开始人脸检测模型初始化");
    //如果已初始化则直接返回
    if (detection_sdk_init_ok) {
        LOGD("人脸检测模型已经导入");
        return true;
    }
    jboolean tRet = false;
    if (NULL == faceDetectionModelPath_) {
        LOGD("导入的人脸检测的目录为空");
        return tRet;
    }

    //获取人脸检测模型的绝对路径的目录（不是/aaa/bbb.bin这样的路径，是/aaa/)
    const char *faceDetectionModelPath = env->GetStringUTFChars(faceDetectionModelPath_, 0);
    if (NULL == faceDetectionModelPath) {
        return tRet;
    }

    string tFaceModelDir = faceDetectionModelPath;
    string tLastChar = tFaceModelDir.substr(tFaceModelDir.length() - 1, 1);
    //目录补齐/
    if ("\\" == tLastChar) {
        tFaceModelDir = tFaceModelDir.substr(0, tFaceModelDir.length() - 1) + "/";
    } else if (tLastChar != "/") {
        tFaceModelDir += "/";
    }
    LOGD("init, tFaceModelDir=%s", tFaceModelDir.c_str());

    //初始化人脸检测模型
    //yolov5face input normalization parameters
    float mean_vals[3] = {127.f, 127.f, 127.f};
    float norm_vals[3] = {1 / 255.f, 1 / 255.f, 1 / 255.f};

    //model initialization
    g_yoloface = new YoloFace();
    g_yoloface->load(tFaceModelDir, 640, mean_vals, norm_vals, false);

    env->ReleaseStringUTFChars(faceDetectionModelPath_, faceDetectionModelPath);
    detection_sdk_init_ok = true;
    tRet = true;
    return tRet;
}



void _release() {
}

JNIEXPORT void JNICALL
Java_com_ncnn_demo_detect_FaceDetect_release(JNIEnv *env, jobject thiz) {
    _release();
}
}


extern "C"
JNIEXPORT jobject JNICALL
Java_com_ncnn_demo_detect_FaceDetect_faceDetectPoint(JNIEnv *env, jobject instance,
                                                              jbyteArray yuv,
                                                              jint imageWidth, jint imageHeight) {

    int input_size = 640;

    // letterbox pad to multiple of 32
    int w = imageWidth;
    int h = imageHeight;
    float scale = 1.f;
    if (w > h) {
        scale = (float) input_size / w;
        w = input_size;
        h = h * scale;
    } else {
        scale = (float) input_size / h;
        h = input_size;
        w = w * scale;
    }

    //将yuv byte数组转为jbyte*
    jbyte *yuvBytes = env->GetByteArrayElements(yuv, nullptr);
    //jbyte* 强转char*
    unsigned char *yuvChars = (unsigned char *) yuvBytes;
    auto *faceImageCharDate = new unsigned char[(int) imageHeight * (int) imageWidth * 3];
    //ncnn 将yuv 逐句转为rgb格式
    ncnn::yuv420sp2rgb(yuvChars, imageWidth, imageHeight, faceImageCharDate);

    //ncnn 将图像转为ncnn::Mat格式
    ncnn::Mat ncnn_img;
    ncnn_img = ncnn::Mat::from_pixels(faceImageCharDate, ncnn::Mat::PIXEL_RGB, imageWidth,
                                      imageHeight);

    ncnn::Mat in = ncnn::Mat::from_pixels_resize(faceImageCharDate, ncnn::Mat::PIXEL_RGB,
                                                 imageWidth, imageHeight, w, h);


    //finalBbox 人脸检测信息
    std::vector<Object> finalBbox;
    g_yoloface->detect(in, finalBbox, imageWidth, imageHeight, scale);

    // 创建ArrayList<FaceInfo>
    jclass faceListClass = env->FindClass("java/util/ArrayList");
    jmethodID faceListConstructor = env->GetMethodID(faceListClass, "<init>", "()V");
    jobject faceList = env->NewObject(faceListClass, faceListConstructor);

    for (int i = 0; i < finalBbox.size(); i++) {
        // 创建FaceArea对象
        jclass faceAreaClass = env->FindClass("com/ncnn/demo/detect/FaceInfoList$FaceArea");
        jmethodID faceAreaConstructor = env->GetMethodID(faceAreaClass, "<init>", "(IIII)V");
        jobject faceArea = env->NewObject(faceAreaClass, faceAreaConstructor,
                                          (int) finalBbox[i].rect.x, (int) finalBbox[i].rect.y,
                                          (int) finalBbox[i].rect.width,
                                          (int) finalBbox[i].rect.height);

        // 创建ArrayList<FacePointInfo>（这里先创建一个空的，因为示例中没有FacePointInfo的详细构造）
        jclass pointListClass = env->FindClass("java/util/ArrayList");
        jmethodID pointListConstructor = env->GetMethodID(pointListClass, "<init>", "()V");
        jobject pointList = env->NewObject(pointListClass, pointListConstructor);

        for (int j = 0; j < finalBbox[i].pts.size(); j++) {
            // 创建FacePointInfo对象
            jclass facePointInfoClass = env->FindClass(
                    "com/ncnn/demo/detect/FaceInfoList$FacePointInfo");
            jmethodID facePointInfoConstructor = env->GetMethodID(facePointInfoClass, "<init>",
                                                                  "(II)V");
            jobject facePointInfo = env->NewObject(facePointInfoClass, facePointInfoConstructor,
                                                   (int) finalBbox[i].pts[j].x,
                                                   (int) finalBbox[i].pts[j].y);
            // 将FacePointInfo对象添加到list中
            jmethodID pointListAdd = env->GetMethodID(pointListClass, "add", "(Ljava/lang/Object;)Z");
            env->CallBooleanMethod(pointList, pointListAdd, facePointInfo);
        }

        // 创建FaceInfo对象
        jclass faceInfoClass = env->FindClass("com/ncnn/demo/detect/FaceInfoList$FaceInfo");
        jmethodID faceInfoConstructor = env->GetMethodID(faceInfoClass, "<init>",
                                                         "(Lcom/ncnn/demo/detect/FaceInfoList$FaceArea;Ljava/util/ArrayList;)V");
        jobject faceInfo = env->NewObject(faceInfoClass, faceInfoConstructor, faceArea, pointList);

        // 将FaceInfo对象添加到faceList中
        jmethodID listAdd = env->GetMethodID(faceListClass, "add", "(Ljava/lang/Object;)Z");
        env->CallBooleanMethod(faceList, listAdd, faceInfo);
    }

    // 创建FaceInfoList对象
    jclass faceInfoListClass = env->FindClass("com/ncnn/demo/detect/FaceInfoList");
    jmethodID faceInfoListConstructor = env->GetMethodID(faceInfoListClass, "<init>",
                                                         "(Ljava/util/ArrayList;)V");
    jobject faceInfoList = env->NewObject(faceInfoListClass, faceInfoListConstructor, faceList);

    //释放内存
    finalBbox.clear();
    in.release();
    ncnn_img.release();
    free(faceImageCharDate);
    env->ReleaseByteArrayElements(yuv, yuvBytes, 0);

    return faceInfoList;

}