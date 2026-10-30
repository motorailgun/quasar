package es.aria_on_the_planet.quasar.ui.component.post

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import es.aria_on_the_planet.quasar.domain.model.Post
import kotlinx.coroutines.flow.StateFlow

/**
 * 自分のポストの編集・削除 (ポストのメニュー [PostMenu] から)。`QuasarNavHost` が提供し、無い所 (ギャラリー等) では
 * メニューに出さない。編集の投稿欄はどの画面からでも全画面の上に重ね、結果は各画面の一覧に流れる。
 */
@Stable
interface OwnPostActions {
    /** 編集の準備・削除の最中のポストの `statusId` (メニューのボタンを読み込み中にする)。 */
    val busyStatusId: StateFlow<String?>

    fun edit(post: Post)

    /** 削除する。確認は呼び出し側 ([PostMenu]) が済ませてから呼ぶ。 */
    fun delete(post: Post)
}

val LocalOwnPostActions = staticCompositionLocalOf<OwnPostActions?> { null }
