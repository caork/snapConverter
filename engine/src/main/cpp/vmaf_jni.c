#include <jni.h>
#include <stdint.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>
#include <libvmaf/libvmaf.h>
#include <libvmaf/model.h>
#include <libvmaf/picture.h>

#define LOG_TAG "SnapVmaf"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static int copy_i420(VmafPicture *pic, const uint8_t *src, int w, int h) {
    const int y_size = w * h;
    const int uv_w = w / 2;
    const int uv_h = h / 2;
    const uint8_t *ys = src;
    const uint8_t *us = src + y_size;
    const uint8_t *vs = src + y_size + uv_w * uv_h;
    uint8_t *yd = (uint8_t *)pic->data[0];
    uint8_t *ud = (uint8_t *)pic->data[1];
    uint8_t *vd = (uint8_t *)pic->data[2];
    for (int y = 0; y < h; y++) {
        memcpy(yd + y * pic->stride[0], ys + y * w, (size_t)w);
    }
    for (int y = 0; y < uv_h; y++) {
        memcpy(ud + y * pic->stride[1], us + y * uv_w, (size_t)uv_w);
        memcpy(vd + y * pic->stride[2], vs + y * uv_w, (size_t)uv_w);
    }
    return 0;
}

JNIEXPORT jboolean JNICALL
Java_com_snapconverter_engine_quality_VmafNative_nativeAvailable(JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    return JNI_TRUE;
}

JNIEXPORT jdouble JNICALL
Java_com_snapconverter_engine_quality_VmafNative_nativeScoreI420(
        JNIEnv *env, jclass clazz, jint width, jint height,
        jobjectArray ref_frames, jobjectArray dist_frames, jstring model_version) {
    (void)clazz;
    if (width < 16 || height < 16 || (width % 2) || (height % 2)) {
        return -1.0;
    }
    const jsize n = (*env)->GetArrayLength(env, ref_frames);
    if (n <= 0 || n != (*env)->GetArrayLength(env, dist_frames)) {
        return -1.0;
    }
    const char *model_name = (*env)->GetStringUTFChars(env, model_version, NULL);
    if (!model_name) return -1.0;

    VmafConfiguration cfg;
    memset(&cfg, 0, sizeof(cfg));
    cfg.log_level = VMAF_LOG_LEVEL_NONE;
    cfg.n_threads = 2;

    VmafContext *vmaf = NULL;
    VmafModel *model = NULL;
    double score = -1.0;
    int err = vmaf_init(&vmaf, cfg);
    if (err) {
        LOGE("vmaf_init failed %d", err);
        goto done;
    }
    VmafModelConfig model_cfg = {0};
    err = vmaf_model_load(&model, &model_cfg, model_name);
    if (err) {
        LOGE("vmaf_model_load %s failed %d", model_name, err);
        goto done;
    }
    err = vmaf_use_features_from_model(vmaf, model);
    if (err) {
        LOGE("vmaf_use_features_from_model failed %d", err);
        goto done;
    }

    const int expected = width * height + 2 * (width / 2) * (height / 2);
    for (jsize i = 0; i < n; i++) {
        jbyteArray ref_ba = (jbyteArray)(*env)->GetObjectArrayElement(env, ref_frames, i);
        jbyteArray dist_ba = (jbyteArray)(*env)->GetObjectArrayElement(env, dist_frames, i);
        if (!ref_ba || !dist_ba) {
            if (ref_ba) (*env)->DeleteLocalRef(env, ref_ba);
            if (dist_ba) (*env)->DeleteLocalRef(env, dist_ba);
            goto done;
        }
        if ((*env)->GetArrayLength(env, ref_ba) < expected ||
            (*env)->GetArrayLength(env, dist_ba) < expected) {
            (*env)->DeleteLocalRef(env, ref_ba);
            (*env)->DeleteLocalRef(env, dist_ba);
            LOGE("short I420 buffer at %d", (int)i);
            goto done;
        }
        jbyte *ref_ptr = (*env)->GetByteArrayElements(env, ref_ba, NULL);
        jbyte *dist_ptr = (*env)->GetByteArrayElements(env, dist_ba, NULL);
        VmafPicture ref_pic, dist_pic;
        memset(&ref_pic, 0, sizeof(ref_pic));
        memset(&dist_pic, 0, sizeof(dist_pic));
        err = vmaf_picture_alloc(&ref_pic, VMAF_PIX_FMT_YUV420P, 8, (unsigned)width, (unsigned)height);
        int err2 = vmaf_picture_alloc(&dist_pic, VMAF_PIX_FMT_YUV420P, 8, (unsigned)width, (unsigned)height);
        if (err || err2) {
            LOGE("vmaf_picture_alloc failed");
            if (ref_ptr) (*env)->ReleaseByteArrayElements(env, ref_ba, ref_ptr, JNI_ABORT);
            if (dist_ptr) (*env)->ReleaseByteArrayElements(env, dist_ba, dist_ptr, JNI_ABORT);
            (*env)->DeleteLocalRef(env, ref_ba);
            (*env)->DeleteLocalRef(env, dist_ba);
            if (!err) vmaf_picture_unref(&ref_pic);
            if (!err2) vmaf_picture_unref(&dist_pic);
            goto done;
        }
        copy_i420(&ref_pic, (const uint8_t *)ref_ptr, width, height);
        copy_i420(&dist_pic, (const uint8_t *)dist_ptr, width, height);
        (*env)->ReleaseByteArrayElements(env, ref_ba, ref_ptr, JNI_ABORT);
        (*env)->ReleaseByteArrayElements(env, dist_ba, dist_ptr, JNI_ABORT);
        (*env)->DeleteLocalRef(env, ref_ba);
        (*env)->DeleteLocalRef(env, dist_ba);
        err = vmaf_read_pictures(vmaf, &ref_pic, &dist_pic, (unsigned)i);
        if (err) {
            LOGE("vmaf_read_pictures %d failed %d", (int)i, err);
            goto done;
        }
    }

    err = vmaf_read_pictures(vmaf, NULL, NULL, 0);
    if (err) {
        LOGE("vmaf flush failed %d", err);
        goto done;
    }
    err = vmaf_score_pooled(vmaf, model, VMAF_POOL_METHOD_MEAN, &score, 0, (unsigned)(n - 1));
    if (err) {
        LOGE("vmaf_score_pooled failed %d", err);
        score = -1.0;
    } else {
        LOGI("pooled VMAF %.3f over %d frames", score, (int)n);
    }

done:
    if (model) vmaf_model_destroy(model);
    if (vmaf) vmaf_close(vmaf);
    (*env)->ReleaseStringUTFChars(env, model_version, model_name);
    return score;
}
