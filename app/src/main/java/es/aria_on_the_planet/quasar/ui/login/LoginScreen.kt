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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
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
import es.aria_on_the_planet.quasar.data.account.AccountKey
import es.aria_on_the_planet.quasar.data.auth.LoginError
import es.aria_on_the_planet.quasar.data.auth.LoginStatus
import es.aria_on_the_planet.quasar.data.auth.OAuthLogin
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * ログイン画面。サーバを入力すると、ブラウザ (Custom Tabs) でサーバの認可画面を開く。認可後の戻り
 * (`OAuthConfig.RedirectUri`) は MainActivity が受けて [OAuthLogin.handleRedirect] に渡す。
 *
 * @param onBack ログイン中のアカウントの追加・ログインし直しなら、戻るボタンで閉じる (`null` ならボタンを出さない = ログアウト中)。
 *   閉じたら進行中のログイン (ブラウザでの認可待ち) もやめる。
 * @param relogin 保存しているアカウントにログインし直すなら、その対象 (サーバは変えられない)。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(login: OAuthLogin, onBack: (() -> Unit)? = null, relogin: ReloginTarget? = null) {
    val status by login.status.collectAsStateWithLifecycle()
    val pendingServer = (status as? LoginStatus.InBrowser)?.serverUrl
    var server by rememberSaveable {
        mutableStateOf((relogin?.serverUrl ?: pendingServer)?.removePrefix("https://").orEmpty())
    }
    val serverUrl = normalizeServerUrl(server)
    val busy = status == LoginStatus.Registering || status == LoginStatus.Exchanging
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun start() {
        val url = serverUrl ?: return
        if (busy) return
        scope.launch {
            val authorizeUrl = login.begin(url, reloginKey = relogin?.key) ?: return@launch
            if (!openInBrowser(context, authorizeUrl)) login.fail(LoginError.NoBrowser)
        }
    }

    Scaffold(
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text(stringResource(if (relogin != null) R.string.relogin else R.string.add_account)) },
                    navigationIcon = {
                        IconButton(onClick = {
                            // トークンに換えている途中なら、そのまま終わらせる (できたら切り替わる)
                            if (status != LoginStatus.Exchanging) login.cancel()
                            onBack()
                        }) {
                            Icon(Symbols.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                )
            }
        },
    ) { padding ->
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
                if (relogin != null) {
                    stringResource(R.string.relogin_description, relogin.label)
                } else {
                    stringResource(R.string.login_description)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                label = { Text(stringResource(R.string.login_server)) },
                placeholder = { Text("mastodon.social") },
                singleLine = true,
                enabled = !busy && relogin == null,
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
            stringResource(status.error.messageRes, status.account.orEmpty()),
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
        LoginError.ExistingAccountNeedsLogin -> R.string.login_error_existing_needs_login
        LoginError.ExistingAccountUnavailable -> R.string.login_error_existing_unavailable
        LoginError.DifferentAccount -> R.string.login_error_different_account
        LoginError.AlreadyAdded -> R.string.login_error_already_added
    }

/** ログインし直す対象 (保存しているアカウント)。[label] は `@user@host` (文言用)。 */
data class ReloginTarget(val key: AccountKey, val serverUrl: String, val label: String)

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
