package es.aria_on_the_planet.quasar.debug

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.domain.model.Post

/** lite: 翻訳 (`:llama`) が入っていない。full の src/fullDebug の画面の代わりに、無いことだけを出す。 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun TranslationGallery(posts: List<Post>, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text("On-device translation is not in this build (lite flavor). Use a full build.")
    }
}
