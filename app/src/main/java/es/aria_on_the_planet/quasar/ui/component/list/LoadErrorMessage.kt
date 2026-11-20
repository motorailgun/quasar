package es.aria_on_the_planet.quasar.ui.component.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.ui.navigation.LocalNavigator

/**
 * 読み込みの失敗の文言。[needsLogin] (401 / 403。`LoadError.needsLogin`) なら下に「ログインし直す」のボタンを出す
 * (使っているアカウントのログインし直し。`Navigator.openRelogin`)。
 */
@Composable
fun LoadErrorMessage(message: String, needsLogin: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = if (needsLogin) TextAlign.Center else null)
        if (needsLogin) {
            val navigator = LocalNavigator.current
            Button(onClick = navigator::openRelogin) { Text(stringResource(R.string.relogin)) }
        }
    }
}
