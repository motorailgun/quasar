package es.aria_on_the_planet.quasar.data.translation

import android.content.Context
import android.os.SystemClock
import android.util.Log
import es.aria_on_the_planet.quasar.llama.LlamaModel
import es.aria_on_the_planet.quasar.llama.PromptPiece
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** full: llama.cpp (`:llama`) で [modelFile] を動かす。lite は src/lite の (翻訳なし)。 */
fun createTranslationEngine(context: Context, modelFile: File, scope: CoroutineScope): TranslationEngine? =
    LlamaTranslationEngine(context.applicationContext, modelFile, scope)

private const val TAG = "TranslationEngine"

/** 使われなくなってからモデルを解放するまで (読み込み直しは 1〜2 秒)。 */
private val IdleUnloadDelay = 2.minutes

private class LlamaTranslationEngine(
    private val context: Context,
    private val modelFile: File,
    private val scope: CoroutineScope,
) : TranslationEngine {
    private val mutex = Mutex()
    private var model: LlamaModel? = null
    private var unload: Job? = null

    override suspend fun generate(prompt: List<PromptSegment>, maxTokens: Int): String = mutex.withLock {
        unload?.cancel()
        try {
            // 読み込みの途中で取り消されても読んだものを失わないよう、ブロックの中で持つ (release で解放される)
            val loaded = model ?: withContext(Dispatchers.IO) {
                val start = SystemClock.elapsedRealtime()
                LlamaModel.load(modelFile.path, context.applicationInfo.nativeLibraryDir)?.also {
                    model = it
                    Log.i(TAG, "model loaded in ${SystemClock.elapsedRealtime() - start} ms")
                }
            } ?: throw IllegalStateException("could not load the translation model")
            loaded.generate(
                prompt = prompt.map { PromptPiece(it.text, it.special) },
                stops = PlamoStopTokens,
                maxTokens = maxTokens,
            ).text
        } finally {
            unload = scope.launch {
                delay(IdleUnloadDelay)
                release()
            }
        }
    }

    override suspend fun release() = mutex.withLock {
        unload?.cancel()
        model?.let {
            it.close()
            Log.i(TAG, "model released")
        }
        model = null
    }
}
