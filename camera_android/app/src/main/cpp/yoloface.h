#pragma once
#ifndef FACE_RECOGNITION_YOLOFACE_LIKE_RETINAFACE_STYLE_H
#define FACE_RECOGNITION_YOLOFACE_LIKE_RETINAFACE_STYLE_H

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/highgui/highgui.hpp>

#include "include/net.h"

struct Object {
    cv::Rect_<float> rect;
    int label;
    float prob;
    std::vector<cv::Point2f> pts;
};


class YoloFace
{
public:
    YoloFace();

    ~YoloFace();

    int load(const std::string& model_path, int target_size, const float* mean_vals, const float* norm_vals, bool use_gpu = false);

    int detect(const ncnn::Mat& rgb, std::vector<Object>& objects, int w, int h, float scale, float prob_threshold = 0.4f, float nms_threshold = 0.5f);

private:

    ncnn::Net yoloface;

    int target_size = 640;
    float mean_vals[3] = { 127.f, 127.f, 127.f };
    float norm_vals[3] = { 1 / 255.f, 1 / 255.f, 1 / 255.f };
    int image_w;
    int image_h;
    int in_w;
    int in_h;
    bool use_gpu_;

    ncnn::UnlockedPoolAllocator blob_pool_allocator;
    ncnn::PoolAllocator workspace_pool_allocator;

};

#endif // NANODET_H


