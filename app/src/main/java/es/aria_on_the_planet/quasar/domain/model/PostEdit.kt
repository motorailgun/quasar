package es.aria_on_the_planet.quasar.domain.model

import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** 編集用のポストの原文 (`GET /api/v1/statuses/:id/source`)。HTML ではなく投稿時に書いたままのテキスト。 */
data class StatusSource(
    val text: String,
    /** CW の警告文。CW 無しなら空。 */
    val contentWarning: String,
)

/**
 * [post] (自分のポスト) を編集するときの最初の下書き。
 *
 * * 本文・CW は [source] (API の `text` は HTML なので使わない)。
 * * 添付は投稿済みのメディアをアップロード済みとして並べる (`localId` はメディアの ID)。
 * * 投票は編集で送った時点から期間が数え直しになるので、残り時間に最も近い [pollDurations] の値にする。
 * * 公開範囲・返信先は編集できない (下書きの公開範囲はポストのまま、返信先は持たない)。
 */
fun editDraftOf(post: Post, source: StatusSource, pollDurations: List<Duration>, now: Instant): PostDraft = PostDraft(
    text = source.text,
    contentWarning = source.contentWarning.ifEmpty { null },
    visibility = post.visibility,
    sensitive = post.sensitive,
    attachments = post.mediaAttachments.map { it.toDraftAttachment() },
    poll = post.poll?.let { poll ->
        // 期限の無い投票は新規と同じ既定値
        val remaining = poll.expiresAt?.let { (it.toEpochMilli() - now.toEpochMilli()).milliseconds }
            ?: PollDraft().expiresIn
        PollDraft(
            options = poll.options.map { it.title },
            expiresIn = pollDurations.minByOrNull { (it - remaining).absoluteValue } ?: remaining,
            multiple = poll.multiple,
        )
    },
)

private fun MediaAttachment.toDraftAttachment() = DraftAttachment(
    localId = id,
    previewUri = previewUrl ?: url,
    description = description.orEmpty(),
    state = UploadState.Uploaded(id),
    // 一緒に付けられるかの判定 (isVideoLike) と GIF の表示に使うだけなので、種別の代表値でよい
    mimeType = when (type) {
        MediaType.Image -> "image/jpeg"
        MediaType.Gifv -> "image/gif"
        MediaType.Video -> "video/mp4"
        MediaType.Audio -> "audio/mpeg"
        MediaType.Unknown -> null
    },
)
