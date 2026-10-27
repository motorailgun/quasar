package es.aria_on_the_planet.quasar.ui.component.poll

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedToggleButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.Poll
import es.aria_on_the_planet.quasar.ui.component.text.EmojiText
import kotlinx.coroutines.launch
import es.aria_on_the_planet.quasar.ui.component.time.LocalNow

private val OptionShape = RoundedCornerShape(8.dp)
private val OptionMinHeight = 40.dp

/**
 * 投票。終了済み・投票済み・投票不可 ([onVote] が `null`) の場合は結果を、それ以外は選択肢を表示する。
 *
 * @param onVote 選んだ選択肢のインデックスで投票する。成功したら `true`。
 *   単一選択は選択肢のタップで即投票、複数選択は選んでから「Vote」で投票する。
 */
@Composable
fun PollView(
    poll: Poll,
    modifier: Modifier = Modifier,
    onVote: (suspend (List<Int>) -> Boolean)? = null,
) {
    val showResults = poll.expired || poll.voted || onVote == null
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showResults) {
            PollResults(poll)
        } else {
            PollChoices(poll, onVote)
        }
        PollFooter(poll)
    }
}

@Composable
private fun PollResults(poll: Poll) {
    val locale = LocalConfiguration.current.locales[0]
    val fractions = poll.fractions()
    val leading = poll.leadingOptions()
    poll.options.forEachIndexed { index, option ->
        val fraction = fractions[index]
        val isLeading = index in leading
        val own = index in poll.ownVotes
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = OptionMinHeight),
                contentAlignment = Alignment.CenterStart,
            ) {
                // 得票率のバー (0% でも存在が分かるよう最小幅を持たせる)
                Box(
                    Modifier
                        .matchParentSize()
                        .padding(end = 0.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((fraction ?: 0f).coerceIn(0.015f, 1f))
                            .clip(OptionShape)
                            .background(
                                if (isLeading) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                    )
                }
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EmojiText(
                        text = option.title,
                        emojis = poll.emojis,
                        postContent = true,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isLeading) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = if (isLeading) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (own) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Symbols.Filled.CheckCircle,
                            contentDescription = stringResource(R.string.poll_your_choice),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            if (fraction != null) {
                Text(
                    text = formatPercent(fraction, locale),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isLeading) FontWeight.Bold else FontWeight.Normal,
                    ),
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .width(52.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PollChoices(poll: Poll, onVote: suspend (List<Int>) -> Boolean) {
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable(poll.id) { mutableStateOf(emptyList<Int>()) }
    var submitting by remember(poll.id) { mutableStateOf(false) }
    var failed by remember(poll.id) { mutableStateOf(false) }

    fun submit(choices: List<Int>) {
        submitting = true
        failed = false
        scope.launch {
            failed = !onVote(choices)
            submitting = false
        }
    }

    poll.options.forEachIndexed { index, option ->
        val label: @Composable () -> Unit = {
            EmojiText(
                text = option.title,
                emojis = poll.emojis,
                postContent = true,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary.takeIf { !(poll.multiple && index in selected) }
                    ?: MaterialTheme.colorScheme.onPrimary,
            )
        }
        if (poll.multiple) {
            OutlinedToggleButton(
                checked = index in selected,
                onCheckedChange = { checked -> selected = if (checked) selected + index else selected - index },
                enabled = !submitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = OptionMinHeight),
            ) { label() }
        } else {
            OutlinedButton(
                onClick = { submit(listOf(index)) },
                enabled = !submitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = OptionMinHeight),
            ) { label() }
        }
    }
    if (poll.multiple) {
        Button(
            onClick = { submit(selected.sorted()) },
            enabled = selected.isNotEmpty() && !submitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.poll_vote))
        }
    }
    if (failed) {
        Text(
            text = stringResource(R.string.poll_vote_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun PollFooter(poll: Poll) {
    val count = if (poll.multiple && poll.votersCount != null) {
        pluralStringResource(R.plurals.poll_voters, poll.votersCount, poll.votersCount)
    } else {
        pluralStringResource(R.plurals.poll_votes, poll.votesCount, poll.votesCount)
    }
    val state = when {
        poll.expired -> stringResource(R.string.poll_final_results)
        poll.expiresAt != null -> when (val left = timeLeft(poll.expiresAt, LocalNow.current)) {
            is TimeLeft.Days -> pluralStringResource(R.plurals.poll_days_left, left.value, left.value)
            is TimeLeft.Hours -> pluralStringResource(R.plurals.poll_hours_left, left.value, left.value)
            is TimeLeft.Minutes -> pluralStringResource(R.plurals.poll_minutes_left, left.value, left.value)
            TimeLeft.Ended -> stringResource(R.string.poll_final_results)
        }
        else -> null
    }
    Text(
        text = listOfNotNull(count, state).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}
