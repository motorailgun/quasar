package es.aria_on_the_planet.quasar.data.licenses

import android.content.Context
import android.util.Log
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.LicenseCatalog
import es.aria_on_the_planet.quasar.domain.model.LicenseInfo
import es.aria_on_the_planet.quasar.domain.model.LicensedComponent
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 設定の「ライセンス」の中身。依存のライブラリの一覧は 1 回だけ読む (約 100KB の JSON)。 */
class LicenseRepository(context: Context) {
    private val context = context.applicationContext
    private val mutex = Mutex()
    private var libraries: List<LicensedComponent>? = null

    /** @param language 本文の言語 (`ja` 等)。訳のある規約 (PLaMo) だけが変わる。 */
    suspend fun catalog(language: String): LicenseCatalog = withContext(Dispatchers.IO) {
        LicenseCatalog(
            translationModel = TranslationModelComponent?.load(language),
            bundled = (CommonBundledComponents + FlavorBundledComponents).mapNotNull { it.load(language) },
            libraries = libraries(),
        )
    }

    private suspend fun libraries(): List<LicensedComponent> = mutex.withLock {
        libraries ?: run {
            val json = context.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
            parseLibraryDefinitions(json).also { libraries = it }
        }
    }

    private fun BundledComponent.load(language: String): LicensedComponent? {
        val path = textAssets[language] ?: textAssets[""]
        val text = path?.let { if (ifAssetPresent && !assetExists(it)) return null else readAsset(it) }
        return LicensedComponent(id, name, version, website, listOf(LicenseInfo(licenseName, licenseUrl, text)))
    }

    private fun assetExists(path: String): Boolean {
        val slash = path.lastIndexOf('/')
        return context.assets.list(path.substring(0, slash.coerceAtLeast(0)))?.contains(path.substring(slash + 1)) == true
    }

    private fun readAsset(path: String): String? = try {
        context.assets.open(path).bufferedReader().use { it.readText() }
    } catch (e: IOException) {
        Log.w(Tag, "license text not found: $path", e)
        null
    }

    private companion object {
        const val Tag = "LicenseRepository"
    }
}
