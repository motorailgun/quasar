package es.aria_on_the_planet.quasar.domain.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/** 送信前のポスト。 */
data class PostDraft(
    /** 本文 (プレーンテキスト)。 */
    val text: String,
    /** CW の警告文。`null` なら CW 無し。 */
    val contentWarning: String? = null,
    val visibility: PostVisibility = PostVisibility.Public,
    /** メディアの閲覧注意。 */
    val sensitive: Boolean = false,
    val attachments: List<DraftAttachment> = emptyList(),
    /** 投票。Mastodon ではメディアと同時に付けられない。 */
    val poll: PollDraft? = null,
    val inReplyToId: String? = null,
)

/** 作成中の投票。 */
data class PollDraft(
    val options: List<String> = listOf("", ""),
    val expiresIn: Duration = 1.days,
    val multiple: Boolean = false,
)

/** 添付予定のメディア。アップロードは別途行い、[state] が [UploadState.Uploaded] になったものだけ送れる。 */
data class DraftAttachment(
    /** 端末内での識別子 (アップロード前は ID が無いため)。 */
    val localId: String,
    /** サムネイルに使う URI (content:// 等)。 */
    val previewUri: String?,
    /** 代替テキスト。 */
    val description: String = "",
    val state: UploadState = UploadState.Pending,
)

sealed interface UploadState {
    data object Pending : UploadState
    /** @param progress 0〜1。不明なら `null`。 */
    data class Uploading(val progress: Float?) : UploadState
    data class Uploaded(val mediaId: String) : UploadState
    data object Failed : UploadState
}

/** 何も入力されていないか (破棄の確認が要らないか)。 */
val PostDraft.isEmpty: Boolean
    get() = text.isBlank() && contentWarning.isNullOrBlank() && attachments.isEmpty() && poll == null

/** 送信を妨げる問題。 */
enum class DraftProblem {
    /** 本文が空 (メディアがあれば空でもよい)。 */
    EmptyText,
    TooLong,
    MediaAndPoll,
    TooManyAttachments,
    AttachmentNotUploaded,
    TooFewPollOptions,
    TooManyPollOptions,
    BlankPollOption,
    PollOptionTooLong,
}

/** [limits] に照らした問題の一覧。空なら送信できる。 */
fun PostDraft.problems(limits: ComposeLimits): Set<DraftProblem> = buildSet {
    if (text.isBlank() && attachments.isEmpty()) add(DraftProblem.EmptyText)
    if (characterCount(limits) > limits.maxCharacters) add(DraftProblem.TooLong)
    if (attachments.isNotEmpty() && poll != null) add(DraftProblem.MediaAndPoll)
    if (attachments.size > limits.maxMediaAttachments) add(DraftProblem.TooManyAttachments)
    if (attachments.any { it.state !is UploadState.Uploaded }) add(DraftProblem.AttachmentNotUploaded)
    if (poll != null) {
        if (poll.options.size < 2) add(DraftProblem.TooFewPollOptions)
        if (poll.options.size > limits.maxPollOptions) add(DraftProblem.TooManyPollOptions)
        if (poll.options.any { it.isBlank() }) add(DraftProblem.BlankPollOption)
        if (poll.options.any { it.length > limits.maxPollOptionCharacters }) add(DraftProblem.PollOptionTooLong)
    }
}

/** 文字数の上限に数える文字数 (CW + 本文)。数え方は [countPostCharacters]。 */
fun PostDraft.characterCount(limits: ComposeLimits): Int =
    countPostCharacters(text, limits) + countPostCharacters(contentWarning.orEmpty(), limits)
