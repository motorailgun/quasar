package es.aria_on_the_planet.quasar.data.remote

import es.aria_on_the_planet.quasar.data.remote.dto.StatusDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class MastodonApiException(val code: Int, message: String) : IOException(message)

/** Mastodon REST API の薄いクライアント。 */
class MastodonApi(
    private val client: OkHttpClient,
    private val json: Json,
) {
    suspend fun homeTimeline(
        serverUrl: String,
        accessToken: String?,
        maxId: String? = null,
        limit: Int = DEFAULT_LIMIT,
    ): List<StatusDto> = getStatuses(serverUrl, accessToken, "api/v1/timelines/home") {
        maxId?.let { addQueryParameter("max_id", it) }
        addQueryParameter("limit", limit.toString())
    }

    suspend fun publicTimeline(
        serverUrl: String,
        accessToken: String?,
        local: Boolean,
        maxId: String? = null,
        limit: Int = DEFAULT_LIMIT,
    ): List<StatusDto> = getStatuses(serverUrl, accessToken, "api/v1/timelines/public") {
        if (local) addQueryParameter("local", "true")
        maxId?.let { addQueryParameter("max_id", it) }
        addQueryParameter("limit", limit.toString())
    }

    private suspend fun getStatuses(
        serverUrl: String,
        accessToken: String?,
        path: String,
        query: HttpUrl.Builder.() -> Unit,
    ): List<StatusDto> = withContext(Dispatchers.IO) {
        val url = serverUrl.toHttpUrl().newBuilder()
            .addPathSegments(path)
            .apply(query)
            .build()
        val request = Request.Builder()
            .url(url)
            .apply { accessToken?.let { header("Authorization", "Bearer $it") } }
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw MastodonApiException(response.code, "HTTP ${response.code}: $body")
            }
            json.decodeFromString<List<StatusDto>>(body)
        }
    }

    companion object {
        const val DEFAULT_LIMIT = 40
    }
}
