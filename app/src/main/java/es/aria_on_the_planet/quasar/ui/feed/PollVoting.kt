package es.aria_on_the_planet.quasar.ui.feed

import android.util.Log
import es.aria_on_the_planet.quasar.data.repository.StatusRepository
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Poll
import es.aria_on_the_planet.quasar.domain.model.Post
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

/**
 * [post] の投票に [choices] で投票し、成功したら [apply] で画面の状態に反映する。
 * 呼び出し元 (Composable) が途中で破棄されても投票と反映は [this] (viewModelScope) 上で完了させる。
 */
internal suspend fun CoroutineScope.submitVote(
    repository: StatusRepository,
    session: Session,
    post: Post,
    choices: List<Int>,
    apply: (Poll) -> Unit,
): Boolean {
    val pollId = post.poll?.id ?: return false
    return async {
        try {
            apply(repository.vote(session, pollId, choices))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PollVoting", "Failed to vote on poll $pollId", e)
            false
        }
    }.await()
}
