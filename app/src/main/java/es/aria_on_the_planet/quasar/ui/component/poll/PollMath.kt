package es.aria_on_the_planet.quasar.ui.component.poll

import es.aria_on_the_planet.quasar.domain.model.Poll
import java.time.Duration
import java.time.Instant
import java.util.Locale

/**
 * 各選択肢の割合 (0〜1)。結果非公開の選択肢は `null`。
 * 複数選択では票数ではなく投票人数を分母にする (Mastodon Web と同じ)。
 */
internal fun Poll.fractions(): List<Float?> {
    val denominator = (if (multiple) votersCount else null) ?: votesCount
    return options.map { option ->
        val votes = option.votesCount ?: return@map null
        if (denominator <= 0) 0f else votes.toFloat() / denominator
    }
}

/** 最多得票の選択肢のインデックス (同率は全て)。 */
internal fun Poll.leadingOptions(): Set<Int> {
    val max = options.maxOfOrNull { it.votesCount ?: 0 } ?: return emptySet()
    if (max <= 0) return emptySet()
    return options.indices.filterTo(HashSet()) { options[it].votesCount == max }
}

/** 割合の表示。10% 未満は小数 1 桁 (例: 1.8%)、それ以上は整数 (例: 45%)。 */
internal fun formatPercent(fraction: Float, locale: Locale = Locale.getDefault()): String {
    val percent = fraction * 100
    return if (percent > 0f && percent < 10f) {
        String.format(locale, "%.1f%%", percent)
    } else {
        String.format(locale, "%.0f%%", percent)
    }
}

/** 終了までの残り時間。 */
sealed interface TimeLeft {
    data class Days(val value: Int) : TimeLeft
    data class Hours(val value: Int) : TimeLeft
    data class Minutes(val value: Int) : TimeLeft
    data object Ended : TimeLeft
}

internal fun timeLeft(expiresAt: Instant, now: Instant = Instant.now()): TimeLeft {
    val left = Duration.between(now, expiresAt)
    return when {
        left.isNegative || left.isZero -> TimeLeft.Ended
        left.toDays() >= 1 -> TimeLeft.Days(left.toDays().toInt())
        left.toHours() >= 1 -> TimeLeft.Hours(left.toHours().toInt())
        else -> TimeLeft.Minutes(left.toMinutes().toInt().coerceAtLeast(1))
    }
}
