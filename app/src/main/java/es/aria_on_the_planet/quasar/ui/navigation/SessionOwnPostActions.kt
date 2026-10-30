package es.aria_on_the_planet.quasar.ui.navigation

import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.ui.component.post.OwnPostActions
import es.aria_on_the_planet.quasar.ui.composer.PostEditStore
import kotlinx.coroutines.flow.StateFlow

/** ログイン中の [session] での自分のポストの編集・削除 ([OwnPostActions])。`QuasarNavHost` が提供する。 */
class SessionOwnPostActions(
    private val session: Session,
    private val store: PostEditStore,
) : OwnPostActions {
    override val busyStatusId: StateFlow<String?> get() = store.busyStatusId

    override fun edit(post: Post) = store.startEdit(session, post)

    override fun delete(post: Post) = store.delete(session, post)
}
