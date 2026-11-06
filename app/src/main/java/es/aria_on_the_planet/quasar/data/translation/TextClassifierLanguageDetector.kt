package es.aria_on_the_planet.quasar.data.translation

import android.content.Context
import android.util.Log
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 端末の TextClassifier (Android 10+ の `detectLanguage`) で言語を推定する。端末によってはモデルが無く、何も返さない
 * (そのときは null)。確信度が [MinConfidence] 未満も null。
 */
class TextClassifierLanguageDetector(private val context: Context) : LanguageDetector {
    override suspend fun detect(text: String): String? = withContext(Dispatchers.Default) {
        try {
            val classifier = context.getSystemService(TextClassificationManager::class.java)?.textClassifier
                ?: return@withContext null
            val result = classifier.detectLanguage(TextLanguage.Request.Builder(text).build())
            if (result.localeHypothesisCount == 0) return@withContext null
            val locale = result.getLocale(0)
            locale.toLanguageTag().takeIf { result.getConfidenceScore(locale) >= MinConfidence }
        } catch (e: Exception) {
            Log.w(TAG, "detectLanguage failed: ${e.javaClass.simpleName}")
            null
        }
    }

    private companion object {
        const val TAG = "LanguageDetector"
        const val MinConfidence = 0.5f
    }
}
