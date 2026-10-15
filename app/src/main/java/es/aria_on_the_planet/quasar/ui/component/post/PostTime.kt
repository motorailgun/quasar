package es.aria_on_the_planet.quasar.ui.component.post

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 詳細表示用の投稿日時 (例: `2026 08/10 22:30:10`)。
 * [is24Hour] が `false` なら 12 時間表記 (例: `2026 08/10 10:30:10 PM`)。
 */
fun formatDetailedTime(
    instant: Instant,
    is24Hour: Boolean,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val pattern = if (is24Hour) "yyyy MM/dd HH:mm:ss" else "yyyy MM/dd h:mm:ss a"
    return DateTimeFormatter.ofPattern(pattern, locale).withZone(zone).format(instant)
}
