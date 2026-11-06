// LlamaModel (Kotlin) の JNI。llama.cpp の API はこのファイルの中だけで使う。
//
// 1 つのモデル = 1 つの llama_context。生成は 1 度に 1 つ (Kotlin 側の Mutex で守る)。
// プロンプトは「文字列 + 特殊トークンとして読むか」の組の並びで受け取り、ユーザーの文字列 (ポストの本文等) は
// 特殊トークンとして読まない。それでも制御トークンになったら生成しない (PLaMo 3 のトークナイザは読んでしまう)。

#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>

#include <algorithm>
#include <chrono>
#include <cstdlib>
#include <string>
#include <unordered_set>
#include <vector>

#include "llama.h"

#define TAG "QuasarLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

struct Engine {
    llama_model *model = nullptr;
    llama_context *context = nullptr;
    llama_sampler *sampler = nullptr;
    const llama_vocab *vocab = nullptr;
};

// 生成中の Kotlin のコールバック (abort の判定に使う。llama_decode を呼んだスレッドで呼ばれる)
struct CallbackRef {
    JNIEnv *env;
    jobject callback;
    jmethodID shouldStop;
};

int64_t nowMicros() {
    return std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
}

void logCallback(ggml_log_level level, const char *text, void *) {
    int priority;
    switch (level) {
        case GGML_LOG_LEVEL_ERROR: priority = ANDROID_LOG_ERROR; break;
        case GGML_LOG_LEVEL_WARN: priority = ANDROID_LOG_WARN; break;
        case GGML_LOG_LEVEL_INFO: priority = ANDROID_LOG_INFO; break;
        case GGML_LOG_LEVEL_DEBUG: return;
        default: priority = ANDROID_LOG_DEBUG; break;
    }
    __android_log_write(priority, "llama.cpp", text);
}

std::string toString(JNIEnv *env, jstring value) {
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

// Java の String を UTF-8 に (GetStringUTFChars は Modified UTF-8 で、絵文字等の 4 バイト文字が崩れるため使わない)
std::string toUtf8(JNIEnv *env, jstring value) {
    jclass stringClass = env->FindClass("java/lang/String");
    jmethodID getBytes = env->GetMethodID(stringClass, "getBytes", "(Ljava/lang/String;)[B");
    jstring charset = env->NewStringUTF("UTF-8");
    auto bytes = (jbyteArray) env->CallObjectMethod(value, getBytes, charset);
    jsize length = env->GetArrayLength(bytes);
    std::string result(length, '\0');
    env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte *>(result.data()));
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(charset);
    env->DeleteLocalRef(stringClass);
    return result;
}

std::vector<llama_token> tokenize(const llama_vocab *vocab, const std::string &text, bool special) {
    int n = -llama_tokenize(vocab, text.data(), (int32_t) text.size(), nullptr, 0, false, special);
    std::vector<llama_token> tokens(n);
    if (n > 0) {
        llama_tokenize(vocab, text.data(), (int32_t) text.size(), tokens.data(), n, false, special);
    }
    return tokens;
}

std::string tokenToPiece(const llama_vocab *vocab, llama_token token) {
    char buffer[256];
    int n = llama_token_to_piece(vocab, token, buffer, sizeof(buffer), 0, false);
    if (n >= 0) return {buffer, (size_t) n};
    std::string piece(-n, '\0');
    llama_token_to_piece(vocab, token, piece.data(), (int32_t) piece.size(), 0, false);
    return piece;
}

// 末尾の書きかけの UTF-8 (トークンの切れ目で文字の途中まで) を除いた長さ
size_t completeUtf8Length(const std::string &bytes) {
    size_t length = bytes.size();
    for (size_t back = 1; back <= std::min<size_t>(3, length); back++) {
        auto byte = (unsigned char) bytes[length - back];
        if ((byte & 0xC0) == 0x80) continue;  // 続きのバイト
        size_t needed = byte >= 0xF0 ? 4 : byte >= 0xE0 ? 3 : byte >= 0xC0 ? 2 : 1;
        return needed > back ? length - back : length;
    }
    return length;
}

bool decode(llama_context *context, std::vector<llama_token> &tokens, int start, int count, bool lastLogits) {
    llama_batch batch = llama_batch_init(count, 0, 1);
    for (int i = 0; i < count; i++) {
        batch.token[i] = tokens[start + i];
        batch.pos[i] = start + i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = lastLogits && i == count - 1;
    }
    batch.n_tokens = count;
    int result = llama_decode(context, batch);
    llama_batch_free(batch);
    if (result != 0) LOGE("llama_decode failed: %d", result);
    return result == 0;
}

/**
 * Snapdragon の NPU (cDSP) の Hexagon の版 (73 = v73 等)。FastRPC が無い・答えない端末は 0。
 * llama.cpp の Hexagon のバックエンド (htpdrv_get_arch) と同じ問い合わせを、バックエンドを読む前に自分で行う
 * (値は Hexagon SDK の remote.h: DSPRPC_GET_DSP_INFO = 2、ARCH_VER = 6、CDSP_DOMAIN_ID = 3)。
 */
int hexagonArch() {
    void *library = dlopen("libcdsprpc.so", RTLD_NOW | RTLD_LOCAL);
    if (library == nullptr) return 0;
    auto control = reinterpret_cast<int (*)(uint32_t, void *, uint32_t)>(dlsym(library, "remote_handle_control"));
    struct {
        uint32_t domain;
        uint32_t attributeId;
        uint32_t capability;
    } capability{3, 6, 0};
    int arch = 0;
    if (control != nullptr && control(2, &capability, sizeof(capability)) == 0) {
        uint32_t value = capability.capability & 0xff;
        arch = (int) ((value >> 4) * 10 + (value & 0x0f));
    }
    // 閉じない (Hexagon のバックエンドも同じものを開く)
    return arch;
}

void throwError(JNIEnv *env, const char *message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_es_aria_1on_1the_1planet_quasar_llama_LlamaModel_nativeInit(JNIEnv *env, jclass, jstring libraryDir) {
    llama_log_set(logCallback, nullptr);
    const std::string dir = toString(env, libraryDir);
    // Hexagon (NPU) のバックエンドが NPU 側で動かす libggml-htp-v*.so を、FastRPC がアプリの .so の場所から探せるように。
    // 区切りは ';'。後ろは端末の既定の場所
    const std::string dspPath = dir + ";/vendor/dsp/cdsp;/vendor/lib/rfsa/adsp;/system/lib/rfsa/adsp;/vendor/dsp";
    setenv("ADSP_LIBRARY_PATH", dspPath.c_str(), 1);
    const int arch = hexagonArch();
    LOGI("Hexagon arch: v%d", arch);
    // v73 には HMX (行列の演算器) が FP16 に対応していないもの (8s Gen 3/4・7 Gen 4・6 Gen 4 等) があり、llama.cpp は FP16 の
    // HMX を使うので訳文が壊れる (Nothing の 8s Gen 4 で確認。llama.cpp #29473 / #31645、整数の HMX で直す PR #29740 は未マージ)。
    // FP16 の HMX があるか (8 Gen 2 等) は外から分からないので、v73 は全部 HVX だけにする。明示されていればそれに従う (native_env)
    if (arch == 73) setenv("GGML_HEXAGON_NHMX", "0", 0);
    // CPU の機能ごとの版 (libggml-cpu-*.so)・Hexagon 等のバックエンドを読む
    ggml_backend_load_all_from_path(dir.c_str());
    // v73 より古い (865+ の v66 等) か分からないときは Hexagon を使わない (llama.cpp は v73 に丸めて v73 用のものを NPU に
    // 読ませようとする)。NPU と話すのはモデルを読むときなので、ここで外せば何もしない
    if (arch < 73) {
        if (ggml_backend_reg_t hexagon = ggml_backend_reg_by_name("HTP")) ggml_backend_unload(hexagon);
    }
    llama_backend_init();
}

extern "C" JNIEXPORT jlong JNICALL
Java_es_aria_1on_1the_1planet_quasar_llama_LlamaModel_nativeLoad(
        JNIEnv *env, jclass, jstring path, jint contextLength, jint threads, jboolean cpuOnly) {
    auto *engine = new Engine();
    llama_model_params modelParams = llama_model_default_params();
    // 既定 (-1) は使えるデバイス (Hexagon 等) に全層を載せる。0 なら CPU だけ
    if (cpuOnly) modelParams.n_gpu_layers = 0;
    engine->model = llama_model_load_from_file(toString(env, path).c_str(), modelParams);
    if (engine->model == nullptr) {
        delete engine;
        return 0;
    }
    engine->vocab = llama_model_get_vocab(engine->model);

    llama_context_params contextParams = llama_context_default_params();
    contextParams.n_ctx = contextLength;
    contextParams.n_batch = std::min(contextLength, 512);
    contextParams.n_ubatch = contextParams.n_batch;
    contextParams.n_threads = threads;
    contextParams.n_threads_batch = threads;
    engine->context = llama_init_from_model(engine->model, contextParams);
    if (engine->context == nullptr) {
        llama_model_free(engine->model);
        delete engine;
        return 0;
    }
    // 翻訳なので常に最も確からしいトークン (PLaMo の推奨も temperature 0)
    engine->sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(engine->sampler, llama_sampler_init_greedy());
    return reinterpret_cast<jlong>(engine);
}

extern "C" JNIEXPORT void JNICALL
Java_es_aria_1on_1the_1planet_quasar_llama_LlamaModel_nativeFree(JNIEnv *, jclass, jlong handle) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    llama_sampler_free(engine->sampler);
    llama_free(engine->context);
    llama_model_free(engine->model);
    delete engine;
}

extern "C" JNIEXPORT jstring JNICALL
Java_es_aria_1on_1the_1planet_quasar_llama_LlamaModel_nativeDescription(JNIEnv *env, jclass, jlong handle) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    char model[128];
    llama_model_desc(engine->model, model, sizeof(model));
    std::string description = std::string(model) + " | devices:";
    for (size_t i = 0; i < ggml_backend_dev_count(); i++) {
        ggml_backend_dev_t device = ggml_backend_dev_get(i);
        description += std::string(" ") + ggml_backend_dev_name(device) + " (" + ggml_backend_dev_description(device) + ")";
    }
    description += std::string(" | ") + llama_print_system_info();
    return env->NewStringUTF(description.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_es_aria_1on_1the_1planet_quasar_llama_LlamaModel_nativeCountTokens(
        JNIEnv *env, jclass, jlong handle, jobjectArray texts, jbooleanArray specials) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    jsize count = env->GetArrayLength(texts);
    jboolean *special = env->GetBooleanArrayElements(specials, nullptr);
    size_t total = llama_vocab_get_add_bos(engine->vocab) ? 1 : 0;
    for (jsize i = 0; i < count; i++) {
        auto text = (jstring) env->GetObjectArrayElement(texts, i);
        total += tokenize(engine->vocab, toUtf8(env, text), special[i]).size();
        env->DeleteLocalRef(text);
    }
    env->ReleaseBooleanArrayElements(specials, special, JNI_ABORT);
    return (jint) total;
}

/**
 * 生成する。callback は `onText(byte[]): boolean` (UTF-8 の切れ目で区切った訳文の続き。false で止める) と
 * `shouldStop(): boolean` (プロンプトの処理中の取り消し)。戻り値は [プロンプトのトークン数, 生成したトークン数,
 * プロンプトの処理の μs, 生成の μs, 止まった理由 (0 = 終わり / 停止トークン, 1 = 上限, 2 = 取り消し)]。
 */
extern "C" JNIEXPORT jlongArray JNICALL
Java_es_aria_1on_1the_1planet_quasar_llama_LlamaModel_nativeGenerate(
        JNIEnv *env, jclass, jlong handle, jobjectArray texts, jbooleanArray specials,
        jobjectArray stops, jint maxTokens, jobject callback) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onText = env->GetMethodID(callbackClass, "onText", "([B)Z");
    CallbackRef ref{env, callback, env->GetMethodID(callbackClass, "shouldStop", "()Z")};

    // プロンプト: 断片ごとに、特殊トークンとして読むかを分けて区切る
    std::vector<llama_token> tokens;
    if (llama_vocab_get_add_bos(engine->vocab)) tokens.push_back(llama_vocab_bos(engine->vocab));
    jsize pieceCount = env->GetArrayLength(texts);
    jboolean *special = env->GetBooleanArrayElements(specials, nullptr);
    bool injected = false;
    for (jsize i = 0; i < pieceCount; i++) {
        auto text = (jstring) env->GetObjectArrayElement(texts, i);
        auto pieceTokens = tokenize(engine->vocab, toUtf8(env, text), special[i]);
        // トークナイザによっては (PLaMo 3)、特殊として読まない指定でも制御トークンの文字列を制御トークンにする。
        // 呼び出し側で崩してあるはずだが、残っていたら構造を壊されうるので生成しない
        if (!special[i]) {
            for (llama_token token : pieceTokens) {
                if (llama_vocab_get_attr(engine->vocab, token) & LLAMA_TOKEN_ATTR_CONTROL) injected = true;
            }
        }
        tokens.insert(tokens.end(), pieceTokens.begin(), pieceTokens.end());
        env->DeleteLocalRef(text);
    }
    env->ReleaseBooleanArrayElements(specials, special, JNI_ABORT);
    if (injected) {
        throwError(env, "control token in non-special text");
        return nullptr;
    }

    // 停止トークン (1 トークンになる特殊トークンだけ。PLaMo は `<|plamo:tag|>` / `<|plamo:bos|>`)
    std::unordered_set<llama_token> stopTokens;
    for (jsize i = 0; i < env->GetArrayLength(stops); i++) {
        auto text = (jstring) env->GetObjectArrayElement(stops, i);
        auto stopTokenIds = tokenize(engine->vocab, toUtf8(env, text), true);
        if (stopTokenIds.size() == 1) {
            stopTokens.insert(stopTokenIds[0]);
        } else {
            LOGW("stop string is not a single token (%zu tokens), ignored", stopTokenIds.size());
        }
        env->DeleteLocalRef(text);
    }

    const int contextLength = (int) llama_n_ctx(engine->context);
    const int promptTokens = (int) tokens.size();
    if (promptTokens >= contextLength) {
        throwError(env, "prompt is longer than the context");
        return nullptr;
    }

    llama_memory_clear(llama_get_memory(engine->context), true);
    llama_sampler_reset(engine->sampler);
    llama_set_abort_callback(engine->context, [](void *data) {
        auto *ref = static_cast<CallbackRef *>(data);
        return (bool) ref->env->CallBooleanMethod(ref->callback, ref->shouldStop);
    }, &ref);

    jlong reason = 0;
    const int64_t promptStart = nowMicros();
    const int batchSize = (int) llama_n_batch(engine->context);
    for (int start = 0; start < promptTokens; start += batchSize) {
        int count = std::min(batchSize, promptTokens - start);
        if (!decode(engine->context, tokens, start, count, start + count == promptTokens)) {
            llama_set_abort_callback(engine->context, nullptr, nullptr);
            if (env->CallBooleanMethod(callback, ref.shouldStop)) {
                jlong cancelled[] = {promptTokens, 0, nowMicros() - promptStart, 0, 2};
                jlongArray result = env->NewLongArray(5);
                env->SetLongArrayRegion(result, 0, 5, cancelled);
                return result;
            }
            throwError(env, "failed to process the prompt");
            return nullptr;
        }
    }
    const int64_t generateStart = nowMicros();

    int generated = 0;
    std::string pending;
    while (true) {
        if (generated >= maxTokens || promptTokens + generated >= contextLength) {
            reason = 1;
            break;
        }
        llama_token token = llama_sampler_sample(engine->sampler, engine->context, -1);
        if (llama_vocab_is_eog(engine->vocab, token) || stopTokens.count(token) > 0) break;
        generated++;

        pending += tokenToPiece(engine->vocab, token);
        size_t complete = completeUtf8Length(pending);
        if (complete > 0) {
            jbyteArray bytes = env->NewByteArray((jsize) complete);
            env->SetByteArrayRegion(bytes, 0, (jsize) complete, reinterpret_cast<const jbyte *>(pending.data()));
            jboolean keepGoing = env->CallBooleanMethod(callback, onText, bytes);
            env->DeleteLocalRef(bytes);
            pending.erase(0, complete);
            if (!keepGoing || env->ExceptionCheck()) {
                reason = 2;
                break;
            }
        }

        tokens.push_back(token);
        if (!decode(engine->context, tokens, (int) tokens.size() - 1, 1, true)) {
            if (env->CallBooleanMethod(callback, ref.shouldStop)) {
                reason = 2;
                break;
            }
            llama_set_abort_callback(engine->context, nullptr, nullptr);
            throwError(env, "failed to decode");
            return nullptr;
        }
    }
    llama_set_abort_callback(engine->context, nullptr, nullptr);
    if (env->ExceptionCheck()) return nullptr;

    jlong stats[] = {promptTokens, generated, generateStart - promptStart, nowMicros() - generateStart, reason};
    jlongArray result = env->NewLongArray(5);
    env->SetLongArrayRegion(result, 0, 5, stats);
    return result;
}
