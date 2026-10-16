package es.aria_on_the_planet.quasar.data.mapper

import es.aria_on_the_planet.quasar.data.remote.dto.InstanceDto
import es.aria_on_the_planet.quasar.domain.model.ComposeLimits
import es.aria_on_the_planet.quasar.domain.model.PostDraft
import es.aria_on_the_planet.quasar.domain.model.PostVisibility
import es.aria_on_the_planet.quasar.domain.model.UploadState
import kotlin.time.Duration.Companion.seconds

/** 返らなかった項目は [ComposeLimits] の既定値。 */
fun InstanceDto.toComposeLimits(): ComposeLimits {
    val defaults = ComposeLimits()
    val statuses = configuration?.statuses
    val polls = configuration?.polls
    val media = configuration?.mediaAttachments
    return ComposeLimits(
        maxCharacters = statuses?.maxCharacters ?: defaults.maxCharacters,
        charactersPerUrl = statuses?.charactersReservedPerUrl ?: defaults.charactersPerUrl,
        maxMediaAttachments = media?.maxMediaAttachments
            ?: statuses?.maxMediaAttachments
            ?: defaults.maxMediaAttachments,
        maxPollOptions = polls?.maxOptions ?: defaults.maxPollOptions,
        maxPollOptionCharacters = polls?.maxCharactersPerOption ?: defaults.maxPollOptionCharacters,
        minPollExpiration = polls?.minExpiration?.seconds ?: defaults.minPollExpiration,
        maxPollExpiration = polls?.maxExpiration?.seconds ?: defaults.maxPollExpiration,
        imageSizeLimit = media?.imageSizeLimit ?: defaults.imageSizeLimit,
        videoSizeLimit = media?.videoSizeLimit ?: defaults.videoSizeLimit,
        supportedMimeTypes = media?.supportedMimeTypes?.toSet(),
        maxDescriptionCharacters = media?.descriptionLimit ?: defaults.maxDescriptionCharacters,
    )
}

/**
 * `POST /api/v1/statuses` のフォーム項目。アップロード済みでない添付は含めない
 * (送信可否は [es.aria_on_the_planet.quasar.domain.model.problems] で先に確かめる)。
 */
fun PostDraft.toFormParams(): List<Pair<String, String>> = buildList {
    add("status" to text)
    add("visibility" to visibility.apiName)
    contentWarning?.takeIf { it.isNotBlank() }?.let { add("spoiler_text" to it) }
    if (attachments.isNotEmpty()) {
        add("sensitive" to sensitive.toString())
        attachments.forEach { attachment ->
            (attachment.state as? UploadState.Uploaded)?.let { add("media_ids[]" to it.mediaId) }
        }
    }
    poll?.let { poll ->
        poll.options.forEach { add("poll[options][]" to it) }
        add("poll[expires_in]" to poll.expiresIn.inWholeSeconds.toString())
        add("poll[multiple]" to poll.multiple.toString())
    }
    inReplyToId?.let { add("in_reply_to_id" to it) }
}

/** API での公開範囲の名前 ([postVisibility] の逆)。 */
val PostVisibility.apiName: String
    get() = when (this) {
        PostVisibility.Public -> "public"
        PostVisibility.Unlisted -> "unlisted"
        PostVisibility.Private -> "private"
        PostVisibility.Direct -> "direct"
    }
