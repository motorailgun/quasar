package es.aria_on_the_planet.quasar.data.licenses

/**
 * 依存の POM から一覧を作れない (Maven の依存でない) もの。本文は assets の `licenses/` に置く。
 *
 * @param textAssets 言語 (`ja` 等) → 本文の assets のパス。`""` は既定 (合う言語が無いとき)。
 * @param ifAssetPresent 本文が assets に無ければ一覧に出さない (ビルドの設定で入ったり入らなかったりするもの)。
 */
data class BundledComponent(
    val id: String,
    val name: String,
    val version: String?,
    val website: String?,
    val licenseName: String,
    val licenseUrl: String?,
    val textAssets: Map<String, String>,
    val ifAssetPresent: Boolean = false,
)

/** どのビルドにも入っているもの。 */
internal val CommonBundledComponents = listOf(
    BundledComponent(
        id = "material-symbols",
        name = "Material Symbols",
        version = null,
        website = "https://github.com/google/material-design-icons",
        licenseName = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        textAssets = mapOf("" to "licenses/material-symbols.txt"),
    ),
)
