package es.aria_on_the_planet.quasar.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Link ヘッダ (`<https://.../followers?max_id=123>; rel="next", <...>; rel="prev"`) から、
 * 次ページ (`rel="next"`) の `max_id` を取り出す。無ければ `null` (最後のページ)。
 *
 * https://docs.joinmastodon.org/api/guidelines/#pagination
 */
fun nextMaxIdFromLinkHeader(header: String?): String? {
    if (header.isNullOrBlank()) return null
    return LinkEntry.findAll(header)
        .firstOrNull { match -> match.groupValues[2].split(' ').any { it == "next" } }
        ?.groupValues?.get(1)
        ?.toHttpUrlOrNull()
        ?.queryParameter("max_id")
}

private val LinkEntry = Regex("""<([^>]*)>\s*;\s*rel="([^"]*)"""")
