package es.aria_on_the_planet.quasar.data.remote

import es.aria_on_the_planet.quasar.data.remote.dto.AccountDto
import es.aria_on_the_planet.quasar.data.remote.dto.ContextDto
import es.aria_on_the_planet.quasar.data.remote.dto.CustomEmojiDto
import es.aria_on_the_planet.quasar.data.remote.dto.FamiliarFollowersDto
import es.aria_on_the_planet.quasar.data.remote.dto.GroupedNotificationsResultsDto
import es.aria_on_the_planet.quasar.data.remote.dto.InstanceDto
import es.aria_on_the_planet.quasar.data.remote.dto.MediaAttachmentDto
import es.aria_on_the_planet.quasar.data.remote.dto.NotificationDto
import es.aria_on_the_planet.quasar.data.remote.dto.PollDto
import es.aria_on_the_planet.quasar.data.remote.dto.RelationshipDto
import es.aria_on_the_planet.quasar.data.remote.dto.StatusDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.MultipartBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class MastodonApiException(val code: Int, message: String) : IOException(message)

/** Link ヘッダでページングする一覧の 1 ページ。[nextMaxId] が `null` なら最後のページ。 */
data class LinkedPage<T>(val items: List<T>, val nextMaxId: String?)

/** Mastodon REST API の薄いクライアント。 */
class MastodonApi(
    private val client: OkHttpClient,
    private val json: Json,
) {
    // 大きな動画の送信や、送信後のサーバ側の処理を待つ間に切れないよう長めにする
    private val uploadClient = client.newBuilder()
        .writeTimeout(2, TimeUnit.MINUTES)
        .readTimeout(2, TimeUnit.MINUTES)
        .build()

    suspend fun homeTimeline(
        serverUrl: String,
        accessToken: String?,
        maxId: String? = null,
        limit: Int = DEFAULT_LIMIT,
        sinceId: String? = null,
    ): List<StatusDto> = getStatuses(serverUrl, accessToken, "api/v1/timelines/home") {
        maxId?.let { addQueryParameter("max_id", it) }
        sinceId?.let { addQueryParameter("since_id", it) }
        addQueryParameter("limit", limit.toString())
    }

    suspend fun publicTimeline(
        serverUrl: String,
        accessToken: String?,
        local: Boolean,
        maxId: String? = null,
        limit: Int = DEFAULT_LIMIT,
        sinceId: String? = null,
    ): List<StatusDto> = getStatuses(serverUrl, accessToken, "api/v1/timelines/public") {
        if (local) addQueryParameter("local", "true")
        maxId?.let { addQueryParameter("max_id", it) }
        sinceId?.let { addQueryParameter("since_id", it) }
        addQueryParameter("limit", limit.toString())
    }

    /** https://docs.joinmastodon.org/methods/timelines/#tag */
    suspend fun hashtagTimeline(
        serverUrl: String,
        accessToken: String?,
        hashtag: String,
        maxId: String? = null,
        limit: Int = DEFAULT_LIMIT,
    ): List<StatusDto> = getStatuses(serverUrl, accessToken, "api/v1/timelines/tag/$hashtag") {
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
        sinceId: String? = null,
    ): List<NotificationDto> = get(serverUrl, accessToken, "api/v1/notifications") {
        maxId?.let { addQueryParameter("max_id", it) }
        sinceId?.let { addQueryParameter("since_id", it) }
        addQueryParameter("limit", limit.toString())
    }

    /** https://docs.joinmastodon.org/methods/grouped_notifications/#get-grouped (要認証、Mastodon 4.3+) */
    suspend fun groupedNotifications(
        serverUrl: String,
        accessToken: String,
        maxId: String? = null,
        limit: Int = DEFAULT_LIMIT,
        sinceId: String? = null,
    ): GroupedNotificationsResultsDto = get(serverUrl, accessToken, "api/v2/notifications") {
        maxId?.let { addQueryParameter("max_id", it) }
        sinceId?.let { addQueryParameter("since_id", it) }
        addQueryParameter("limit", limit.toString())
    }

    /** https://docs.joinmastodon.org/methods/polls/#vote (要認証) */
    suspend fun votePoll(serverUrl: String, accessToken: String, pollId: String, choices: List<Int>): PollDto =
        post(serverUrl, accessToken, "api/v1/polls/$pollId/votes") {
            choices.forEach { add("choices[]", it.toString()) }
        }

    /**
     * ポストへの操作 (要認証)。[action] はパスの末尾で、以下のいずれか。いずれも更新後の Status を返す。
     * * `reblog` / `unreblog` (https://docs.joinmastodon.org/methods/statuses/#boost)。
     *   `reblog` はブースト自体の Status (中身は `reblog`) を、`unreblog` は元の Status を返す。
     * * `favourite` / `unfavourite` (https://docs.joinmastodon.org/methods/statuses/#favourite)
     * * `bookmark` / `unbookmark` (https://docs.joinmastodon.org/methods/statuses/#bookmark)
     *
     * 既にその状態でもエラーにはならない (冪等)。
     */
    suspend fun statusAction(serverUrl: String, accessToken: String, id: String, action: String): StatusDto =
        post(serverUrl, accessToken, "api/v1/statuses/$id/$action") {}

    /**
     * ポストを投稿する (要認証)。https://docs.joinmastodon.org/methods/statuses/#create
     *
     * @param params フォームの項目 (`status`, `visibility`, `poll[options][]` 等。同じ名前を複数回含みうる)。
     * @param idempotencyKey 同じキーの再送は二重に投稿されない (サーバが 1 時間覚えている)。
     */
    suspend fun postStatus(
        serverUrl: String,
        accessToken: String,
        params: List<Pair<String, String>>,
        idempotencyKey: String,
    ): StatusDto = post(serverUrl, accessToken, "api/v1/statuses", headers = mapOf("Idempotency-Key" to idempotencyKey)) {
        params.forEach { (name, value) -> add(name, value) }
    }

    /**
     * メディアをアップロードする (要認証)。https://docs.joinmastodon.org/methods/media/#v2
     * 画像は処理済み (200) で返るが、動画等は処理待ち (202、`url` が `null`) で返り、[media] で完了を待つ。
     *
     * @param file 送る中身。進み具合は呼び出し側の [RequestBody] 実装で数える。
     */
    suspend fun uploadMedia(
        serverUrl: String,
        accessToken: String,
        fileName: String,
        file: RequestBody,
        description: String?,
    ): MediaAttachmentDto {
        val url = serverUrl.toHttpUrl().newBuilder().addPathSegments("api/v2/media").build()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, file)
            .apply { description?.takeIf { it.isNotBlank() }?.let { addFormDataPart("description", it) } }
            .build()
        return execute(Request.Builder().url(url).post(body), accessToken, uploadClient)
    }

    /** アップロードしたメディア (要認証)。処理中は 206 で `url` が `null`。https://docs.joinmastodon.org/methods/media/#get */
    suspend fun media(serverUrl: String, accessToken: String, id: String): MediaAttachmentDto =
        get(serverUrl, accessToken, "api/v1/media/$id") {}

    /** 投稿前のメディアの代替テキストを変える (要認証)。https://docs.joinmastodon.org/methods/media/#update */
    suspend fun updateMediaDescription(serverUrl: String, accessToken: String, id: String, description: String) {
        val url = serverUrl.toHttpUrl().newBuilder().addPathSegments("api/v1/media/$id").build()
        val body = FormBody.Builder().add("description", description).build()
        execute<MediaAttachmentDto>(Request.Builder().url(url).put(body), accessToken)
    }

    /** サーバのカスタム絵文字 (認証不要)。https://docs.joinmastodon.org/methods/custom_emojis/ */
    suspend fun customEmojis(serverUrl: String): List<CustomEmojiDto> =
        get(serverUrl, null, "api/v1/custom_emojis") {}

    /** https://docs.joinmastodon.org/methods/instance/#v2 (認証不要) */
    suspend fun instance(serverUrl: String): InstanceDto =
        get(serverUrl, null, "api/v2/instance") {}

    /** ログイン中のアカウント (要認証)。`source` (既定の公開範囲等) を含む。 */
    suspend fun verifyCredentials(serverUrl: String, accessToken: String): AccountDto =
        get(serverUrl, accessToken, "api/v1/accounts/verify_credentials") {}

    /** https://docs.joinmastodon.org/methods/accounts/#get */
    suspend fun account(serverUrl: String, accessToken: String?, id: String): AccountDto =
        get(serverUrl, accessToken, "api/v1/accounts/$id") {}

    /**
     * https://docs.joinmastodon.org/methods/accounts/#statuses
     *
     * @param pinned `true` ならピン留めされたポストのみ取得する。
     * @param onlyMedia `true` ならメディア付きのポストのみ取得する (ブーストは含まれない)。
     */
    suspend fun accountStatuses(
        serverUrl: String,
        accessToken: String?,
        id: String,
        maxId: String? = null,
        limit: Int = DEFAULT_LIMIT,
        pinned: Boolean = false,
        onlyMedia: Boolean = false,
    ): List<StatusDto> = getStatuses(serverUrl, accessToken, "api/v1/accounts/$id/statuses") {
        maxId?.let { addQueryParameter("max_id", it) }
        addQueryParameter("limit", limit.toString())
        if (pinned) addQueryParameter("pinned", "true")
        if (onlyMedia) addQueryParameter("only_media", "true")
    }

    /**
     * フォローしているアカウント (https://docs.joinmastodon.org/methods/accounts/#following) /
     * フォロワー (https://docs.joinmastodon.org/methods/accounts/#followers)。[relation] は `following` か `followers`。
     *
     * `max_id` はアカウントの ID ではなくフォロー関係の ID なので、次ページの `max_id` は Link ヘッダから取る。
     * 一覧を隠しているアカウント (`hide_collections`) では、本人以外には空が返る。
     */
    suspend fun accountRelations(
        serverUrl: String,
        accessToken: String?,
        id: String,
        relation: String,
        maxId: String? = null,
        limit: Int = DEFAULT_LIMIT,
    ): LinkedPage<AccountDto> {
        val url = serverUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/v1/accounts/$id/$relation")
            .apply { maxId?.let { addQueryParameter("max_id", it) } }
            .addQueryParameter("limit", limit.toString())
            .build()
        val (items, headers) = executeWithHeaders<List<AccountDto>>(Request.Builder().url(url), accessToken)
        return LinkedPage(items, nextMaxIdFromLinkHeader(headers["Link"]))
    }

    /** https://docs.joinmastodon.org/methods/accounts/#relationships (要認証) */
    suspend fun relationships(serverUrl: String, accessToken: String, ids: List<String>): List<RelationshipDto> =
        get(serverUrl, accessToken, "api/v1/accounts/relationships") {
            ids.forEach { addQueryParameter("id[]", it) }
        }

    /**
     * フォローする (https://docs.joinmastodon.org/methods/accounts/#follow) / フォロー (リクエスト) をやめる
     * (https://docs.joinmastodon.org/methods/accounts/#unfollow)。要認証。更新後の関係を返す。
     * 鍵アカウントへのフォローは `requested` になる。
     */
    suspend fun follow(serverUrl: String, accessToken: String, id: String, follow: Boolean): RelationshipDto =
        post(serverUrl, accessToken, "api/v1/accounts/$id/${if (follow) "follow" else "unfollow"}") {}

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
        headers: Map<String, String> = emptyMap(),
        crossinline form: FormBody.Builder.() -> Unit,
    ): T {
        val url = serverUrl.toHttpUrl().newBuilder().addPathSegments(path).build()
        val body: RequestBody = FormBody.Builder().apply(form).build()
        val builder = Request.Builder().url(url).post(body)
        headers.forEach { (name, value) -> builder.header(name, value) }
        return execute(builder, accessToken)
    }

    private suspend inline fun <reified T> execute(
        builder: Request.Builder,
        accessToken: String?,
        httpClient: OkHttpClient = client,
    ): T = executeWithHeaders<T>(builder, accessToken, httpClient).first

    private suspend inline fun <reified T> executeWithHeaders(
        builder: Request.Builder,
        accessToken: String?,
        httpClient: OkHttpClient = client,
    ): Pair<T, Headers> =
        withContext(Dispatchers.IO) {
            val request = builder
                .apply { accessToken?.let { header("Authorization", "Bearer $it") } }
                .build()
            val call = httpClient.newCall(request)
            // コルーチンを止めたら通信も止める (アップロードの取り消し等)
            val cancellation = coroutineContext.job.invokeOnCompletion { if (it != null) call.cancel() }
            try {
                call.execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        throw MastodonApiException(response.code, "HTTP ${response.code}: $body")
                    }
                    json.decodeFromString<T>(body) to response.headers
                }
            } finally {
                cancellation.dispose()
            }
        }

    companion object {
        const val DEFAULT_LIMIT = 40
    }
}
