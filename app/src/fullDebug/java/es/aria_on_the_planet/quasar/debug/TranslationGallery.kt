package es.aria_on_the_planet.quasar.debug

import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import android.system.Os
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import es.aria_on_the_planet.quasar.data.translation.TextClassifierLanguageDetector
import es.aria_on_the_planet.quasar.data.translation.languageDetectionText
import es.aria_on_the_planet.quasar.data.translation.PlamoForm
import es.aria_on_the_planet.quasar.data.translation.PlamoStopTokens
import es.aria_on_the_planet.quasar.data.translation.PlamoTranslationRequest
import es.aria_on_the_planet.quasar.data.translation.PromptSegment
import es.aria_on_the_planet.quasar.data.translation.TranslatableHtml
import es.aria_on_the_planet.quasar.data.translation.restoreTranslatedHtml
import es.aria_on_the_planet.quasar.data.translation.toTranslatableHtml
import es.aria_on_the_planet.quasar.data.translation.plamoLanguageOf
import es.aria_on_the_planet.quasar.data.translation.plamoTranslationPrompt
import es.aria_on_the_planet.quasar.data.translation.render
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.llama.GenerationResult
import es.aria_on_the_planet.quasar.llama.LlamaModel
import es.aria_on_the_planet.quasar.llama.LlamaParams
import es.aria_on_the_planet.quasar.llama.PromptPiece
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailScaffold
import es.aria_on_the_planet.quasar.ui.component.text.EmojiText
import es.aria_on_the_planet.quasar.ui.component.text.postPlainText
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * オンデバイス翻訳 (PLaMo 3 Translate + llama.cpp) を試すシナリオ。モデルは APK に入れないので adb で置く
 * (一度このシナリオを開いて models/ をアプリに作らせてから。adb shell で作ったディレクトリの中はアプリから読めない):
 * ```
 * adb push plamo-3-translate-0.8b-Q4_0.gguf /sdcard/Android/data/es.aria_on_the_planet.quasar.debug/files/models/
 * ```
 * extra: `--es model <ファイル名>` (models/ の中)、`--ei threads N`、`--ei context_length N`、
 * `--ei post N` (原文にするポスト、0 始まり) / `--es text "原文"`、`--es mode plain` (既定は html)、`--es target ja`、
 * `--es device cpu` (CPU だけ。既定は NPU 等も使う)、`--ez autorun true` で開いたらすぐ読み込んで訳す (結果は logcat の `QuasarTranslate`。HTML なら戻した結果も)。
 */
internal object TranslationExtras {
    const val Model = "model"
    const val Threads = "threads"
    const val ContextLength = "context_length"
    const val Text = "text"
    const val Target = "target"
    const val AutoRun = "autorun"

    /** `html` (既定) / `plain`。 */
    const val Mode = "mode"

    /** 原文にするギャラリーのポスト (0 始まり)。 */
    const val Post = "post"

    /** `cpu` なら CPU だけ。既定は使えるデバイス全部 (Hexagon 入りのビルドなら NPU)。 */
    const val Device = "device"

    /**
     * llama.cpp の環境変数 (`--es native_env "GGML_HEXAGON_OPFILTER=FLASH_ATTN_EXT;GGML_HEXAGON_VERBOSE=1"`)。バックエンドは
     * プロセスで最初に読み込むときに読むので、変えるときは `am force-stop` してから開く。
     */
    const val NativeEnv = "native_env"
}

private const val DefaultModel = "plamo-3-translate-0.8b-Q4_0.gguf"
private const val LogTag = "QuasarTranslate"

internal data class TranslationGalleryState(
    val modelFile: File,
    val params: LlamaParams,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val loadMillis: Long? = null,
    val description: String? = null,
    val running: Boolean = false,
    val output: String = "",
    val result: GenerationResult? = null,
    val error: String? = null,
)

internal class TranslationGalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(
        TranslationGalleryState(
            modelFile = File(application.getExternalFilesDir(null), "models/$DefaultModel"),
            params = LlamaParams(),
        ),
    )
    val state: StateFlow<TranslationGalleryState> = _state
    private var model: LlamaModel? = null
    private var job: Job? = null

    init {
        // models/ はアプリが作る (adb shell で作ったディレクトリの中はアプリから読めない)
        _state.value.modelFile.parentFile?.mkdirs()
    }

    fun configure(extras: Bundle?) {
        if (extras == null || _state.value.loaded || _state.value.loading) return
        extras.getString(TranslationExtras.NativeEnv)?.split(';')?.filter { '=' in it }?.forEach {
            Os.setenv(it.substringBefore('='), it.substringAfter('='), true)
            Log.i(LogTag, "env $it")
        }
        val app = getApplication<Application>()
        _state.update { state ->
            state.copy(
                modelFile = extras.getString(TranslationExtras.Model)
                    ?.let { File(app.getExternalFilesDir(null), "models/$it") } ?: state.modelFile,
                params = state.params.copy(
                    threads = extras.getInt(TranslationExtras.Threads, state.params.threads),
                    contextLength = extras.getInt(TranslationExtras.ContextLength, state.params.contextLength),
                    cpuOnly = extras.getString(TranslationExtras.Device) == "cpu",
                ),
            )
        }
    }

    fun load() {
        val current = _state.value
        if (current.loading || current.loaded) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val start = SystemClock.elapsedRealtime()
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    LlamaModel.load(
                        current.modelFile.path,
                        getApplication<Application>().applicationInfo.nativeLibraryDir,
                        current.params,
                    )
                }
            }
            val elapsed = SystemClock.elapsedRealtime() - start
            val loadedModel = loaded.getOrNull()
            model = loadedModel
            _state.update {
                if (loadedModel == null) {
                    it.copy(loading = false, error = loaded.exceptionOrNull()?.toString() ?: "could not load ${current.modelFile}")
                } else {
                    it.copy(loading = false, loaded = true, loadMillis = elapsed, description = loadedModel.description)
                }
            }
            if (loadedModel != null) Log.i(LogTag, "loaded in $elapsed ms: ${loadedModel.description}")
        }
    }

    fun translate(prompt: List<PromptSegment>) {
        val model = model ?: return
        job?.cancel()
        _state.update { it.copy(running = true, output = "", result = null, error = null) }
        job = viewModelScope.launch {
            try {
                val result = model.generate(
                    prompt = prompt.map { PromptPiece(it.text, it.special) },
                    stops = PlamoStopTokens,
                    onText = { text -> _state.update { it.copy(output = it.output + text) } },
                )
                _state.update { it.copy(running = false, result = result) }
                Log.i(LogTag, "${result.stats()}\n${result.text}")
            } catch (e: IllegalStateException) {
                _state.update { it.copy(running = false, error = e.message) }
            } finally {
                _state.update { it.copy(running = false) }
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    override fun onCleared() {
        job?.cancel()
        // viewModelScope は取り消されているので、生成が止まるのを待ってから別のスコープで解放する
        model?.let { CoroutineScope(Dispatchers.IO).launch { it.close() } }
    }
}

private fun GenerationResult.stats(): String =
    "prompt $promptTokens tok / %.0f ms (%.1f tok/s), generated $generatedTokens tok / %.0f ms (%.1f tok/s), $stopReason".format(
        promptMillis, promptTokensPerSecond, generateMillis, generateTokensPerSecond,
    )

/** モデルに何を渡すか。HTML は [toTranslatableHtml] で簡略化した HTML (訳文は [restoreTranslatedHtml] で戻す)、Plain は平文。 */
private enum class InputMode { Html, Plain }

private fun Post.sourceText(mode: InputMode) = if (mode == InputMode.Html) text else postPlainText(text)

@Composable
internal fun TranslationGallery(posts: List<Post>, onBack: () -> Unit) {
    val viewModel: TranslationGalleryViewModel = viewModel()
    val extras = LocalActivity.current?.intent?.extras
    val state by viewModel.state.collectAsStateWithLifecycle()

    var mode by remember {
        mutableStateOf(if (extras?.getString(TranslationExtras.Mode) == "plain") InputMode.Plain else InputMode.Html)
    }
    var selected by remember { mutableIntStateOf(extras?.getInt(TranslationExtras.Post, 0) ?: 0) }
    val post = posts.getOrNull(selected)
    // 原文 (HTML のときは Mastodon の HTML のまま。モデルに渡す形は下の Prompt に出る)
    val source = remember { TextFieldState(extras?.getString(TranslationExtras.Text) ?: post?.sourceText(mode).orEmpty()) }
    val context = remember { TextFieldState() }
    val target = remember {
        TextFieldState(
            extras?.getString(TranslationExtras.Target)
                ?: plamoLanguageOf(Locale.getDefault().toLanguageTag())
                ?: "en",
        )
    }
    var form by remember { mutableStateOf<PlamoForm?>(null) }
    val emojis = post?.emojis.orEmpty()
    // 入力が変わるたびに組み立て直す (表示用)
    var translatable by remember { mutableStateOf<TranslatableHtml?>(null) }
    var prompt by remember { mutableStateOf<List<PromptSegment>>(emptyList()) }
    LaunchedEffect(form, mode, emojis) {
        snapshotFlow { Triple(source.text.toString(), target.text.toString(), context.text.toString()) }
            .collect { (text, lang, contextText) ->
                val html = if (mode == InputMode.Html) toTranslatableHtml(text, emojis.mapTo(HashSet()) { it.shortcode }) else null
                translatable = html
                prompt = plamoTranslationPrompt(
                    PlamoTranslationRequest(text = html?.html ?: text, targetLanguage = lang.trim(), form = form, context = contextText),
                )
            }
    }
    // 訳し終えたら HTML に戻す (戻せなければ null = 構造が崩れた)
    val restored = remember(state.result, translatable) {
        val html = translatable
        val result = state.result
        if (html != null && result != null) restoreTranslatedHtml(html, result.text) else null
    }

    // 言語の無いポスト (Misskey 等) 用の推定。端末の TextClassifier が何を返すか
    val appContext = LocalContext.current.applicationContext
    val detector = remember(appContext) { TextClassifierLanguageDetector(appContext) }
    var detected by remember { mutableStateOf("") }
    LaunchedEffect(mode, emojis) {
        snapshotFlow { source.text.toString() }.collectLatest { text ->
            val plain = if (mode == InputMode.Html) languageDetectionText(text, emojis.mapTo(HashSet()) { it.shortcode }) else text
            val classified = detector.detect(plain)
            detected = "TextClassifier $classified → ${plamoLanguageOf(classified)}"
            Log.i(LogTag, "detected language: $detected")
        }
    }

    LaunchedEffect(Unit) {
        viewModel.configure(extras)
        if (extras?.getBoolean(TranslationExtras.AutoRun) == true) viewModel.load()
    }
    LaunchedEffect(state.loaded) {
        if (state.loaded && extras?.getBoolean(TranslationExtras.AutoRun) == true) viewModel.translate(prompt)
    }
    LaunchedEffect(state.result) {
        if (state.result != null && translatable != null) Log.i(LogTag, "restored: $restored")
    }

    val listState = rememberLazyListState()
    DetailScaffold(
        onBack = onBack,
        listState = listState,
        isRefreshing = false,
        onRefresh = {},
        title = { Text("On-device translation") },
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "model") {
                Section("Model") {
                    Mono(state.modelFile.path)
                    Mono("threads ${state.params.threads}, context ${state.params.contextLength}, ${if (state.params.cpuOnly) "CPU only" else "all devices"}")
                    when {
                        state.loaded -> {
                            Mono("loaded in ${state.loadMillis} ms")
                            state.description?.let { Mono(it) }
                        }
                        !state.modelFile.exists() -> Mono(
                            "Not found. adb push <gguf> ${state.modelFile.parent}/",
                            color = MaterialTheme.colorScheme.error,
                        )
                        else -> Button(onClick = viewModel::load, enabled = !state.loading) {
                            Text(if (state.loading) "Loading…" else "Load")
                        }
                    }
                }
            }
            item(key = "input") {
                Section("Input") {
                    Mono("post language ${post?.language}, detected: $detected")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (option in InputMode.entries) {
                            FilterChip(
                                selected = mode == option,
                                onClick = {
                                    mode = option
                                    post?.let { source.setTextAndPlaceCursorAtEnd(it.sourceText(option)) }
                                },
                                label = { Text(option.name) },
                            )
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        posts.forEachIndexed { index, sample ->
                            FilterChip(
                                selected = selected == index,
                                onClick = {
                                    selected = index
                                    source.setTextAndPlaceCursorAtEnd(sample.sourceText(mode))
                                },
                                label = { Text("Post ${index + 1}") },
                            )
                        }
                    }
                    OutlinedTextField(source, Modifier.fillMaxWidth(), label = { Text("Text") }, lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 8))
                    if (mode == InputMode.Html) {
                        EmojiText(source.text.toString(), emojis, isHtml = true, postContent = true, style = MaterialTheme.typography.bodyLarge)
                    }
                    OutlinedTextField(context, Modifier.fillMaxWidth(), label = { Text("Context (optional)") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(target, Modifier.weight(1f), label = { Text("Target") }, lineLimits = TextFieldLineLimits.SingleLine)
                        for (option in listOf(null, PlamoForm.Plain, PlamoForm.Polite)) {
                            FilterChip(
                                selected = form == option,
                                onClick = { form = option },
                                label = { Text(option?.value ?: "auto") },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
            item(key = "prompt") {
                Section("Prompt (PFML3)") {
                    SelectionContainer { Mono(prompt.render()) }
                }
            }
            item(key = "output") {
                Section("Output") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.translate(prompt) }, enabled = state.loaded && !state.running) {
                            Text("Translate")
                        }
                        OutlinedButton(onClick = viewModel::stop, enabled = state.running) { Text("Stop") }
                    }
                    when {
                        // 戻した HTML を本番と同じ EmojiText で表示する
                        restored != null -> EmojiText(restored, emojis, isHtml = true, postContent = true, style = MaterialTheme.typography.bodyLarge)
                        else -> SelectionContainer { Text(state.output, style = MaterialTheme.typography.bodyLarge) }
                    }
                    if (translatable != null && state.result != null) {
                        if (restored == null) {
                            Mono("Could not restore the HTML (tags, links or emoji changed)", color = MaterialTheme.colorScheme.error)
                        }
                        SelectionContainer { Mono(state.output) }
                    }
                    state.result?.let { Mono(it.stats()) }
                    state.error?.let { Mono(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun Mono(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = color)
}
