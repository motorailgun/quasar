package es.aria_on_the_planet.quasar.data.licenses

import es.aria_on_the_planet.quasar.domain.model.LicenseInfo
import es.aria_on_the_planet.quasar.domain.model.LicensedComponent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * AboutLibraries のプラグインが作る `res/raw/aboutlibraries.json` (依存の POM から集めた名前・版・ライセンス) の形。
 * 使う項目だけ。ライセンスの本文は SPDX の雛形 (MIT 等の著作権者は `<year> <copyright holders>` のまま)。
 */
@Serializable
private data class LibraryDefinitionsDto(
    val libraries: List<LibraryDto> = emptyList(),
    val licenses: Map<String, LicenseDto> = emptyMap(),
)

@Serializable
private data class LibraryDto(
    val uniqueId: String,
    val artifactVersion: String? = null,
    val name: String? = null,
    val website: String? = null,
    val licenses: List<String> = emptyList(),
)

@Serializable
private data class LicenseDto(val name: String, val url: String? = null, val content: String? = null)

private val definitionsJson = Json { ignoreUnknownKeys = true }

/**
 * 依存のライブラリの一覧 (名前順)。BOM (中身の無い版の揃え) は除く。知らないライセンスの ID はその ID を名前にする。
 */
fun parseLibraryDefinitions(json: String): List<LicensedComponent> {
    val definitions = definitionsJson.decodeFromString<LibraryDefinitionsDto>(json)
    return definitions.libraries
        .filterNot { it.uniqueId.endsWith("-bom") }
        .map { library ->
            LicensedComponent(
                id = library.uniqueId,
                name = library.name?.takeIf { it.isNotBlank() } ?: library.uniqueId,
                version = library.artifactVersion,
                website = library.website,
                licenses = library.licenses.map { id ->
                    definitions.licenses[id]?.let { LicenseInfo(it.name, it.url, it.content) }
                        ?: LicenseInfo(id, url = null, text = null)
                },
            )
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, LicensedComponent::name).thenBy(LicensedComponent::id))
}
