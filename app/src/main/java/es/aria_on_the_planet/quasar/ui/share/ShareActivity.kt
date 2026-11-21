package es.aria_on_the_planet.quasar.ui.share

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import es.aria_on_the_planet.quasar.MainActivity
import es.aria_on_the_planet.quasar.QuasarApplication
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.data.account.AccountKey
import es.aria_on_the_planet.quasar.di.AppContainer
import es.aria_on_the_planet.quasar.ui.QuasarAppContent
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.ui.composer.ComposerScreen

/**
 * 他のアプリからの共有 (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`) を受けて投稿画面を出す。
 *
 * MainActivity とは別の Activity にしている: 標準の launchMode なので共有元のタスクに積まれ、投稿・破棄で
 * [finish] すると共有元のアプリに戻る (MainActivity は singleTask なので、受けると Quasar のタスクに移ってしまう)。
 * manifest の `taskAffinity=""` は、共有元が `FLAG_ACTIVITY_NEW_TASK` を付けてきたときに MainActivity の
 * タスクに積まれないようにするため。
 *
 * 共有された URI を読める (一時的な許可) のはこの Activity が生きている間なので、開いたらすぐアップロードする。
 *
 * 投稿するのは開いたときに使っていたアカウントで、開いている間に MainActivity で切り替えても追わない。そのアカウントが
 * ログアウトされた・ログインし直された (依存が閉じられた) ら閉じる。
 */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val content = intent.readSharedContent(::isOwnProvider)
        if (content == null) {
            finish()
            return
        }
        val container = (application as QuasarApplication).container
        setContent {
            QuasarAppContent(container) {
                ShareScreen(
                    container = container,
                    content = content,
                    onClose = ::finish,
                    onPublished = {
                        Toast.makeText(this, R.string.share_posted, Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onAccountClosed = {
                        Toast.makeText(this, R.string.share_account_closed, Toast.LENGTH_LONG).show()
                        finish()
                    },
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    },
                )
            }
        }
    }

    private fun isOwnProvider(authority: String): Boolean =
        packageManager.resolveContentProvider(authority, PackageManager.ComponentInfoFlags.of(0))
            ?.packageName == packageName
}

@Composable
private fun ShareScreen(
    container: AppContainer,
    content: SharedContent,
    onClose: () -> Unit,
    onPublished: () -> Unit,
    onAccountClosed: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val accountManager = container.accountManager
    val active by accountManager.active.collectAsStateWithLifecycle()
    // 投稿するアカウント。最初に決まった (開いたときに使っていた) アカウントに固定し、MainActivity で切り替えても追わない
    // (追うと入力が消え、前のアカウントの下書きがアップロードを続ける)。未ログインの間だけ使っているアカウントを待つ。
    // 将来この画面でアカウントを選べるようにするときは、選んだキーを pinnedKey に入れる (下書きの中身の引き継ぎは別に要る)。
    var pinnedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val key = pinnedKey?.let(::AccountKey) ?: active?.key
    if (key == null) {
        NotLoggedIn(onClose = onClose, onOpenApp = onOpenApp)
        return
    }
    LaunchedEffect(key) { pinnedKey = key.value }
    // プロセスの作り直しの後は、使っているアカウントでなくなっていても開く (無ければログアウトされた)
    val account = remember(key) { accountManager.context(key) }
    val closed = account?.closed?.collectAsStateWithLifecycle()?.value ?: true
    if (account == null || closed) {
        LaunchedEffect(Unit) { onAccountClosed() }
        return
    }
    val viewModel: ShareViewModel = viewModel(
        key = "share#${key.value}",
        factory = viewModelFactory {
            initializer {
                ShareViewModel(
                    createDraft = { scope, published -> account.composerSession(scope, null, published) },
                    content = content,
                )
            }
        },
    )
    val published by viewModel.published.collectAsStateWithLifecycle()
    LaunchedEffect(published) {
        if (published) onPublished()
    }
    val skipped by viewModel.skippedMedia.collectAsStateWithLifecycle()
    // 返信先は無いので、ComposerScreen が LocalNavigator を読むこと (返信先のメディアのタップ) は無い
    ComposerScreen(
        session = viewModel.draft,
        onClose = onClose,
        onFinish = onClose,
        keepDraftOnClose = false,
        notice = if (skipped > 0) pluralStringResource(R.plurals.share_media_skipped, skipped, skipped) else null,
        onNoticeShown = viewModel::skippedMediaShown,
    )
}

/** 未ログイン: アプリを開いてログインしてもらう (共有の中身は引き継がない)。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotLoggedIn(onClose: () -> Unit, onOpenApp: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Symbols.Filled.Close, contentDescription = stringResource(R.string.composer_close))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.share_not_logged_in),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onOpenApp) { Text(stringResource(R.string.login_submit)) }
        }
    }
}
