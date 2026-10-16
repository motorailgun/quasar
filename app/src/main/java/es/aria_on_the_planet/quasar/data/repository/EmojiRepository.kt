package es.aria_on_the_planet.quasar.data.repository

import es.aria_on_the_planet.quasar.data.mapper.toDomain
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * サーバのカスタム絵文字 (`GET /api/v1/custom_emojis`)。[InstanceRepository] と同じく、サーバごとにプロセス内で
 * 1 回だけ取得する。失敗はキャッシュしない。
 */
class EmojiRepository(
    private val api: MastodonApi,
) {
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, List<CustomEmoji>>()

    /** ピッカー・補完に出す絵文字 (`visible_in_picker`)。 */
    suspend fun pickerEmojis(serverUrl: String): List<CustomEmoji> = mutex.withLock {
        cache[serverUrl] ?: api.customEmojis(serverUrl)
            .filter { it.visibleInPicker }
            .map { it.toDomain() }
            .also { cache[serverUrl] = it }
    }
}
