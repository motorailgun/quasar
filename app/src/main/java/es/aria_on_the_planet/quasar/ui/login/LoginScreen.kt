package es.aria_on_the_planet.quasar.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.data.session.Session
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** 暫定ログイン画面。OAuth 実装までサーバ URL とアクセストークンを直接入力する。 */
@Composable
fun LoginScreen(onLogin: (Session) -> Unit) {
    var server by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    val serverUrl = normalizeServerUrl(server)

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayMedium)
            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                label = { Text(stringResource(R.string.login_server)) },
                placeholder = { Text("mastodon.social") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(stringResource(R.string.login_token)) },
                supportingText = { Text(stringResource(R.string.login_token_hint)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { serverUrl?.let { onLogin(Session(it, token.trim().ifEmpty { null })) } },
                enabled = serverUrl != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.login_submit))
            }
        }
    }
}

internal fun normalizeServerUrl(input: String): String? {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
    return withScheme.takeIf { it.toHttpUrlOrNull() != null }
}
