package es.aria_on_the_planet.quasar.ui.component.post

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import es.aria_on_the_planet.quasar.ui.component.time.LocalNow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 一覧に出す投稿・通知の時刻 ([relativeTime])。現在時刻は [LocalNow]、言語はアプリの言語。 */
@Composable
@ReadOnlyComposable
fun postTimeText(instant: Instant): String =
    relativeTime(instant, LocalNow.current, locale = LocalConfiguration.current.locales[0])

/**
 * 一覧用の時刻。1 週間未満は経過時間 (`3m` / `5h` / `6d`)、それ以上は日付で、今年なら `Aug 18` / `8/18`、
 * 去年以前は `Apr 10, 2023` / `2023/04/10` (英語 / 日本語)。年は [zone] での暦の年で比べる。
 */
fun relativeTime(
    instant: Instant,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val d = Duration.between(instant, now)
    if (d.toDays() < AbsoluteAfterDays) {
        return when {
            d.toMinutes() < 1 -> "${d.seconds.coerceAtLeast(0)}s"
            d.toHours() < 1 -> "${d.toMinutes()}m"
            d.toDays() < 1 -> "${d.toHours()}h"
            else -> "${d.toDays()}d"
        }
    }
    val date = instant.atZone(zone)
    val sameYear = date.year == now.atZone(zone).year
    val japanese = locale.language == Locale.JAPANESE.language
    val pattern = when {
        japanese -> if (sameYear) "M/d" else "yyyy/MM/dd"
        else -> if (sameYear) "MMM d" else "MMM d, yyyy"
    }
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}

/** 経過時間ではなく日付で出し始める日数。 */
private const val AbsoluteAfterDays = 7L

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
