package es.aria_on_the_planet.quasar.data.translation

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import es.aria_on_the_planet.quasar.domain.model.TranslatedContent
import es.aria_on_the_planet.quasar.domain.model.TranslationProviderType
import es.aria_on_the_planet.quasar.domain.model.translationSourceHash
import kotlinx.coroutines.tasks.await

/**
 * ML Kit によるオンデバイス翻訳。言語モデル (1 言語あたり約 30MB) は初回に Google のサーバーから取得する。
 * Google Play 開発者サービスが無い端末では使わない ([available])。
 */
class OnDeviceTranslator(private val context: Context) {
    /** Google Play 開発者サービスがあるか。無ければ設定で有効にしても使わない (設定画面ではグレーアウトする)。 */
    val available: Boolean by lazy {
        GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }

    /** 対応する言語 (ISO 639-1)。使えなければ空。 */
    val languages: Set<String> by lazy {
        if (available) TranslateLanguage.getAllLanguages().toSet() else emptySet()
    }

    /** 言語の組ごとに 1 つ作ってアプリ全体で使い回す (閉じない)。 */
    private val translators = HashMap<Pair<String, String>, Translator>()

    private fun translatorFor(source: String, target: String): Translator = synchronized(translators) {
        translators.getOrPut(source to target) {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(requireNotNull(TranslateLanguage.fromLanguageTag(source)) { "Unsupported: $source" })
                    .setTargetLanguage(requireNotNull(TranslateLanguage.fromLanguageTag(target)) { "Unsupported: $target" })
                    .build(),
            )
        }
    }

    suspend fun translate(request: TranslationRequest, onDownloadingModel: () -> Unit): TranslatedContent {
        check(available) { "Google Play services are not available" }
        val translator = translatorFor(request.source, request.target)
        val models = RemoteModelManager.getInstance()
        val needsDownload = listOf(request.source, request.target).any { language ->
            !models.isModelDownloaded(TranslateRemoteModel.Builder(language).build()).await()
        }
        if (needsDownload) onDownloadingModel()
        // opt-in した上で翻訳を押しているので、回線の条件は付けない
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()

        val translate: suspend (String) -> String = { translator.translate(it).await() }
        val post = request.post
        return TranslatedContent(
            html = translateHtml(post.text, translate),
            contentWarning = post.contentWarning?.let { translatePlainText(it, translate) },
            sourceLanguage = request.source,
            provider = TranslationProviderType.OnDevice,
            sourceHash = post.translationSourceHash(),
        )
    }
}
