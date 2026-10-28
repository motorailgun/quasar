package es.aria_on_the_planet.quasar.data.mapper

import es.aria_on_the_planet.quasar.data.remote.dto.AccountDto
import es.aria_on_the_planet.quasar.data.remote.dto.InstanceDto
import es.aria_on_the_planet.quasar.domain.model.ProfileFieldSource
import es.aria_on_the_planet.quasar.domain.model.ProfileLimits
import es.aria_on_the_planet.quasar.domain.model.ProfileSource
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileMappersTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun params() {
        val source = ProfileSource(
            displayName = "Name :blob:",
            note = "line 1\nline 2",
            fields = listOf(ProfileFieldSource("Site", "https://example.invalid"), ProfileFieldSource("", ""), ProfileFieldSource("Pronouns", "")),
        )
        assertEquals(
            listOf(
                "display_name" to "Name :blob:",
                "note" to "line 1\nline 2",
                // 空のフィールドは詰める
                "fields_attributes[0][name]" to "Site",
                "fields_attributes[0][value]" to "https://example.invalid",
                "fields_attributes[1][name]" to "Pronouns",
                "fields_attributes[1][value]" to "",
            ),
            source.toUpdateCredentialsParams(),
        )
    }

    @Test
    fun clearingAllFieldsSendsOneEmptyField() {
        // 何も送らないとフィールドは変わらないので、空のものを 1 つ送って消す
        val params = ProfileSource("a", "", listOf(ProfileFieldSource(" ", ""))).toUpdateCredentialsParams()
        assertEquals(
            listOf(
                "display_name" to "a",
                "note" to "",
                "fields_attributes[0][name]" to "",
                "fields_attributes[0][value]" to "",
            ),
            params,
        )
    }

    @Test
    fun sourceFromCredentialAccount() {
        val dto = json.decodeFromString<AccountDto>(
            """
            {"id":"1","username":"a","acct":"a","url":"https://example.invalid/@a","avatar":"",
             "display_name":"A :blob:","note":"<p>html</p>",
             "fields":[{"name":"Site","value":"<a href=\"https://example.invalid\">example.invalid</a>"}],
             "source":{"privacy":"public","sensitive":false,"note":"plain\n#tag",
                       "fields":[{"name":"Site","value":"https://example.invalid","verified_at":null}]}}
            """.trimIndent(),
        )
        assertEquals(
            ProfileSource("A :blob:", "plain\n#tag", listOf(ProfileFieldSource("Site", "https://example.invalid"))),
            dto.toProfileSource(),
        )
    }

    @Test
    fun noSource() {
        val dto = json.decodeFromString<AccountDto>("""{"id":"1","username":"a","acct":"a","url":"u","avatar":""}""")
        assertNull(dto.toProfileSource())
    }

    @Test
    fun limits() {
        val dto = json.decodeFromString<InstanceDto>(
            """{"configuration":{"accounts":{"max_display_name_length":40,"max_note_length":500,"max_profile_fields":6,
               "profile_field_name_limit":255,"profile_field_value_limit":2048}}}""",
        )
        assertEquals(ProfileLimits(40, 500, 6, 255, 2048), dto.toProfileLimits())
        // 返さないサーバは既定値
        assertEquals(ProfileLimits(), InstanceDto().toProfileLimits())
    }
}
