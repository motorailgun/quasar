package es.aria_on_the_planet.quasar.ui.openlink

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailScaffold
import es.aria_on_the_planet.quasar.ui.navigation.Route

/**
 * 他のアプリから共有された URL を開く途中の画面。サーバで引けたら [onResolved] で行き先の画面に置き換わる。
 * 見つからない・失敗したら理由と、ブラウザで開く (URL のとき) ボタン。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OpenLinkScreen(
    viewModel: OpenLinkViewModel,
    onBack: () -> Unit,
    onResolved: (Route) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resolved by rememberUpdatedState(onResolved)
    LaunchedEffect(state) {
        (state as? OpenLinkState.Resolved)?.let { resolved(it.route) }
    }

    DetailScaffold(
        onBack = onBack,
        titleRevealed = false,
        isRefreshing = false,
        onRefresh = viewModel::resolve,
        title = { Text(stringResource(R.string.open_link_title)) },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val s = state) {
                OpenLinkState.Loading, is OpenLinkState.Resolved -> LoadingIndicator()
                OpenLinkState.NotFound -> Message(stringResource(R.string.open_link_not_found))
                is OpenLinkState.Failed -> Message(stringResource(R.string.open_link_failed, s.message))
            }
            Text(
                viewModel.query,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            val s = state
            if (s is OpenLinkState.NotFound || s is OpenLinkState.Failed) {
                Actions(
                    url = viewModel.query.takeIf { it.startsWith("http", ignoreCase = true) },
                    onRetry = viewModel::resolve.takeIf { s is OpenLinkState.Failed },
                )
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
}

@Composable
private fun Actions(url: String?, onRetry: (() -> Unit)?) {
    val uriHandler = LocalUriHandler.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onRetry != null) OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.open_link_retry)) }
        if (url != null) {
            Button(onClick = { uriHandler.openUri(url) }) { Text(stringResource(R.string.open_link_open_in_browser)) }
        }
    }
}
