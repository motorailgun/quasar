package es.aria_on_the_planet.quasar.data.mapper

import es.aria_on_the_planet.quasar.data.remote.dto.InstanceDto
import es.aria_on_the_planet.quasar.domain.model.ComposeLimits
import es.aria_on_the_planet.quasar.domain.model.DraftAttachment
import es.aria_on_the_planet.quasar.domain.model.PollDraft
import es.aria_on_the_planet.quasar.domain.model.PostDraft
import es.aria_on_the_planet.quasar.domain.model.PostVisibility
import es.aria_on_the_planet.quasar.domain.model.UploadFailure
import es.aria_on_the_planet.quasar.domain.model.UploadState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class DraftMappersTest {
    @Test
    fun plainPost() {
        assertEquals(
            listOf("status" to "hi", "visibility" to "unlisted"),
            PostDraft("hi", visibility = PostVisibility.Unlisted).toFormParams(),
        )
    }

    @Test
    fun contentWarningAndReply() {
        val params = PostDraft("hi", contentWarning = "spoiler", inReplyToId = "42").toFormParams()
        assertEquals(
            listOf("status" to "hi", "visibility" to "public", "spoiler_text" to "spoiler", "in_reply_to_id" to "42"),
            params,
        )
    }

    @Test
    fun blankContentWarningIsOmitted() {
        assertEquals(2, PostDraft("hi", contentWarning = " ").toFormParams().size)
    }

    @Test
    fun mediaIdsOnlyForUploaded() {
        val params = PostDraft(
            "hi",
            sensitive = true,
            attachments = listOf(
                DraftAttachment("1", null, state = UploadState.Uploaded("m1")),
                DraftAttachment("2", null, state = UploadState.Failed(UploadFailure.Failed)),
            ),
        ).toFormParams()
        assertEquals(listOf("sensitive" to "true", "media_ids[]" to "m1"), params.drop(2))
    }

    @Test
    fun poll() {
        val params = PostDraft("q", poll = PollDraft(listOf("a", "b"), 3.days, multiple = true)).toFormParams()
        assertEquals(
            listOf(
                "poll[options][]" to "a",
                "poll[options][]" to "b",
                "poll[expires_in]" to "259200",
                "poll[multiple]" to "true",
            ),
            params.drop(2),
        )
    }

    @Test
    fun visibilityNamesRoundTrip() {
        PostVisibility.entries.forEach { assertEquals(it, postVisibility(it.apiName)) }
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun limitsFromInstance() {
        val dto = json.decodeFromString<InstanceDto>(
            """
            {"configuration": {
              "urls": {"streaming": "wss://example.com"},
              "statuses": {"max_characters": 1000, "max_media_attachments": 8, "characters_reserved_per_url": 23},
              "media_attachments": {"supported_mime_types": ["image/png"], "image_size_limit": 100, "video_size_limit": 200, "description_limit": 1000},
              "polls": {"max_options": 10, "max_characters_per_option": 100, "min_expiration": 300, "max_expiration": 2629746}
            }}
            """,
        )
        val limits = dto.toComposeLimits()
        assertEquals(1000, limits.maxCharacters)
        assertEquals(8, limits.maxMediaAttachments)
        assertEquals(10, limits.maxPollOptions)
        assertEquals(100, limits.maxPollOptionCharacters)
        assertEquals(5.minutes, limits.minPollExpiration)
        assertEquals(100L, limits.imageSizeLimit)
        assertEquals(200L, limits.videoSizeLimit)
        assertEquals(setOf("image/png"), limits.supportedMimeTypes)
        assertEquals(1000, limits.maxDescriptionCharacters)
    }

    @Test
    fun missingLimitsFallBackToDefaults() {
        assertEquals(ComposeLimits(), json.decodeFromString<InstanceDto>("{}").toComposeLimits())
    }
}
