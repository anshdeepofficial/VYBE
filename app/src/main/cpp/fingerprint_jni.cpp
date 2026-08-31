#include <jni.h>
#include <chromaprint.h>
#include <cstdint>

extern "C" JNIEXPORT jstring JNICALL
Java_com_theveloper_pixelplay_data_recognition_NativeFingerprintEngine_encode(
        JNIEnv *env, jobject, jshortArray samples, jint sampleRate) {
    if (samples == nullptr || sampleRate != 16000) return nullptr;
    const jsize count = env->GetArrayLength(samples);
    if (count < sampleRate * 5 || count > sampleRate * 15) return nullptr;

    jshort *pcm = env->GetShortArrayElements(samples, nullptr);
    if (pcm == nullptr) return nullptr;
    ChromaprintContext *context = nullptr;
    char *encoded = nullptr;
    bool success = false;
    try {
        context = chromaprint_new(CHROMAPRINT_ALGORITHM_DEFAULT);
        success = context != nullptr &&
                  chromaprint_start(context, sampleRate, 1) == 1 &&
                  chromaprint_feed(context, reinterpret_cast<int16_t *>(pcm), count) == 1 &&
                  chromaprint_finish(context) == 1 &&
                  chromaprint_get_fingerprint(context, &encoded) == 1 &&
                  encoded != nullptr;
    } catch (...) {
        success = false;
    }
    env->ReleaseShortArrayElements(samples, pcm, JNI_ABORT);
    jstring result = success ? env->NewStringUTF(encoded) : nullptr;
    if (encoded != nullptr) chromaprint_dealloc(encoded);
    if (context != nullptr) chromaprint_free(context);
    return result;
}
