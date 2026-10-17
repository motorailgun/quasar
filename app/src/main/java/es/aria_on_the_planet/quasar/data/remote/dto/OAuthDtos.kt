package es.aria_on_the_planet.quasar.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `POST /api/v1/apps` の応答 (CredentialApplication)。https://docs.joinmastodon.org/entities/Application/ */
@Serializable
data class RegisteredAppDto(
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
)

/** `POST /oauth/token` の応答。https://docs.joinmastodon.org/entities/Token/ */
@Serializable
data class TokenDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    val scope: String = "",
)
