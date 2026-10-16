package es.aria_on_the_planet.quasar.data.streaming

import android.util.Log
import es.aria_on_the_planet.quasar.data.repository.InstanceRepository
import es.aria_on_the_planet.quasar.data.session.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Mastodon の Streaming API (WebSocket) のクライアント。1 本の接続で複数のストリームを購読する。
 * https://docs.joinmastodon.org/methods/streaming/
 */
class StreamingClient(
    okHttpClient: OkHttpClient,
    private val json: Json,
    private val instanceRepository: InstanceRepository,
) {
    // 長時間の接続なので読み取りタイムアウトは無効にし、ping で生存確認する
    private val client = okHttpClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    /**
     * [channels] を購読し、イベントを流す。切断されたら待ってから再接続し続ける (collect をやめるまで)。
     * 接続 (再接続を含む) のたびに [StreamEvent.Connected] を流す。要ログイン (4.2 以降は全ストリームで必須)。
     */
    fun events(session: Session, channels: Set<StreamChannel>): Flow<StreamEvent> = flow {
        val token = session.accessToken ?: return@flow
        if (channels.isEmpty()) return@flow
        val base = try {
            instanceRepository.instance(session.serverUrl).streamingUrl
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve streaming URL; falling back to server URL", e)
            null
        } ?: session.serverUrl
        val endpoint = streamingEndpoint(base)
        Log.d(TAG, "Streaming endpoint: $endpoint, channels: ${channels.map { it.apiName }}")
        var failures = 0
        while (true) {
            var connected = false
            connectOnce(endpoint, token, channels).collect { event ->
                if (event is StreamEvent.Connected) {
                    connected = true
                    failures = 0
                }
                emit(event)
            }
            if (!connected) failures++
            delay(reconnectDelayMillis(failures.coerceAtLeast(1)))
        }
    }

    /** 1 回分の接続。切断・失敗で完了する。 */
    private fun connectOnce(endpoint: String, token: String, channels: Set<StreamChannel>): Flow<StreamEvent> =
        callbackFlow {
            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $token")
                .build()
            val webSocket = client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d(TAG, "Connected (${response.code})")
                        channels.forEach { webSocket.send("""{"type":"subscribe","stream":"${it.apiName}"}""") }
                        trySend(StreamEvent.Connected)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val event = parseStreamMessage(json, text)
                        Log.d(TAG, "Received ${event?.javaClass?.simpleName ?: "unhandled"} (${text.length} chars)")
                        event?.let { trySend(it) }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(NORMAL_CLOSURE, null)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "Closed ($code)")
                        channel.close()
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.w(TAG, "Streaming connection failed (${response?.code})", t)
                        channel.close()
                    }
                },
            )
            awaitClose { webSocket.close(NORMAL_CLOSURE, null) }
        }.buffer(Channel.UNLIMITED) // 取りこぼし補完などで受け手が待つ間も、届いたイベントを捨てない

    private companion object {
        const val TAG = "StreamingClient"
        const val NORMAL_CLOSURE = 1000
    }
}
