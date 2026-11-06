package es.aria_on_the_planet.quasar.llama

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * プロンプトの断片。[special] が true なら `<|plamo:tag|>` 等を特殊トークンとして読む (プロンプトの構造)。
 * ユーザーの文字列 (ポストの本文等) は false にする: 本文に特殊トークンの文字列が書かれていても普通の文字として読まれ、
 * プロンプトの構造を壊せない。
 */
data class PromptPiece(val text: String, val special: Boolean)

data class LlamaParams(
    /** プロンプト + 生成の合計の上限 (トークン)。KV キャッシュの大きさになる。 */
    val contextLength: Int = 4096,
    val threads: Int = defaultThreads(),
    /** CPU だけで動かす。false なら使えるデバイス (Snapdragon の NPU 等、ビルドに入っていれば) に載せる。 */
    val cpuOnly: Boolean = false,
) {
    companion object {
        /** 大きいコアの数の目安 (多すぎると小さいコアを待って遅くなる)。 */
        fun defaultThreads(): Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(1, 4)
    }
}

enum class StopReason { Finished, Length, Cancelled }

data class GenerationResult(
    val text: String,
    val promptTokens: Int,
    val generatedTokens: Int,
    val promptMillis: Double,
    val generateMillis: Double,
    val stopReason: StopReason,
) {
    val promptTokensPerSecond: Double get() = promptTokens / (promptMillis / 1000)
    val generateTokensPerSecond: Double get() = generatedTokens / (generateMillis / 1000)
}

/**
 * llama.cpp で読んだ GGUF のモデル 1 つ (+ コンテキスト 1 つ)。生成は 1 度に 1 つで、呼び出しは IO スレッドで行う。
 * 使い終わったら [close] する (重みのメモリを返す)。
 */
class LlamaModel private constructor(private var handle: Long) {
    private val mutex = Mutex()

    /** モデル・使えるデバイス・CPU の機能 (ログ・ギャラリーの表示用)。 */
    val description: String by lazy { nativeDescription(handle) }

    suspend fun countTokens(prompt: List<PromptPiece>): Int = mutex.withLock {
        withContext(Dispatchers.IO) {
            check(handle != 0L) { "closed" }
            nativeCountTokens(handle, prompt.texts(), prompt.specials())
        }
    }

    /**
     * [prompt] の続きを生成する。[stops] は止める特殊トークン (1 トークンになるもののみ)。生成した文字は文字の切れ目ごとに
     * [onText] に渡す (IO スレッドから呼ぶ)。コルーチンが取り消されたら次のトークンの前 (プロンプトの処理中なら
     * 次の計算の区切り) で止まり、CancellationException になる。
     */
    suspend fun generate(
        prompt: List<PromptPiece>,
        stops: List<String> = emptyList(),
        maxTokens: Int = 512,
        onText: (String) -> Unit = {},
    ): GenerationResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            check(handle != 0L) { "closed" }
            val context = currentCoroutineContext()
            val output = StringBuilder()
            val callback = object : GenerationCallback {
                // ネイティブ側で文字の切れ目に揃えてある
                override fun onText(bytes: ByteArray): Boolean {
                    val text = String(bytes, Charsets.UTF_8)
                    output.append(text)
                    onText(text)
                    return context.isActive
                }

                override fun shouldStop(): Boolean = !context.isActive
            }
            val stats = nativeGenerate(handle, prompt.texts(), prompt.specials(), stops.toTypedArray(), maxTokens, callback)
            context.ensureActive()
            GenerationResult(
                text = output.toString(),
                promptTokens = stats[0].toInt(),
                generatedTokens = stats[1].toInt(),
                promptMillis = stats[2] / 1000.0,
                generateMillis = stats[3] / 1000.0,
                stopReason = StopReason.entries[stats[4].toInt()],
            )
        }
    }

    /** 生成中なら終わるのを待ってから解放する (先に生成のコルーチンを取り消せば次のトークンで止まる)。 */
    suspend fun close() = mutex.withLock {
        if (handle != 0L) {
            nativeFree(handle)
            handle = 0
        }
    }

    companion object {
        @Volatile
        private var initialized = false

        /**
         * [path] の GGUF を読む (数百 MB を mmap するので IO スレッドで呼ぶ)。読めなければ null。
         * [nativeLibraryDir] は `applicationInfo.nativeLibraryDir` (CPU の版ごとのバックエンドを探す場所)。
         */
        fun load(path: String, nativeLibraryDir: String, params: LlamaParams = LlamaParams()): LlamaModel? {
            synchronized(this) {
                if (!initialized) {
                    System.loadLibrary("quasar_llama")
                    nativeInit(nativeLibraryDir)
                    initialized = true
                }
            }
            val handle = nativeLoad(path, params.contextLength, params.threads, params.cpuOnly)
            return if (handle == 0L) null else LlamaModel(handle)
        }

        @JvmStatic private external fun nativeInit(libraryDir: String)
        @JvmStatic private external fun nativeLoad(path: String, contextLength: Int, threads: Int, cpuOnly: Boolean): Long
        @JvmStatic private external fun nativeFree(handle: Long)
        @JvmStatic private external fun nativeDescription(handle: Long): String
        @JvmStatic private external fun nativeCountTokens(handle: Long, texts: Array<String>, specials: BooleanArray): Int
        @JvmStatic private external fun nativeGenerate(
            handle: Long,
            texts: Array<String>,
            specials: BooleanArray,
            stops: Array<String>,
            maxTokens: Int,
            callback: GenerationCallback,
        ): LongArray
    }
}

private fun List<PromptPiece>.texts() = map { it.text }.toTypedArray()
private fun List<PromptPiece>.specials() = map { it.special }.toBooleanArray()

/** JNI から呼ぶ (メソッド名・シグネチャは quasar_llama.cpp と揃える)。 */
private interface GenerationCallback {
    fun onText(bytes: ByteArray): Boolean
    fun shouldStop(): Boolean
}
