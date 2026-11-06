package es.aria_on_the_planet.quasar.domain.model

/**
 * ライセンスの一覧の 1 件 (依存のライブラリ・同梱のソフトウェア・翻訳のモデル)。
 *
 * @param id 一覧の中で一意。Maven の依存なら `group:artifact`。
 */
data class LicensedComponent(
    val id: String,
    val name: String,
    val version: String?,
    val website: String?,
    val licenses: List<LicenseInfo>,
)

/** @param text 本文。無ければ [url] を開いてもらう。 */
data class LicenseInfo(val name: String, val url: String?, val text: String?)

/**
 * 設定の「ライセンス」に出すもの全部。
 *
 * @param translationModel 翻訳のモデル (PLaMo。翻訳の入ったビルドだけ)。規約が「Built with PLaMo」の表示を求める。
 * @param bundled 依存の POM から一覧を作れないもの (アイコン・llama.cpp 等)。
 * @param libraries 依存のライブラリ (ビルド時に AboutLibraries のプラグインが POM から作る)。
 */
data class LicenseCatalog(
    val translationModel: LicensedComponent?,
    val bundled: List<LicensedComponent>,
    val libraries: List<LicensedComponent>,
) {
    fun find(id: String): LicensedComponent? =
        sequenceOf(translationModel).filterNotNull().plus(bundled).plus(libraries).firstOrNull { it.id == id }
}
