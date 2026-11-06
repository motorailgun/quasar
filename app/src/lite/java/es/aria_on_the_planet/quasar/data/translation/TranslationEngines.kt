package es.aria_on_the_planet.quasar.data.translation

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope

/** lite: 端末内の翻訳を入れない (`:llama` は full だけ)。翻訳の UI・設定は出ない。 */
@Suppress("UNUSED_PARAMETER")
fun createTranslationEngine(context: Context, modelFile: File, scope: CoroutineScope): TranslationEngine? = null
