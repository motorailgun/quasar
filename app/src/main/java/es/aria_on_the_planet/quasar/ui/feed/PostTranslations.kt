package es.aria_on_the_planet.quasar.ui.feed

import androidx.compose.runtime.Immutable
import es.aria_on_the_planet.quasar.data.translation.TranslationEnvironment
import es.aria_on_the_planet.quasar.data.translation.TranslationRequest
import es.aria_on_the_planet.quasar.data.translation.translationRequestFor
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.TranslatedContent
import es.aria_on_the_planet.quasar.domain.model.TranslationProviderType
import es.aria_on_the_planet.quasar.domain.model.TranslationSettings
import es.aria_on_the_planet.quasar.domain.model.translationSourceHash
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** ポスト 1 件の翻訳の状態 (ViewModel 側)。 */
sealed interface PostTranslationState {
    data class Translating(val downloadingModel: Boolean = false) : PostTranslationState
    data class Translated(val content: TranslatedContent, val showing: Boolean = true) : PostTranslationState
    data object Failed : PostTranslationState
}

/** ポスト 1 件の翻訳の表示 (PostCard が受け取る形)。翻訳できないポストは `null`。 */
@Immutable
sealed interface PostTranslationUi {
    /** 未翻訳。「Translate post」。 */
    data object Available : PostTranslationUi

    /** 翻訳中。オンデバイスで言語モデルを取得中なら [downloadingModel]。 */
    data class Loading(val sourceLanguage: String, val downloadingModel: Boolean) : PostTranslationUi

    /** 訳文を表示中。「Translated from … · Show original」。 */
    data class Shown(val content: TranslatedContent) : PostTranslationUi

    /** 訳した後に原文に戻した。「Show translation」。 */
    data class Original(val content: TranslatedContent) : PostTranslationUi

    /** 失敗。「Couldn't translate · Retry」。 */
    data object Failed : PostTranslationUi
}

/** 画面に渡す翻訳の状態のまとめ。[of] でポストごとの表示を求める。 */
@Immutable
data class PostTranslations(
    val states: Map<String, PostTranslationState> = emptyMap(),
    val settings: TranslationSettings = TranslationSettings(),
    val environment: TranslationEnvironment = TranslationEnvironment(loggedIn = false),
) {
    fun of(post: Post): PostTranslationUi? {
        val request = translationRequestFor(post, settings, environment)
        return when (val state = states[post.statusId]?.takeUnless { it.isStaleFor(post) }) {
            null -> if (request != null) PostTranslationUi.Available else null
            is PostTranslationState.Translating ->
                PostTranslationUi.Loading(request?.source ?: post.language.orEmpty(), state.downloadingModel)
            is PostTranslationState.Translated ->
                if (state.showing) PostTranslationUi.Shown(state.content) else PostTranslationUi.Original(state.content)
            PostTranslationState.Failed -> if (request != null) PostTranslationUi.Failed else null
        }
    }
}

/** 訳した後にポストが編集されていれば、その訳はもう使わない。 */
private fun PostTranslationState.isStaleFor(post: Post): Boolean =
    this is PostTranslationState.Translated && content.sourceHash != post.translationSourceHash()

/**
 * ポストの翻訳の状態管理。ポスト一覧を持つ ViewModel が 1 つずつ持つ。状態は statusId ごとで、
 * 同じポストが複数のカラム・ブーストに出ていても 1 つ。
 *
 * * [toggle]: 未翻訳 → 翻訳、訳文 ↔ 原文 (訳は保持)、失敗 → 再試行。翻訳中は無視する。
 * * [onShown]: 自動翻訳 ([TranslationSettings.autoTranslate]) のとき、表示されたポストを訳し始める。
 *   読み込んだ全件ではなく表示されたものだけを訳す (サーバー側翻訳への要求を抑えるため)。
 * * プロバイダは [TranslationRequest.providers] の順に試し、失敗したら次へ (サーバー → オンデバイス)。
 * * 設定が変わったら (翻訳先など) 翻訳中のものも含めてすべて捨てる。
 *
 * メインスレッドからのみ呼ぶ。
 *
 * @param translate [provider] で訳す。オンデバイスで言語モデルの取得が要るときは先に `onDownloadingModel` を呼ぶ。
 */
class PostTranslationController(
    private val scope: CoroutineScope,
    private val settings: StateFlow<TranslationSettings>,
    private val environment: StateFlow<TranslationEnvironment>,
    private val translate: suspend (
        request: TranslationRequest,
        provider: TranslationProviderType,
        onDownloadingModel: () -> Unit,
    ) -> TranslatedContent,
    private val onError: (message: String, error: Throwable) -> Unit,
    private val maxEntries: Int = MaxEntries,
    maxConcurrent: Int = MaxConcurrent,
) {
    private val states = MutableStateFlow<Map<String, PostTranslationState>>(emptyMap())
    /** 翻訳の親。設定が変わったら丸ごとキャンセルして作り直す。 */
    private var jobs = SupervisorJob(scope.coroutineContext[Job])
    private val semaphore = Semaphore(maxConcurrent)

    val translations: StateFlow<PostTranslations> = combine(states, settings, environment, ::PostTranslations)
        .stateIn(scope, SharingStarted.Eagerly, PostTranslations(states.value, settings.value, environment.value))

    init {
        scope.launch {
            settings.drop(1).collect {
                jobs.cancel()
                jobs = SupervisorJob(scope.coroutineContext[Job])
                states.value = emptyMap()
            }
        }
    }

    fun toggle(post: Post) {
        when (val state = states.value[post.statusId]?.takeUnless { it.isStaleFor(post) }) {
            is PostTranslationState.Translating -> Unit
            is PostTranslationState.Translated -> put(post.statusId, state.copy(showing = !state.showing))
            null, PostTranslationState.Failed -> start(post)
        }
    }

    fun onShown(post: Post) {
        if (!settings.value.autoTranslate) return
        val state = states.value[post.statusId]
        if (state == null || state.isStaleFor(post)) start(post)
    }

    private fun start(post: Post) {
        val request = translationRequestFor(post, settings.value, environment.value) ?: return
        val statusId = post.statusId
        put(statusId, PostTranslationState.Translating())
        scope.launch(jobs) {
            semaphore.withPermit {
                var lastError: Throwable? = null
                for (provider in request.providers) {
                    try {
                        val content = translate(request, provider) {
                            put(statusId, PostTranslationState.Translating(downloadingModel = true))
                        }
                        put(statusId, PostTranslationState.Translated(content))
                        return@withPermit
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        lastError = e
                        put(statusId, PostTranslationState.Translating())
                    }
                }
                put(statusId, PostTranslationState.Failed)
                lastError?.let { onError("Failed to translate $statusId (${request.source} -> ${request.target})", it) }
            }
        }
    }

    /** 状態を更新する。件数が [maxEntries] を超えたら、翻訳中以外の古いものから捨てる。 */
    private fun put(statusId: String, state: PostTranslationState) {
        val next = LinkedHashMap(states.value)
        next.remove(statusId)
        next[statusId] = state
        if (next.size > maxEntries) {
            val iterator = next.entries.iterator()
            while (next.size > maxEntries && iterator.hasNext()) {
                if (iterator.next().value !is PostTranslationState.Translating) iterator.remove()
            }
        }
        states.value = next
    }

    private companion object {
        /** タイムラインの上限 (MaxTimelinePosts) と揃える。 */
        const val MaxEntries = 400

        /** 同時に訳す件数。自動翻訳でサーバーへの要求が一度に出すぎないように。 */
        const val MaxConcurrent = 2
    }
}
