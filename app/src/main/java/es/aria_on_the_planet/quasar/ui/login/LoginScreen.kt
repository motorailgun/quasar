package es.aria_on_the_planet.quasar.ui.login

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.data.auth.LoginError
import es.aria_on_the_planet.quasar.data.auth.LoginStatus
import es.aria_on_the_planet.quasar.data.auth.OAuthLogin
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * ログイン画面。サーバを入力すると、ブラウザ (Custom Tabs) でサーバの認可画面を開く。認可後の戻り
 * (`OAuthConfig.RedirectUri`) は MainActivity が受けて [OAuthLogin.handleRedirect] に渡す。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginScreen(login: OAuthLogin) {
    val status by login.status.collectAsStateWithLifecycle()
    val pendingServer = (status as? LoginStatus.InBrowser)?.serverUrl
    var server by rememberSaveable { mutableStateOf(pendingServer?.removePrefix("https://").orEmpty()) }
    val serverUrl = normalizeServerUrl(server)
    val busy = status == LoginStatus.Registering || status == LoginStatus.Exchanging
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun start() {
        val url = serverUrl ?: return
        if (busy) return
        scope.launch {
            val authorizeUrl = login.begin(url) ?: return@launch
            if (!openInBrowser(context, authorizeUrl)) login.fail(LoginError.NoBrowser)
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayMedium)
            Text(
                stringResource(R.string.login_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                label = { Text(stringResource(R.string.login_server)) },
                placeholder = { Text("mastodon.social") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { start() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = ::start,
                enabled = serverUrl != null && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) {
                    LoadingIndicator(Modifier.size(24.dp), color = LocalContentColor.current)
                } else {
                    Text(stringResource(R.string.login_submit))
                }
            }
            LoginStatusMessage(status, onCancel = login::cancel)
        }
    }
}

@Composable
private fun LoginStatusMessage(status: LoginStatus, onCancel: () -> Unit) {
    when (status) {
        is LoginStatus.InBrowser -> Column {
            Text(
                stringResource(R.string.login_in_browser),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
        is LoginStatus.Failed -> Text(
            stringResource(status.error.messageRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        LoginStatus.Idle, LoginStatus.Registering, LoginStatus.Exchanging -> Unit
    }
}

private val LoginError.messageRes: Int
    get() = when (this) {
        LoginError.Network -> R.string.login_error_network
        LoginError.Rejected -> R.string.login_error_rejected
        LoginError.Denied -> R.string.login_error_denied
        LoginError.InvalidResponse -> R.string.login_error_invalid_response
        LoginError.NoBrowser -> R.string.login_error_no_browser
    }

/** 認可画面をブラウザで開く (Custom Tabs。対応ブラウザが無くても普通のブラウザで開く)。開けなければ `false`。 */
private fun openInBrowser(context: Context, url: String): Boolean = try {
    CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
    true
} catch (_: ActivityNotFoundException) {
    false
}

internal fun normalizeServerUrl(input: String): String? {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
    return withScheme.takeIf { it.toHttpUrlOrNull() != null }
}
