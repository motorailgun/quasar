package es.aria_on_the_planet.quasar.data.repository

import es.aria_on_the_planet.quasar.data.mapper.toComposeLimits
import es.aria_on_the_planet.quasar.data.mapper.toProfileLimits
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.domain.model.ComposeLimits
import es.aria_on_the_planet.quasar.domain.model.ProfileLimits
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** サーバの情報のうちアプリが使うもの。 */
data class InstanceInfo(
    /** ストリーミングのベース URL。未設定なら `null` (サーバ URL を使う)。 */
    val streamingUrl: String?,
    val composeLimits: ComposeLimits,
    val profileLimits: ProfileLimits,
)

/**
 * `GET /api/v2/instance` の結果をサーバごとにメモリへキャッシュする。取得はプロセスごとに 1 回で、
 * 同時に呼ばれても通信は 1 回。失敗はキャッシュせず、次の呼び出しで取り直す。
 */
class InstanceRepository(
    private val api: MastodonApi,
) {
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, InstanceInfo>()

    suspend fun instance(serverUrl: String): InstanceInfo = mutex.withLock {
        cache[serverUrl] ?: api.instance(serverUrl).let { dto ->
            InstanceInfo(
                streamingUrl = dto.configuration?.urls?.streaming,
                composeLimits = dto.toComposeLimits(),
                profileLimits = dto.toProfileLimits(),
            ).also { cache[serverUrl] = it }
        }
    }
}
