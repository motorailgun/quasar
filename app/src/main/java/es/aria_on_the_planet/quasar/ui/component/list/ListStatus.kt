package es.aria_on_the_planet.quasar.ui.component.list

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.ui.feed.LoadError

/**
 * 一覧が空のときに、一覧の中 (header 等) に出す状態: 読み込み中 / 失敗 ([error]。文言は [errorFormat]、401 / 403 なら
 * 「ログインし直す」も。[LoadErrorMessage]) / 空 ([empty])。一覧の中に置くので、引っ張って更新はそのまま使える。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ListStatus(
    loading: Boolean,
    error: LoadError?,
    @StringRes errorFormat: Int,
    empty: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> LoadingIndicator()
            error != null -> LoadErrorMessage(error, errorFormat)
            else -> Text(empty, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
