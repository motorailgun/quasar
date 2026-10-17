package es.aria_on_the_planet.quasar.ui.component.dialog

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.settings.ConfirmTarget
import kotlinx.coroutines.CompletableDeferred

/**
 * 操作の前の確認 (投稿・ブースト等)。設定で確認する操作ならダイアログを出して答えを待ち、そうでなければすぐ `true`。
 * ダイアログは [ActionConfirmationHost] が描く。ViewModel は確認の有無を知らない (画面側で [confirm] してから呼ぶ)。
 */
@Stable
class ActionConfirmer internal constructor() {
    /** 確認する操作 (設定)。[ActionConfirmationHost] が更新する。 */
    internal var enabled: Set<ConfirmTarget> = emptySet()

    internal var pending by mutableStateOf<PendingConfirmation?>(null)
        private set

    /** [target] を行ってよいか。確認しない設定なら `true`、確認するならダイアログの答え (閉じたら `false`)。 */
    suspend fun confirm(target: ConfirmTarget): Boolean {
        if (target !in enabled) return true
        // 答えを待っている確認があれば取りやめにして、新しい方を出す
        pending?.answer?.complete(false)
        val request = PendingConfirmation(target)
        pending = request
        return try {
            request.answer.await()
        } finally {
            // 呼び出し側がキャンセルされた (画面を離れた) ときもダイアログを消す
            if (pending === request) pending = null
        }
    }
}

internal class PendingConfirmation(val target: ConfirmTarget) {
    val answer = CompletableDeferred<Boolean>()
}

/** 確認。[ActionConfirmationHost] の外 (ギャラリー等) では確認しない。 */
val LocalActionConfirmer = staticCompositionLocalOf { ActionConfirmer() }

/** [confirmations] の操作を確認するようにして [content] を描き、確認のダイアログを出す。`QuasarAppContent` が使う。 */
@Composable
fun ActionConfirmationHost(confirmations: Set<ConfirmTarget>, content: @Composable () -> Unit) {
    val confirmer = remember { ActionConfirmer() }
    SideEffect { confirmer.enabled = confirmations }
    CompositionLocalProvider(LocalActionConfirmer provides confirmer, content = content)
    confirmer.pending?.let { request ->
        val texts = request.target.texts
        ConfirmDialog(
            title = stringResource(texts.title),
            confirmLabel = stringResource(texts.confirm),
            dismissLabel = stringResource(R.string.cancel),
            message = texts.message?.let { stringResource(it) },
            destructive = texts.destructive,
            onConfirm = { request.answer.complete(true) },
            onDismiss = { request.answer.complete(false) },
        )
    }
}

private class ConfirmationTexts(
    @StringRes val title: Int,
    @StringRes val confirm: Int,
    @StringRes val message: Int? = null,
    val destructive: Boolean = false,
)

private val ConfirmTarget.texts: ConfirmationTexts
    get() = when (this) {
        ConfirmTarget.Post -> ConfirmationTexts(R.string.confirm_post_title, R.string.confirm_post)
        ConfirmTarget.Boost -> ConfirmationTexts(R.string.confirm_boost_title, R.string.confirm_boost)
        ConfirmTarget.Unboost -> ConfirmationTexts(R.string.confirm_unboost_title, R.string.confirm_unboost)
        ConfirmTarget.Favourite -> ConfirmationTexts(R.string.confirm_favourite_title, R.string.confirm_favourite)
        ConfirmTarget.Unfavourite -> ConfirmationTexts(R.string.confirm_unfavourite_title, R.string.confirm_remove)
        ConfirmTarget.Bookmark -> ConfirmationTexts(R.string.confirm_bookmark_title, R.string.confirm_bookmark)
        ConfirmTarget.Unbookmark -> ConfirmationTexts(R.string.confirm_unbookmark_title, R.string.confirm_remove)
        ConfirmTarget.EditPost -> ConfirmationTexts(R.string.confirm_edit_post_title, R.string.confirm_edit_post)
        ConfirmTarget.DeletePost -> ConfirmationTexts(
            R.string.confirm_delete_post_title,
            R.string.confirm_delete_post,
            message = R.string.confirm_delete_post_message,
            destructive = true,
        )
    }
