package es.aria_on_the_planet.quasar.ui.account

import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.ui.component.post.OwnPostActions
import es.aria_on_the_planet.quasar.ui.composer.PostEditStore
import kotlinx.coroutines.flow.StateFlow

/** ログイン中のアカウントでの自分のポストの編集・削除 ([OwnPostActions])。[AccountUiContext] が持ち、`QuasarNavHost` が提供する。 */
class SessionOwnPostActions(
    private val store: PostEditStore,
) : OwnPostActions {
    override val busyStatusId: StateFlow<String?> get() = store.busyStatusId

    override fun edit(post: Post) = store.startEdit(post)

    override fun delete(post: Post) = store.delete(post)
}
