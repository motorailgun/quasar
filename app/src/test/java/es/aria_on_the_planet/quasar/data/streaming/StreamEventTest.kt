package es.aria_on_the_planet.quasar.data.streaming

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamEventTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val account = """{"id":"1","username":"alice","acct":"alice","url":"https://e/@alice","avatar":"https://e/a.png"}"""
    private fun status(id: String, content: String = "hi") =
        """{"id":"$id","created_at":"2026-10-15T00:00:00.000Z","account":$account,"content":"$content"}"""

    /** payload は JSON を文字列化したもの (二重エンコード) なので、エスケープして埋め込む。 */
    private fun message(stream: String, event: String, payload: String?) = buildString {
        append("""{"stream":["$stream"],"event":"$event"""")
        if (payload != null) append(""","payload":""").append(Json.encodeToString(String.serializer(), payload))
        append("}")
    }

    @Test
    fun parsesUpdateWithChannel() {
        val home = parseStreamMessage(json, message("user", "update", status("10")))
        assertEquals(StreamChannel.User, (home as StreamEvent.Update).channel)
        assertEquals("10", home.post.id)
        val local = parseStreamMessage(json, message("public:local", "update", status("11")))
        assertEquals(StreamChannel.Local, (local as StreamEvent.Update).channel)
    }

    @Test
    fun parsesGlobalUpdateAndIgnoresUnknownStream() {
        assertEquals(StreamChannel.Public, (parseStreamMessage(json, message("public", "update", status("12"))) as StreamEvent.Update).channel)
        assertNull(parseStreamMessage(json, message("public:remote", "update", status("13"))))
    }

    @Test
    fun parsesDeleteAsPlainId() {
        assertEquals(StreamEvent.Delete("99"), parseStreamMessage(json, message("user", "delete", "99")))
    }

    @Test
    fun parsesStatusUpdate() {
        val event = parseStreamMessage(json, message("user", "status.update", status("10", "edited")))
        assertEquals("edited", (event as StreamEvent.StatusUpdate).post.text)
    }

    @Test
    fun parsesNotificationWithGroupKey() {
        val notification = """{"id":"500","type":"favourite","group_key":"favourite-10-123","created_at":"2026-10-15T00:00:00.000Z","account":$account,"status":${status("10")}}"""
        val event = parseStreamMessage(json, message("user", "notification", notification))
        val n = (event as StreamEvent.NotificationReceived).notification
        assertEquals("500", n.id)
        assertEquals("favourite-10-123", n.groupKey)
    }

    @Test
    fun ignoresUnknownAndMalformedMessages() {
        assertNull(parseStreamMessage(json, message("user", "filters_changed", null)))
        assertNull(parseStreamMessage(json, message("user", "announcement", "{}")))
        assertNull(parseStreamMessage(json, "not json"))
        assertNull(parseStreamMessage(json, message("user", "update", "{broken")))
    }

    @Test
    fun reconnectDelayBacksOffExponentially() {
        assertEquals(listOf(0L, 1_000L, 2_000L, 4_000L, 8_000L), (0..4).map(::reconnectDelayMillis))
        assertEquals(60_000L, reconnectDelayMillis(20))
        assertTrue((1..30).map(::reconnectDelayMillis).zipWithNext().all { (a, b) -> a <= b })
    }

    @Test
    fun buildsWebSocketEndpoint() {
        assertEquals("wss://mstdn.example/api/v1/streaming", streamingEndpoint("wss://mstdn.example"))
        assertEquals("wss://mstdn.example/api/v1/streaming", streamingEndpoint("https://mstdn.example/"))
        assertEquals("ws://localhost:4000/api/v1/streaming", streamingEndpoint("http://localhost:4000"))
    }
}
