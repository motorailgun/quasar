package es.aria_on_the_planet.quasar.data.remote

import es.aria_on_the_planet.quasar.data.remote.dto.AccountDto
import es.aria_on_the_planet.quasar.data.remote.dto.ContextDto
import es.aria_on_the_planet.quasar.data.remote.dto.FamiliarFollowersDto
import es.aria_on_the_planet.quasar.data.remote.dto.NotificationDto
import es.aria_on_the_planet.quasar.data.remote.dto.PollDto
import es.aria_on_the_planet.quasar.data.remote.dto.StatusDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody
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

    /** https://docs.joinmastodon.org/methods/statuses/#get */
    suspend fun status(serverUrl: String, accessToken: String?, id: String): StatusDto =
        get(serverUrl, accessToken, "api/v1/statuses/$id") {}

    /**
     * https://docs.joinmastodon.org/methods/statuses/#context
     *
     * 未認証では祖先 40 件・子孫 60 件・深さ 20 までに制限される。
     */
    suspend fun statusContext(serverUrl: String, accessToken: String?, id: String): ContextDto =
        get(serverUrl, accessToken, "api/v1/statuses/$id/context") {}

    /** https://docs.joinmastodon.org/methods/notifications/#get (要認証) */
    suspend fun notifications(
        serverUrl: String,
        accessToken: String,
        maxId: String? = null,
        limit: Int = DEFAULT_LIMIT,
    ): List<NotificationDto> = get(serverUrl, accessToken, "api/v1/notifications") {
        maxId?.let { addQueryParameter("max_id", it) }
        addQueryParameter("limit", limit.toString())
    }

    /** https://docs.joinmastodon.org/methods/polls/#vote (要認証) */
    suspend fun votePoll(serverUrl: String, accessToken: String, pollId: String, choices: List<Int>): PollDto =
        post(serverUrl, accessToken, "api/v1/polls/$pollId/votes") {
            choices.forEach { add("choices[]", it.toString()) }
        }

    /** https://docs.joinmastodon.org/methods/accounts/#get */
    suspend fun account(serverUrl: String, accessToken: String?, id: String): AccountDto =
        get(serverUrl, accessToken, "api/v1/accounts/$id") {}

    /**
     * https://docs.joinmastodon.org/methods/accounts/#statuses
     *
     * @param pinned `true` ならピン留めされたポストのみ取得する。
     */
    suspend fun accountStatuses(
        serverUrl: String,
        accessToken: String?,
        id: String,
        maxId: String? = null,
        limit: Int = DEFAULT_LIMIT,
        pinned: Boolean = false,
    ): List<StatusDto> = getStatuses(serverUrl, accessToken, "api/v1/accounts/$id/statuses") {
        maxId?.let { addQueryParameter("max_id", it) }
        addQueryParameter("limit", limit.toString())
        if (pinned) addQueryParameter("pinned", "true")
    }

    /** https://docs.joinmastodon.org/methods/accounts/#familiar_followers (要認証) */
    suspend fun familiarFollowers(
        serverUrl: String,
        accessToken: String,
        ids: List<String>,
    ): List<FamiliarFollowersDto> = get(serverUrl, accessToken, "api/v1/accounts/familiar_followers") {
        ids.forEach { addQueryParameter("id[]", it) }
    }

    private suspend fun getStatuses(
        serverUrl: String,
        accessToken: String?,
        path: String,
        query: HttpUrl.Builder.() -> Unit,
    ): List<StatusDto> = get(serverUrl, accessToken, path, query)

    private suspend inline fun <reified T> get(
        serverUrl: String,
        accessToken: String?,
        path: String,
        crossinline query: HttpUrl.Builder.() -> Unit,
    ): T {
        val url = serverUrl.toHttpUrl().newBuilder()
            .addPathSegments(path)
            .apply(query)
            .build()
        return execute(Request.Builder().url(url), accessToken)
    }

    private suspend inline fun <reified T> post(
        serverUrl: String,
        accessToken: String?,
        path: String,
        crossinline form: FormBody.Builder.() -> Unit,
    ): T {
        val url = serverUrl.toHttpUrl().newBuilder().addPathSegments(path).build()
        val body: RequestBody = FormBody.Builder().apply(form).build()
        return execute(Request.Builder().url(url).post(body), accessToken)
    }

    private suspend inline fun <reified T> execute(builder: Request.Builder, accessToken: String?): T =
        withContext(Dispatchers.IO) {
            val request = builder
                .apply { accessToken?.let { header("Authorization", "Bearer $it") } }
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    throw MastodonApiException(response.code, "HTTP ${response.code}: $body")
                }
                json.decodeFromString<T>(body)
            }
        }

    companion object {
        const val DEFAULT_LIMIT = 40
    }
}
