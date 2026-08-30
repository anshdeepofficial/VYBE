#include <jni.h>
#include <chromaprint.h>

extern "C" JNIEXPORT jstring JNICALL
Java_com_theveloper_pixelplay_data_recognition_ChromaprintBridge_fingerprint(
        JNIEnv *env, jobject, jshortArray samples, jint sampleRate) {
    if (!samples) return nullptr;
    const jsize size = env->GetArrayLength(samples);
    jshort *pcm = env->GetShortArrayElements(samples, nullptr);
    ChromaprintContext *ctx = chromaprint_new(CHROMAPRINT_ALGORITHM_DEFAULT);
    char *encoded = nullptr;
    bool ok = ctx && chromaprint_start(ctx, sampleRate, 1) &&
              chromaprint_feed(ctx, reinterpret_cast<int16_t *>(pcm), size) &&
              chromaprint_finish(ctx) && chromaprint_get_fingerprint(ctx, &encoded);
    env->ReleaseShortArrayElements(samples, pcm, JNI_ABORT);
    jstring result = ok && encoded ? env->NewStringUTF(encoded) : nullptr;
    if (encoded) chromaprint_dealloc(encoded);
    if (ctx) chromaprint_free(ctx);
    return result;
}
