// JARVIS V1 local speech-to-text: whisper.cpp (pinned v1.8.4, 9386f239) behind a minimal JNI surface.
// French only, greedy decoding, no network, no file other than the verified model path given by the host.
// Cancellation is cooperative through whisper's abort callback (checked between compute graphs).
#include <jni.h>
#include <atomic>
#include <mutex>
#include <string>
#include <vector>
#ifdef __ANDROID__
#include <android/log.h>
#endif
#include "whisper.h"

namespace {
struct Stt {
    whisper_context * ctx = nullptr;
    std::atomic<bool> cancel{false};
    std::mutex busy;
};
void throw_ise(JNIEnv * env, const char * message) {
    jclass c = env->FindClass("java/lang/IllegalStateException");
    if (c) env->ThrowNew(c, message);
}
bool aborted(void * user) { return static_cast<Stt *>(user)->cancel.load(); }
void quiet_log(ggml_log_level level, const char * text, void *) {
#ifdef __ANDROID__
    if (level == GGML_LOG_LEVEL_ERROR && text) __android_log_print(ANDROID_LOG_ERROR, "JARVIS-STT", "%s", text);
#else
    (void) level; (void) text;
#endif
}
}

extern "C" JNIEXPORT jlong JNICALL Java_fr_jarvis_v1_stt_WhisperNative_load(JNIEnv * env, jclass, jstring path) {
    whisper_log_set(quiet_log, nullptr);
    const char * p = env->GetStringUTFChars(path, nullptr);
    if (!p) return 0;
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    whisper_context * ctx = whisper_init_from_file_with_params(p, params);
    env->ReleaseStringUTFChars(path, p);
    if (!ctx) { throw_ise(env, "STT_MODEL_LOAD_REFUSED"); return 0; }
    auto * s = new Stt(); s->ctx = ctx;
    return reinterpret_cast<jlong>(s);
}

extern "C" JNIEXPORT jstring JNICALL Java_fr_jarvis_v1_stt_WhisperNative_transcribe(
    JNIEnv * env, jclass, jlong h, jfloatArray pcm16k, jint threads) {
    auto * s = reinterpret_cast<Stt *>(h);
    if (!s || !s->ctx) { throw_ise(env, "STT_NOT_LOADED"); return nullptr; }
    std::unique_lock<std::mutex> lock(s->busy, std::try_to_lock);
    if (!lock.owns_lock()) { throw_ise(env, "STT_BUSY"); return nullptr; }
    const jsize n = env->GetArrayLength(pcm16k);
    if (n < 1600 || n > 16000 * 30) { throw_ise(env, "STT_AUDIO_LENGTH_REFUSED"); return nullptr; } // 0.1 s .. 30 s
    std::vector<float> samples(n);
    env->GetFloatArrayRegion(pcm16k, 0, n, samples.data());
    s->cancel.store(false);
    whisper_full_params wp = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wp.n_threads = threads < 1 ? 1 : (threads > 4 ? 4 : threads);
    wp.language = "fr"; wp.detect_language = false; wp.translate = false;
    wp.no_timestamps = true; wp.single_segment = false;
    wp.print_special = false; wp.print_progress = false; wp.print_realtime = false; wp.print_timestamps = false;
    wp.suppress_blank = true; wp.suppress_nst = true;
    wp.abort_callback = aborted; wp.abort_callback_user_data = s;
    const int rc = whisper_full(s->ctx, wp, samples.data(), n);
    if (s->cancel.load()) { throw_ise(env, "STT_CANCELLED"); return nullptr; }
    if (rc != 0) { throw_ise(env, "STT_TRANSCRIPTION_FAILED"); return nullptr; }
    std::string text;
    for (int i = 0; i < whisper_full_n_segments(s->ctx); ++i) text += whisper_full_get_segment_text(s->ctx, i);
    return env->NewStringUTF(text.c_str());
}

extern "C" JNIEXPORT void JNICALL Java_fr_jarvis_v1_stt_WhisperNative_cancel(JNIEnv *, jclass, jlong h) {
    if (auto * s = reinterpret_cast<Stt *>(h)) s->cancel.store(true);
}

extern "C" JNIEXPORT void JNICALL Java_fr_jarvis_v1_stt_WhisperNative_free(JNIEnv *, jclass, jlong h) {
    auto * s = reinterpret_cast<Stt *>(h);
    if (!s) return;
    s->cancel.store(true);
    { std::lock_guard<std::mutex> wait(s->busy); whisper_free(s->ctx); s->ctx = nullptr; }
    delete s;
}
