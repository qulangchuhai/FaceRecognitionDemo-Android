#include "yoloface.h"
#include "cpu.h"

#define clip(x, y) (x < 0 ? 0 : (x > y ? y : x))

static inline float intersection_area(const Object& a, const Object& b)
{
    cv::Rect_<float> inter = a.rect & b.rect;
    return inter.area();
}

static void qsort_descent_inplace(std::vector<Object>& faceobjects, int left, int right)
{
    int i = left;
    int j = right;
    float p = faceobjects[(left + right) / 2].prob;

    while (i <= j)
    {
        while (faceobjects[i].prob > p)
            i++;

        while (faceobjects[j].prob < p)
            j--;

        if (i <= j)
        {
            // swap
            std::swap(faceobjects[i], faceobjects[j]);

            i++;
            j--;
        }
    }

#pragma omp parallel sections
    {
#pragma omp section
        {
            if (left < j) qsort_descent_inplace(faceobjects, left, j);
        }
#pragma omp section
        {
            if (i < right) qsort_descent_inplace(faceobjects, i, right);
        }
    }
}

static void qsort_descent_inplace(std::vector<Object>& faceobjects)
{
    if (faceobjects.empty())
        return;

    qsort_descent_inplace(faceobjects, 0, faceobjects.size() - 1);
}

static void nms_sorted_bboxes(const std::vector<Object>& faceobjects, std::vector<int>& picked, float nms_threshold)
{
    picked.clear();

    const int n = faceobjects.size();

    std::vector<float> areas(n);
    for (int i = 0; i < n; i++)
    {
        areas[i] = faceobjects[i].rect.area();
    }

    for (int i = 0; i < n; i++)
    {
        const Object& a = faceobjects[i];

        int keep = 1;
        for (int j = 0; j < (int)picked.size(); j++)
        {
            const Object& b = faceobjects[picked[j]];

            // intersection over union
            float inter_area = intersection_area(a, b);
            float union_area = areas[i] + areas[picked[j]] - inter_area;
            // float IoU = inter_area / union_area
            if (inter_area / union_area > nms_threshold)
                keep = 0;
        }

        if (keep)
            picked.push_back(i);
    }
}

static inline float sigmoid(float x)
{
    return static_cast<float>(1.f / (1.f + exp(-x)));
}

static void generate_proposals(const ncnn::Mat& anchors, int stride, const ncnn::Mat& in_pad, const ncnn::Mat& feat_blob, float prob_threshold, std::vector<Object>& objects)
{
    const int num_grid = feat_blob.h;

    int num_grid_x;
    int num_grid_y;
    if (in_pad.w > in_pad.h)
    {
        num_grid_x = in_pad.w / stride;
        num_grid_y = num_grid / num_grid_x;
    }
    else
    {
        num_grid_y = in_pad.h / stride;
        num_grid_x = num_grid / num_grid_y;
    }

    const int num_class = feat_blob.w - 5 - 10;

    const int num_anchors = anchors.w / 2;

    for (int q = 0; q < num_anchors; q++)
    {
        const float anchor_w = anchors[q * 2];
        const float anchor_h = anchors[q * 2 + 1];

        const ncnn::Mat feat = feat_blob.channel(q);

        for (int i = 0; i < num_grid_y; i++)
        {
            for (int j = 0; j < num_grid_x; j++)
            {
                const float* featptr = feat.row(i * num_grid_x + j);

                // find class index with max class score
                int class_index = 0;
                float class_score = -FLT_MAX;
                for (int k = 0; k < num_class; k++)
                {
                    float score = featptr[5 + 10 + k];
                    if (score > class_score)
                    {
                        class_index = k;
                        class_score = score;
                    }
                }

                float box_score = featptr[4];

                float confidence = sigmoid(box_score); //* sigmoid(class_score);

                if (confidence >= prob_threshold)
                {
                    // yolov5/models/yolo.py Detect forward
                    // y = x[i].sigmoid()
                    // y[..., 0:2] = (y[..., 0:2] * 2. - 0.5 + self.grid[i].to(x[i].device)) * self.stride[i]  # xy
                    // y[..., 2:4] = (y[..., 2:4] * 2) ** 2 * self.anchor_grid[i]  # wh

                    float dx = sigmoid(featptr[0]);
                    float dy = sigmoid(featptr[1]);
                    float dw = sigmoid(featptr[2]);
                    float dh = sigmoid(featptr[3]);

                    float pb_cx = (dx * 2.f - 0.5f + j) * stride;
                    float pb_cy = (dy * 2.f - 0.5f + i) * stride;

                    float pb_w = pow(dw * 2.f, 2) * anchor_w;
                    float pb_h = pow(dh * 2.f, 2) * anchor_h;

                    float x0 = pb_cx - pb_w * 0.5f;
                    float y0 = pb_cy - pb_h * 0.5f;
                    float x1 = pb_cx + pb_w * 0.5f;
                    float y1 = pb_cy + pb_h * 0.5f;

                    Object obj;
                    obj.rect.x = x0;
                    obj.rect.y = y0;
                    obj.rect.width = x1 - x0;
                    obj.rect.height = y1 - y0;
                    obj.label = class_index;
                    obj.prob = confidence;

                    for (int l = 0; l < 5; l++)
                    {
                        float x = featptr[2 * l + 5] * anchor_w + j * stride;
                        float y = featptr[2 * l + 1 + 5] * anchor_h + i * stride;
                        obj.pts.push_back(cv::Point2f(x, y));
                    }
                    objects.push_back(obj);
                }
            }
        }
    }
}

static bool cmpArea(Object lsh, Object rsh) {
    if (lsh.rect.area() < rsh.rect.area())
        return false;
    else
        return true;
}

static void extractMaxFace(std::vector<Object>& boundingBox_)
{
    if (boundingBox_.empty()) {
        return;
    }
    sort(boundingBox_.begin(), boundingBox_.end(), cmpArea);

    /*for (std::vector<Object>::iterator itx = boundingBox_.begin() + 1; itx != boundingBox_.end();) {
        itx = boundingBox_.erase(itx);
    }*/
}


YoloFace::YoloFace()
{

    blob_pool_allocator.set_size_compare_ratio(0.f);
    workspace_pool_allocator.set_size_compare_ratio(0.f);

}

YoloFace::~YoloFace()
{
    yoloface.clear();
    blob_pool_allocator.clear();
    workspace_pool_allocator.clear();
    //ncnn::destroy_gpu_instance();

}


int YoloFace::load(const std::string& model_path, int _target_size, const float* _mean_vals, const float* _norm_vals, bool use_gpu)
{


    yoloface.clear();
    blob_pool_allocator.clear();
    workspace_pool_allocator.clear();

    //ncnn::create_gpu_instance();

    ncnn::set_cpu_powersave(2);
    ncnn::set_omp_num_threads(ncnn::get_big_cpu_count());

    yoloface.opt = ncnn::Option();


#if NCNN_VULKAN
    yoloface.opt.use_vulkan_compute = use_gpu;
    use_gpu_ = true;

#endif

    yoloface.opt.num_threads = ncnn::get_big_cpu_count();
    yoloface.opt.blob_allocator = &blob_pool_allocator;
    yoloface.opt.workspace_allocator = &workspace_pool_allocator;

    std::string parampath = model_path + "yolov5n-0.5.param";
    std::string modelpath = model_path + "yolov5n-0.5.bin";
    yoloface.load_param(parampath.c_str());
    yoloface.load_model(modelpath.c_str());

    target_size = _target_size;
    mean_vals[0] = _mean_vals[0];
    mean_vals[1] = _mean_vals[1];
    mean_vals[2] = _mean_vals[2];
    norm_vals[0] = _norm_vals[0];
    norm_vals[1] = _norm_vals[1];
    norm_vals[2] = _norm_vals[2];

    return 0;
}


int YoloFace::detect(const ncnn::Mat& in, std::vector<Object>& objects, int w, int h, float scale, float prob_threshold, float nms_threshold)
{

    ncnn::Extractor ex = yoloface.create_extractor();

    // pad to target_size rectangle
    // yolov5/utils/datasets.py letterbox
    int wpad = (in.w + 31) / 32 * 32 - in.w;
    int hpad = (in.h + 31) / 32 * 32 - in.h;
    int img_w = w;
    int img_h = h;

    ncnn::Mat in_pad;
    ncnn::copy_make_border(in, in_pad, hpad / 2, hpad - hpad / 2, wpad / 2, wpad - wpad / 2, ncnn::BORDER_CONSTANT, 114.f);


    in_pad.substract_mean_normalize(0, norm_vals);
    //ncnn::Extractor ex = yoloface.create_extractor();

    //ex.set_vulkan_compute(use_gpu_);

    ex.input(0, in_pad);

    std::vector<Object> proposals;

    // anchor setting from yolov5/models/yolov5s.yaml

    // stride 8
    {
        ncnn::Mat out;
        ex.extract("981", out);

        ncnn::Mat anchors(6);
        anchors[0] = 4.f;
        anchors[1] = 5.f;
        anchors[2] = 8.f;
        anchors[3] = 10.f;
        anchors[4] = 13.f;
        anchors[5] = 16.f;

        std::vector<Object> objects8;
        generate_proposals(anchors, 8, in_pad, out, prob_threshold, objects8);

        proposals.insert(proposals.end(), objects8.begin(), objects8.end());
    }

    // stride 16
    {
        ncnn::Mat out;
        ex.extract("983", out);

        ncnn::Mat anchors(6);
        anchors[0] = 23.f;
        anchors[1] = 29.f;
        anchors[2] = 43.f;
        anchors[3] = 55.f;
        anchors[4] = 73.f;
        anchors[5] = 105.f;

        std::vector<Object> objects16;
        generate_proposals(anchors, 16, in_pad, out, prob_threshold, objects16);

        proposals.insert(proposals.end(), objects16.begin(), objects16.end());
    }

    // stride 32
    {
        ncnn::Mat out;
        ex.extract("985", out);

        ncnn::Mat anchors(6);
        anchors[0] = 146.f;
        anchors[1] = 217.f;
        anchors[2] = 231.f;
        anchors[3] = 300.f;
        anchors[4] = 335.f;
        anchors[5] = 433.f;

        std::vector<Object> objects32;
        generate_proposals(anchors, 32, in_pad, out, prob_threshold, objects32);

        proposals.insert(proposals.end(), objects32.begin(), objects32.end());
    }

    // sort all proposals by score from highest to lowest
    qsort_descent_inplace(proposals);

    // apply nms with nms_threshold
    std::vector<int> picked;
    nms_sorted_bboxes(proposals, picked, nms_threshold);

    int count = picked.size();
    //std::cout << "人脸数量：" << count << std::endl;
    objects.resize(count);

    for (int i = 0; i < count; i++)
    {
        objects[i] = proposals[picked[i]];

        // adjust offset to original unpadded
        float x0 = (objects[i].rect.x - (wpad / 2)) / scale;
        float y0 = (objects[i].rect.y - (hpad / 2)) / scale;
        float x1 = (objects[i].rect.x + objects[i].rect.width - (wpad / 2)) / scale;
        float y1 = (objects[i].rect.y + objects[i].rect.height - (hpad / 2)) / scale;

        for (int j = 0; j < objects[i].pts.size(); j++)
        {
            float ptx = (objects[i].pts[j].x - (wpad / 2)) / scale;
            float pty = (objects[i].pts[j].y - (hpad / 2)) / scale;
            objects[i].pts[j] = cv::Point2f(ptx, pty);
        }

        // clip
        x0 = (std::max)((std::min)(x0, (float)(img_w - 1)), 0.f);
        y0 = (std::max)((std::min)(y0, (float)(img_h - 1)), 0.f);
        x1 = (std::max)((std::min)(x1, (float)(img_w - 1)), 0.f);
        y1 = (std::max)((std::min)(y1, (float)(img_h - 1)), 0.f);

        objects[i].rect.x = x0;
        objects[i].rect.y = y0;
        objects[i].rect.width = x1 - x0;
        objects[i].rect.height = y1 - y0;

    }

    picked.clear();
    return 1;
}


