package es.aria_on_the_planet.quasar.data.streaming

import es.aria_on_the_planet.quasar.data.mapper.toDomain
import es.aria_on_the_planet.quasar.data.remote.dto.NotificationDto
import es.aria_on_the_planet.quasar.data.remote.dto.StatusDto
import es.aria_on_the_planet.quasar.domain.model.Notification
import es.aria_on_the_planet.quasar.domain.model.Post
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 購読するストリーム。https://docs.joinmastodon.org/methods/streaming/#streams */
enum class StreamChannel(val apiName: String) {
    /** ホームタイムライン + 自分宛ての通知。 */
    User("user"),

    /** ローカルタイムライン。 */
    Local("public:local"),

    /** グローバル (連合) タイムライン。流量が多いので通常は購読しない (`TimelineViewModel.StreamGlobalTimeline`)。 */
    Public("public"),
    ;

    companion object {
        fun fromApiName(name: String): StreamChannel? = entries.firstOrNull { it.apiName == name }
    }
}

/** ストリーミングで届くイベント (アプリで使うもののみ)。 */
sealed interface StreamEvent {
    /** 接続 (再接続を含む) が確立した。切断中の抜けを補完するきっかけに使う。 */
    data object Connected : StreamEvent

    /** 新しいポスト。 */
    data class Update(val channel: StreamChannel, val post: Post) : StreamEvent

    /** ポストの削除。ID は Status の ID (ブーストの削除ならブースト自体の ID)。 */
    data class Delete(val statusId: String) : StreamEvent

    /** ポストの編集。 */
    data class StatusUpdate(val post: Post) : StreamEvent

    /** 新しい通知。 */
    data class NotificationReceived(val notification: Notification) : StreamEvent
}

/** WebSocket で届く 1 メッセージ。`payload` は JSON を文字列化したもの (delete では ID そのもの)。 */
@Serializable
internal data class StreamMessageDto(
    val stream: List<String> = emptyList(),
    val event: String,
    val payload: String? = null,
)

/**
 * WebSocket のテキストメッセージを [StreamEvent] にする。未対応・解釈できないメッセージは `null`。
 * https://docs.joinmastodon.org/methods/streaming/#events
 */
internal fun parseStreamMessage(json: Json, text: String): StreamEvent? {
    val message = runCatching { json.decodeFromString<StreamMessageDto>(text) }.getOrNull() ?: return null
    val payload = message.payload ?: return null
    return runCatching {
        when (message.event) {
            "update" -> {
                val channel = message.stream.firstNotNullOfOrNull(StreamChannel::fromApiName) ?: return null
                StreamEvent.Update(channel, json.decodeFromString<StatusDto>(payload).toDomain())
            }
            "delete" -> StreamEvent.Delete(payload)
            "status.update" -> StreamEvent.StatusUpdate(json.decodeFromString<StatusDto>(payload).toDomain())
            "notification" -> StreamEvent.NotificationReceived(json.decodeFromString<NotificationDto>(payload).toDomain())
            else -> null
        }
    }.getOrNull()
}

/** 再接続までの待ち時間。連続失敗回数に応じて 1 秒から倍々にし、60 秒で頭打ち。 */
internal fun reconnectDelayMillis(consecutiveFailures: Int): Long {
    if (consecutiveFailures <= 0) return 0
    val exponent = (consecutiveFailures - 1).coerceAtMost(6)
    return (1_000L shl exponent).coerceAtMost(60_000L)
}

/** ストリーミングの WebSocket エンドポイント。[base] は `configuration.urls.streaming` かサーバの URL。 */
internal fun streamingEndpoint(base: String): String {
    val withScheme = when {
        base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
        base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
        else -> base
    }
    return withScheme.trimEnd('/') + "/api/v1/streaming"
}
