package es.aria_on_the_planet.quasar.data.licenses

import es.aria_on_the_planet.quasar.domain.model.LicenseInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryDefinitionsTest {
    private val json = """
        {
          "libraries": [
            {"uniqueId": "com.squareup.okhttp3:okhttp", "artifactVersion": "5.5.0", "name": "okhttp",
             "website": "https://square.github.io/okhttp/", "licenses": ["Apache-2.0"], "developers": []},
            {"uniqueId": "androidx.compose:compose-bom", "artifactVersion": "2026.09.00", "name": "Jetpack Compose Libraries BOM",
             "licenses": ["Apache-2.0"]},
            {"uniqueId": "androidx.activity:activity", "artifactVersion": "1.13.0", "name": "Activity", "licenses": ["Apache-2.0"]},
            {"uniqueId": "org.example:nameless", "name": "", "licenses": ["Unknown-1.0"]}
          ],
          "licenses": {
            "Apache-2.0": {"name": "Apache License 2.0", "url": "https://spdx.org/licenses/Apache-2.0.html",
                           "content": "Apache License", "spdxId": "Apache-2.0"}
          }
        }
    """.trimIndent()

    @Test
    fun parsesSortedAndDropsBoms() {
        val libraries = parseLibraryDefinitions(json)
        assertEquals(listOf("Activity", "okhttp", "org.example:nameless"), libraries.map { it.name })
        val okhttp = libraries[1]
        assertEquals("com.squareup.okhttp3:okhttp", okhttp.id)
        assertEquals("5.5.0", okhttp.version)
        assertEquals("https://square.github.io/okhttp/", okhttp.website)
        assertEquals(
            listOf(LicenseInfo("Apache License 2.0", "https://spdx.org/licenses/Apache-2.0.html", "Apache License")),
            okhttp.licenses,
        )
    }

    @Test
    fun unknownLicenseKeepsId() {
        val nameless = parseLibraryDefinitions(json).last()
        assertEquals(listOf(LicenseInfo("Unknown-1.0", url = null, text = null)), nameless.licenses)
    }
}
