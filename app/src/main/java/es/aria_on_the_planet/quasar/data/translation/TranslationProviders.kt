package es.aria_on_the_planet.quasar.data.translation

import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostVisibility
import es.aria_on_the_planet.quasar.domain.model.TranslationProviderType
import es.aria_on_the_planet.quasar.domain.model.TranslationSettings
import es.aria_on_the_planet.quasar.domain.model.primaryLanguageOf

/**
 * 接続中サーバーの翻訳機能。
 *
 * @param languages 翻訳元 → 翻訳先の一覧 (`/api/v1/instance/translation_languages`)。取得できない (4.2 未満) なら `null` で、
 *   そのときは言語の組を確かめずに使う。
 */
data class ServerTranslationCapabilities(
    val enabled: Boolean,
    val languages: Map<String, List<String>>? = null,
) {
    fun supports(source: String, target: String): Boolean {
        if (!enabled) return false
        val targets = languages ?: return true
        return targets[source].orEmpty().any { primaryLanguageOf(it) == target }
    }

    companion object {
        val None = ServerTranslationCapabilities(enabled = false)
    }
}

/**
 * 翻訳に使えるものの状況。
 *
 * @param server サーバーの翻訳機能。まだ取得していなければ `null` (サーバーは使わない)。
 * @param onDeviceLanguages オンデバイス翻訳 (ML Kit) が対応する言語。Google Play 開発者サービスが無ければ空。
 */
data class TranslationEnvironment(
    val loggedIn: Boolean,
    val server: ServerTranslationCapabilities? = null,
    val onDeviceLanguages: Set<String> = emptySet(),
)

/** 翻訳するポストと言語の組。 */
data class TranslationRequest(
    val post: Post,
    val source: String,
    val target: String,
    /** 試す順のプロバイダ。空なら翻訳できない。 */
    val providers: List<TranslationProviderType>,
)

/**
 * [post] を翻訳するなら、どのプロバイダをどの順で試すかを決める。翻訳しないなら `null`。
 *
 * * 翻訳元は [Post.language] のみで決める (端末上では判定しない)。不明なら翻訳しない。
 * * 翻訳元が [TranslationSettings.sourceLanguages] に無い・翻訳先と同じ・本文も CW も空なら翻訳しない。
 * * サーバー: ログイン中・有効・言語の組に対応・公開か未収載のとき。公式 Web UI と同じく、サーバーはそれ以外を翻訳しない
 *   (フォロワー限定・DM を外部の翻訳サービスに送らないためでもある)。
 * * オンデバイス: 設定で有効 (opt-in) かつ ML Kit が両方の言語に対応しているとき。
 * * サーバー → オンデバイスの順。前が失敗したら次を試す。
 */
fun translationRequestFor(
    post: Post,
    settings: TranslationSettings,
    environment: TranslationEnvironment,
): TranslationRequest? {
    val source = primaryLanguageOf(post.language) ?: return null
    val target = primaryLanguageOf(settings.targetLanguage) ?: return null
    if (source == target || source !in settings.sourceLanguages) return null
    if (post.text.isBlank() && post.contentWarning.isNullOrBlank()) return null
    val providers = buildList {
        val server = environment.server
        if (environment.loggedIn && server != null && server.supports(source, target) &&
            (post.visibility == PostVisibility.Public || post.visibility == PostVisibility.Unlisted)
        ) {
            add(TranslationProviderType.Server)
        }
        if (settings.onDeviceEnabled && source in environment.onDeviceLanguages && target in environment.onDeviceLanguages) {
            add(TranslationProviderType.OnDevice)
        }
    }
    return if (providers.isEmpty()) null else TranslationRequest(post, source, target, providers)
}
