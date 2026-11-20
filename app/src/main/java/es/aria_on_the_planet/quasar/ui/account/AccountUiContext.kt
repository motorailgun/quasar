package es.aria_on_the_planet.quasar.ui.account

import es.aria_on_the_planet.quasar.data.account.AccountContext
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.ui.component.account.AccountActions
import es.aria_on_the_planet.quasar.ui.component.post.OwnPostActions
import es.aria_on_the_planet.quasar.ui.composer.ComposerSession
import es.aria_on_the_planet.quasar.ui.composer.DraftStore
import es.aria_on_the_planet.quasar.ui.composer.EditTarget
import es.aria_on_the_planet.quasar.ui.composer.PostEditStore
import kotlinx.coroutines.CoroutineScope

/**
 * アカウント 1 つ分の UI 側の依存 (書きかけの投稿・自分のポストの編集・メニューの操作)。データ層の依存は [account]。
 * `AccountManager` がアカウントごとに持ち (切り替えても閉じずにログアウトまで)、`QuasarNavHost` が使っているアカウントの
 * 分を各画面に渡す (ViewModel には [account])。
 * [AccountContext] は data 層 (UI のクラスを参照しない) なので、UI のものはこちらに分けている。
 *
 * @param createComposer 投稿欄を作る (`AppContainer` がサーバ・設定の依存を渡す)。
 */
class AccountUiContext(
    val account: AccountContext,
    private val createComposer: (AccountContext, CoroutineScope, EditTarget?, (Post) -> Unit) -> ComposerSession,
) {
    /** 書きかけの投稿 (クイック投稿バーと投稿画面で共有)。 */
    val draftStore = DraftStore { scope, onPublished -> composerSession(scope, null, onPublished) }

    /** 詳細のパネル (タブレット表示) の中の書きかけの投稿。[draftStore] とは別で、パネルの中の画面だけで共有する。 */
    val panelDraftStore = DraftStore { scope, onPublished -> composerSession(scope, null, onPublished) }

    /** 自分のポストの編集・削除 (どの画面のメニューからでも同じ流れ)。 */
    val postEditStore = PostEditStore(account.statusRepository) { scope, target, onSaved ->
        composerSession(scope, target, onSaved)
    }

    /** ポストのメニューのブロック等・ブラウザで開く。 */
    val accountActions: AccountActions = SessionAccountActions(account)

    /** 自分のポストの編集・削除。編集の投稿欄はどの画面から開いても全画面の上に重ねる (`PostEditorHost`)。 */
    val ownPostActions: OwnPostActions = SessionOwnPostActions(postEditStore)

    /**
     * 投稿欄 1 つ分。[editing] が `null` なら新しい投稿 ([draftStore] が持つ。共有から開いた投稿画面も)、あれば自分のポストの
     * 編集 ([postEditStore] が持つ)。[onPublished] は投稿・保存できたとき。
     */
    fun composerSession(scope: CoroutineScope, editing: EditTarget?, onPublished: (Post) -> Unit): ComposerSession =
        createComposer(account, scope, editing, onPublished)

    /**
     * ログアウト等でこのアカウントを使わなくなった: 下書きのアップロード・編集を止め、データ層 ([AccountContext.close]) も
     * 閉じる。以後は使わない。
     */
    fun close() {
        draftStore.close()
        panelDraftStore.close()
        postEditStore.dispose()
        account.close()
    }
}
