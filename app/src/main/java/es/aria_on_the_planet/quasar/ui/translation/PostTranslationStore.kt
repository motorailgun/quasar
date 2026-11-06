package es.aria_on_the_planet.quasar.ui.translation

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import es.aria_on_the_planet.quasar.data.translation.LanguageDetector
import es.aria_on_the_planet.quasar.data.translation.PostTranslator
import es.aria_on_the_planet.quasar.data.translation.TranslationModelFiles
import es.aria_on_the_planet.quasar.data.translation.TranslationModelState
import es.aria_on_the_planet.quasar.data.translation.TranslationOffer
import es.aria_on_the_planet.quasar.data.translation.detectPostLanguage
import es.aria_on_the_planet.quasar.data.translation.translationOfferFor
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostTranslation
import es.aria_on_the_planet.quasar.domain.model.settings.AppSettings
import es.aria_on_the_planet.quasar.domain.model.settings.TranslationSettings
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 1 件のポストの訳の状態。 */
sealed interface PostTranslationState {
    /** まだ訳していない (自動で訳さない言語、または訳す前に画面から外れた)。 */
    data object Idle : PostTranslationState

    /** 訳す順番を待っている。 */
    data object Queued : PostTranslationState
    data object Translating : PostTranslationState
    data class Translated(val translation: PostTranslation) : PostTranslationState
    data object Failed : PostTranslationState
}

/**
 * 1 件のポストの訳 ([PostTranslationStore.entryFor])。[state] と [showingOriginal] は snapshot の状態なので、読んだポストだけが
 * 描き直される (一覧の全部ではなく)。
 */
class PostTranslationEntry internal constructor(
    val statusId: String,
    offer: TranslationOffer,
    /** 訳したときの原文 (編集されたら別の entry になる)。 */
    internal val post: Post,
) {
    /** 何語から何語へか。自動で訳すか ([TranslationOffer.automatic]) だけは設定で変わる (訳は残す)。 */
    var offer: TranslationOffer by mutableStateOf(offer)
        internal set

    var state: PostTranslationState by mutableStateOf(PostTranslationState.Idle)
        internal set

    /** 訳した後に「原文を表示」にしている。 */
    var showingOriginal: Boolean by mutableStateOf(false)
        internal set

    /** 今表示する訳 (訳していない・原文を表示中なら null)。 */
    val shownTranslation: PostTranslation?
        get() = (state as? PostTranslationState.Translated)?.translation?.takeUnless { showingOriginal }
}

/**
 * アプリ全体のポストの訳 (`AppContainer.postTranslations`、`LocalPostTranslations` で配る)。どの画面のどの一覧でも同じポストは
 * 同じ訳を使う。
 *
 * * 一覧の項目が組み立てられたとき ([onShown]) に、自動で訳す言語なら順番待ちに入れる。訳し始める前に外れたら ([onHidden])
 *   順番待ちから外す。訳すのは 1 度に 1 件で、最後に表示されたものから (スクロールして通り過ぎたものより今見ているもの)。
 * * 「翻訳を表示」([toggle]) で押したものは先頭に入れる。
 * * 言語の付いていないポスト (Misskey 等から届いたもの) は、本文から言語を推定してから出す ([languageDetector]。裏で推定し、
 *   終わったら読んだ項目が描き直される)。推定できなければ出さない。
 * * 訳す先が変わったら作り直す ([version] が増える)。言語ごとの扱いだけが変わったときは訳を残し、表示中のもので新しく自動に
 *   なったものは訳し始める。
 * * モデル (数百 MB) はアプリが裏に回ったら ([onBackground]) すぐ解放し、前面に戻るまで訳さない (裏で大きなメモリを持って
 *   いるとアプリごと終わらされやすい)。前面でもメモリが足りないと言われたら ([onMemoryPressure]) 解放し、しばらく訳さない。
 *   訳している途中で止めたものは順番待ちに戻す。
 * * メインスレッドだけで触る ([scope] は Main)。
 */
class PostTranslationStore(
    private val translator: PostTranslator,
    private val model: TranslationModelFiles,
    settings: StateFlow<AppSettings?>,
    private val scope: CoroutineScope,
    private val languageDetector: LanguageDetector?,
) {
    /** 翻訳の設定。snapshot の状態なので、読んだ画面は設定が変わると描き直される。 */
    var settings: TranslationSettings by mutableStateOf(TranslationSettings())
        private set

    /** 設定が変わって entry を作り直すたびに増える (覚えている entry を取り直す鍵)。 */
    var version: Int by mutableIntStateOf(0)
        private set

    /** モデルのファイル・ダウンロードの状態。 */
    var modelState: TranslationModelState by mutableStateOf(TranslationModelState.Missing)
        private set

    /** 翻訳がオンで、モデルのファイルがある。でなければ翻訳の UI を出さない。 */
    val modelAvailable: Boolean get() = settings.enabled && modelState == TranslationModelState.Ready

    private val entries = object : LinkedHashMap<String, PostTranslationEntry>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PostTranslationEntry>?) = size > MaxEntries
    }
    /** 言語の無いポストの推定した言語 (statusId ごと)。snapshot の状態で、推定が終わると読んだ項目が描き直される。 */
    private val detectedLanguages = mutableStateMapOf<String, DetectedLanguage>()

    /** [detectedLanguages] に入れた順 (古いものから捨てる)。 */
    private val detectedOrder = ArrayDeque<String>()

    /** 推定している statusId。 */
    private val detecting = HashSet<String>()

    /**
     * 端末の言語 ([deviceLanguageTags])。ポストを組み立てるたびに読み直さないよう覚えておき、構成が変わったら
     * ([onConfigurationChanged]) 読み直す。
     */
    private var deviceLanguages = deviceLanguageTags()

    private val pending = ArrayDeque<PostTranslationEntry>()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** 訳している 1 件 (止めるとき用)。 */
    private var running: Job? = null

    /** 前面にいる (裏では訳さない)。 */
    private var foreground = true

    /** メモリが足りないと言われてから、まだ訳さない間。 */
    private var cooldown: Job? = null

    /** モデルのファイルを消している ([deleteModel])。 */
    private var deleting = false

    init {
        scope.launch {
            settings.filterNotNull().map { it.translation }.collect {
                val previous = this@PostTranslationStore.settings
                this@PostTranslationStore.settings = it
                if (it.targetLanguage != previous.targetLanguage) reset() else if (it != previous) updateOffers()
                if (!it.enabled && previous.enabled) releaseModel()
                wake.trySend(Unit)
            }
        }
        scope.launch {
            model.state.collect {
                val wasReady = modelState == TranslationModelState.Ready
                modelState = it
                if (wasReady && it != TranslationModelState.Ready) releaseModel()
                wake.trySend(Unit)
            }
        }
        model.refresh()
        scope.launch { for (signal in wake) drain() }
    }

    /** モデルのファイル・ダウンロードを見直す (翻訳の設定を開いたとき)。 */
    fun refreshModel() = model.refresh()

    /** モデルをダウンロードする。 */
    fun downloadModel() = model.download()

    /** 読み込んだモデルを解放してから、モデルのファイル (ダウンロード中なら止めて) を消す。 */
    fun deleteModel() {
        // 消し終わるまで (モデルの状態が Missing になるまで) は次を訳さない。訳し始めると消すファイルを読み込み直す
        deleting = true
        stopRunning()
        scope.launch {
            try {
                translator.release()
                model.delete()
            } finally {
                deleting = false
                wake.trySend(Unit)
            }
        }
    }

    /** [post] の訳 (訳せないポスト・翻訳がオフ・モデルが無いなら null)。原文が変わっていれば (編集) 作り直す。 */
    fun entryFor(post: Post): PostTranslationEntry? {
        if (!modelAvailable) return null
        val source = post.language ?: detectedLanguageOf(post) ?: return null
        val offer = translationOfferFor(post, settings, deviceLanguages, source) ?: return null
        val existing = entries[post.statusId]
        // 自動で訳すかは設定が変わったときに updateOffers が合わせる (組み立ての中では状態を書かない)
        if (existing != null && existing.offer.copy(automatic = offer.automatic) == offer &&
            existing.post.text == post.text && existing.post.contentWarning == post.contentWarning
        ) {
            return existing
        }
        existing?.let(pending::remove)
        return PostTranslationEntry(post.statusId, offer, post).also { entries[post.statusId] = it }
    }

    /** 言語の無い [post] の推定した言語。まだなら推定を始めて null (終わったら [detectedLanguages] が変わる)。 */
    private fun detectedLanguageOf(post: Post): String? {
        val detected = detectedLanguages[post.statusId]
        if (detected != null && detected.text == post.text && detected.contentWarning == post.contentWarning) return detected.language
        if (detecting.add(post.statusId)) {
            // 組み立て (composition) の中から呼ばれるので、結果は後で Main で書く
            scope.launch(Dispatchers.Default) {
                val language = detectPostLanguage(post, languageDetector)
                withContext(Dispatchers.Main) {
                    detecting.remove(post.statusId)
                    if (post.statusId !in detectedLanguages) {
                        detectedOrder.addLast(post.statusId)
                        // 丸ごと消すと表示中のものも一斉に推定し直しになるので、古いものから 1 つずつ捨てる
                        while (detectedOrder.size > MaxEntries) detectedLanguages.remove(detectedOrder.removeFirst())
                    }
                    detectedLanguages[post.statusId] = DetectedLanguage(post.text, post.contentWarning, language)
                }
            }
        }
        return null
    }

    /** 一覧に出た。自動で訳す言語なら順番待ちに入れる。 */
    fun onShown(entry: PostTranslationEntry) {
        if (entry.offer.automatic && entry.state == PostTranslationState.Idle) enqueue(entry)
    }

    /** 一覧から外れた。まだ訳し始めていなければ順番待ちから外す (また出たら入れ直す)。 */
    fun onHidden(entry: PostTranslationEntry) {
        if (entry.state == PostTranslationState.Queued && pending.remove(entry)) entry.state = PostTranslationState.Idle
    }

    /** 「翻訳を表示」/「原文を表示」/「再試行」。 */
    fun toggle(entry: PostTranslationEntry) {
        when (entry.state) {
            is PostTranslationState.Translated -> entry.showingOriginal = !entry.showingOriginal
            PostTranslationState.Idle, PostTranslationState.Failed -> {
                entry.showingOriginal = false
                enqueue(entry)
            }
            PostTranslationState.Queued -> {
                // 先頭に回す
                pending.remove(entry)
                pending.addLast(entry)
            }
            PostTranslationState.Translating -> Unit
        }
        // 押したものはメモリが足りないと言われた後の待ちを切り上げて訳す
        cooldown?.cancel()
        wake.trySend(Unit)
    }

    /** 構成が変わった (`onConfigurationChanged`)。端末・アプリの言語が変わったら、訳す先が変わりうるので作り直す。 */
    fun onConfigurationChanged() {
        val languages = deviceLanguageTags()
        if (languages == deviceLanguages) return
        deviceLanguages = languages
        reset()
        wake.trySend(Unit)
    }

    /** アプリが前面に来た。止めていた順番待ちを訳す。 */
    fun onForeground() {
        foreground = true
        wake.trySend(Unit)
    }

    /** アプリが裏に回った。モデルを解放し、前面に戻るまで訳さない。 */
    fun onBackground() {
        foreground = false
        releaseModel()
    }

    /** メモリが足りない (`onTrimMemory` / `onLowMemory`)。モデルを解放し、前面ならしばらく自動では訳さない。 */
    fun onMemoryPressure() {
        releaseModel()
        if (foreground) {
            cooldown?.cancel()
            cooldown = scope.launch {
                delay(MemoryPressureCooldown)
                cooldown = null
                wake.trySend(Unit)
            }
        }
    }

    private fun releaseModel() {
        stopRunning()
        scope.launch { translator.release() }
    }

    /** 訳している途中のものを止める (順番待ちに戻る)。 */
    private fun stopRunning() {
        running?.cancel()
    }

    private val canTranslate: Boolean get() = modelAvailable && foreground && !deleting && cooldown?.isActive != true

    private fun enqueue(entry: PostTranslationEntry) {
        entry.state = PostTranslationState.Queued
        pending.remove(entry)
        pending.addLast(entry)
        wake.trySend(Unit)
    }

    private suspend fun drain() {
        while (canTranslate) {
            val entry = pending.removeLastOrNull() ?: return
            entry.state = PostTranslationState.Translating
            // 子のコルーチンで訳す: 止めても (running.cancel) この順番待ちの処理は続く
            val job = scope.async { translator.translate(entry.post, entry.offer) }
            running = job
            entry.state = try {
                PostTranslationState.Translated(job.await())
            } catch (e: CancellationException) {
                // 自分が取り消されたのでなければ、止められた (メモリ・裏に回った) ので順番待ちに戻す。
                // 訳す先が変わって作り直した ([reset]) ・編集で別の entry になったものは戻さない (もう表示されない)
                currentCoroutineContext().ensureActive()
                if (entries[entry.statusId] === entry) {
                    pending.addLast(entry)
                    PostTranslationState.Queued
                } else {
                    PostTranslationState.Idle
                }
            } catch (e: Exception) {
                Log.w(TAG, "translation failed: ${e.javaClass.simpleName}")
                PostTranslationState.Failed
            } finally {
                running = null
            }
        }
    }

    /** 言語ごとの扱いが変わった。訳は残し、自動で訳すかだけを合わせる (表示中で新しく自動になったものは訳し始める)。 */
    private fun updateOffers() {
        for (entry in entries.values) {
            val automatic = translationOfferFor(entry.post, settings, deviceLanguages, entry.offer.source)?.automatic ?: continue
            if (entry.offer.automatic != automatic) entry.offer = entry.offer.copy(automatic = automatic)
        }
    }

    private fun reset() {
        stopRunning()
        pending.clear()
        entries.clear()
        version++
    }

    /** 推定した言語 (推定できなければ null)。本文が変わったら (編集) 推定し直す。 */
    private class DetectedLanguage(val text: String, val contentWarning: String?, val language: String?)

    private companion object {
        const val TAG = "PostTranslations"

        /** 覚えておく訳の数 (タイムラインの上限 400 件 × カラム数程度)。 */
        const val MaxEntries = 1000

        /** 前面でメモリが足りないと言われてから、自動で訳すのを再開するまで。 */
        val MemoryPressureCooldown = 30.seconds
    }
}
