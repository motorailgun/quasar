package es.aria_on_the_planet.quasar.debug

import es.aria_on_the_planet.quasar.data.mapper.toDomain
import es.aria_on_the_planet.quasar.data.mapper.toProfile
import es.aria_on_the_planet.quasar.data.remote.dto.AccountDto
import es.aria_on_the_planet.quasar.data.remote.dto.CustomEmojiDto
import es.aria_on_the_planet.quasar.data.remote.dto.StatusDto
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.Profile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * ギャラリーの固定データ (`assets/gallery/` の JSON、`scripts/gallery/generate.py` が生成) を読む。
 * JSON は API の応答と同じ形なので、本番と同じ DTO と mapper を通す。
 */
internal object GalleryFixtures {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /** `Status` の配列。 */
    fun posts(text: String): List<Post> = json.decodeFromString<List<StatusDto>>(text).map { it.toDomain() }

    /** `{"account": Account, "statuses": [Status]}`。 */
    fun profile(text: String): ProfileFixture {
        val dto = json.decodeFromString<ProfileFixtureDto>(text)
        return ProfileFixture(dto.account.toProfile(), dto.statuses.map { it.toDomain() })
    }

    /** `CustomEmoji` の配列 (`GET /api/v1/custom_emojis` と同じ形)。 */
    fun emojis(text: String): List<CustomEmoji> = json.decodeFromString<List<CustomEmojiDto>>(text).map { it.toDomain() }
}

internal data class ProfileFixture(val profile: Profile, val posts: List<Post>)

@Serializable
private data class ProfileFixtureDto(val account: AccountDto, val statuses: List<StatusDto>)
