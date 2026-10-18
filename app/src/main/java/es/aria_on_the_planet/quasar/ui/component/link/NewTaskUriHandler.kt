package es.aria_on_the_planet.quasar.ui.component.link

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri

/**
 * 外部のリンクを開く Intent。`FLAG_ACTIVITY_NEW_TASK` を付けて、開いた先のアプリ (Pixiv 等) を Quasar のタスクに積まず
 * 自分のタスクで開かせる (付けないと受け口が standard の Activity は Quasar のタスクに載り、recents で 1 枚にまとまる)。
 */
fun externalViewIntent(uri: String): Intent =
    Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/**
 * [externalViewIntent] で開く [UriHandler]。`QuasarAppContent` が `LocalUriHandler` として提供する。
 * 開けるアプリが無いときは Compose 標準の `AndroidUriHandler` と同じく [IllegalArgumentException]。
 */
private class NewTaskUriHandler(private val context: Context) : UriHandler {
    override fun openUri(uri: String) {
        try {
            context.startActivity(externalViewIntent(uri))
        } catch (e: ActivityNotFoundException) {
            throw IllegalArgumentException("Can't open $uri.", e)
        }
    }
}

@Composable
fun rememberNewTaskUriHandler(): UriHandler {
    val context = LocalContext.current
    return remember(context) { NewTaskUriHandler(context) }
}
