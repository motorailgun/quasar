package es.aria_on_the_planet.quasar.data.translation

import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.TranslatedContent
import es.aria_on_the_planet.quasar.domain.model.TranslationProviderType
import es.aria_on_the_planet.quasar.domain.model.primaryLanguageOf
import es.aria_on_the_planet.quasar.domain.model.translationSourceHash
import kotlinx.coroutines.CancellationException

/** 翻訳。サーバー側翻訳 (Mastodon の translate API) と、オンデバイス翻訳 ([OnDeviceTranslator]) をまとめる。 */
class TranslationRepository(
    private val api: MastodonApi,
    val onDevice: OnDeviceTranslator,
) {
    /**
     * 接続中サーバーの翻訳機能。言語の組の一覧は 4.2 以降のみなので、取得できなければ組を確かめずに使う。
     * 翻訳が有効かどうか自体を取得できなければ例外を投げる (呼び出し側で「サーバー翻訳なし」とみなす)。
     */
    suspend fun serverCapabilities(session: Session): ServerTranslationCapabilities {
        if (!api.translationEnabled(session.serverUrl)) return ServerTranslationCapabilities.None
        val languages = try {
            api.translationLanguages(session.serverUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return ServerTranslationCapabilities(enabled = true, languages = languages)
    }

    /** [request] を [provider] で訳す。オンデバイスでモデルのダウンロードが要るときは先に [onDownloadingModel] を呼ぶ。 */
    suspend fun translate(
        session: Session,
        request: TranslationRequest,
        provider: TranslationProviderType,
        onDownloadingModel: () -> Unit,
    ): TranslatedContent = when (provider) {
        TranslationProviderType.Server -> translateOnServer(session, request)
        TranslationProviderType.OnDevice -> onDevice.translate(request, onDownloadingModel)
    }

    private suspend fun translateOnServer(session: Session, request: TranslationRequest): TranslatedContent {
        val token = requireNotNull(session.accessToken) { "Server translation requires login" }
        val post: Post = request.post
        val result = api.translateStatus(session.serverUrl, token, post.statusId, request.target)
        return TranslatedContent(
            html = result.content,
            contentWarning = result.spoilerText.takeIf { it.isNotBlank() && post.contentWarning != null },
            sourceLanguage = primaryLanguageOf(result.detectedSourceLanguage) ?: request.source,
            provider = TranslationProviderType.Server,
            providerName = result.provider?.takeIf { it.isNotBlank() },
            sourceHash = post.translationSourceHash(),
        )
    }
}
