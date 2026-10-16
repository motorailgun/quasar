package es.aria_on_the_planet.quasar.data.repository

import es.aria_on_the_planet.quasar.data.media.ProgressRequestBody
import es.aria_on_the_planet.quasar.data.media.UploadSource
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.data.session.Session
import kotlinx.coroutines.delay
import java.io.IOException

/** 投稿に添付するメディアのアップロード。 */
class MediaRepository(
    private val api: MastodonApi,
) {
    /**
     * [source] をアップロードし、投稿に使えるメディアの ID を返す。動画等でサーバの変換待ちになったら
     * [onProcessing] を呼び、終わるまで待つ。未認証では使えない。
     *
     * @param onProgress 送った割合 (0〜1、サイズ不明なら `null`)。通信のスレッドから呼ばれる。
     */
    suspend fun upload(
        session: Session,
        source: UploadSource,
        description: String?,
        onProgress: (Float?) -> Unit,
        onProcessing: (mediaId: String) -> Unit,
    ): String {
        val token = requireNotNull(session.accessToken) { "Uploading requires login" }
        val uploaded = api.uploadMedia(
            serverUrl = session.serverUrl,
            accessToken = token,
            fileName = source.fileName,
            file = ProgressRequestBody(source, onProgress),
            description = description,
        )
        if (uploaded.url != null) return uploaded.id
        onProcessing(uploaded.id)
        repeat(MaxPolls) { attempt ->
            delay(pollDelayMillis(attempt))
            if (api.media(session.serverUrl, token, uploaded.id).url != null) return uploaded.id
        }
        throw IOException("Media ${uploaded.id} is still processing")
    }

    /** アップロード済み (未投稿) のメディアの代替テキストを変える。 */
    suspend fun updateDescription(session: Session, mediaId: String, description: String) {
        val token = requireNotNull(session.accessToken) { "Updating media requires login" }
        api.updateMediaDescription(session.serverUrl, token, mediaId, description)
    }

    private companion object {
        /** 1 秒から 1 秒ずつ延ばし、5 秒で頭打ち。合計でおよそ 4 分待つ。 */
        const val MaxPolls = 50
        fun pollDelayMillis(attempt: Int): Long = (attempt + 1).coerceAtMost(5) * 1000L
    }
}
