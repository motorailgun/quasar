package es.aria_on_the_planet.quasar.ui.component.post

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.PostVersion
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.ui.component.list.LoadErrorMessage
import es.aria_on_the_planet.quasar.ui.component.menu.ActionMenu
import es.aria_on_the_planet.quasar.ui.feed.LoadError

/** 編集履歴のシート ([PostHistoryMenu]) の中身。 */
@Immutable
sealed interface PostHistoryMenuState {
    data object Loading : PostHistoryMenuState

    /** [versions] は古い順 (最後が今の版)。 */
    data class Loaded(val versions: List<PostVersion>) : PostHistoryMenuState

    data class Failed(val error: LoadError) : PostHistoryMenuState
}

/**
 * ポスト詳細の「Edited」から開く編集履歴のシート (Mastodon Web のスマホの形)。見出しに「N 回編集」、項目に新しい順で
 * 「(鉛筆のアイコン) 編集 <日時>」、最後に「(post_add のアイコン) 作成 <日時>」([PostVersionLabel])。項目のタップで [onSelect] (版の番号は古い順の 0 始まり)。
 * 入れ物はポストのメニューと同じ下からのシート ([ActionMenu])。[state] が `null` なら閉じる。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PostHistoryMenu(
    state: PostHistoryMenuState?,
    onDismissRequest: () -> Unit,
    onSelect: (index: Int) -> Unit,
) {
    // 閉じるアニメーションの間も中身を出しておく
    var shown by remember { mutableStateOf(state) }
    if (state != null) shown = state
    ActionMenu(
        expanded = state != null,
        onDismissRequest = onDismissRequest,
        header = {
            val versions = (shown as? PostHistoryMenuState.Loaded)?.versions
            Text(
                text = if (versions != null) {
                    pluralStringResource(R.plurals.post_history_edited_times, versions.size - 1, versions.size - 1)
                } else {
                    stringResource(R.string.post_history_title)
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        },
    ) {
        when (val current = shown) {
            null, PostHistoryMenuState.Loading -> Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp),
                contentAlignment = Alignment.Center,
            ) { LoadingIndicator() }
            is PostHistoryMenuState.Failed -> LoadErrorMessage(
                current.error,
                R.string.post_history_error,
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
            is PostHistoryMenuState.Loaded -> {
                // 新しい順に並べる
                for (index in current.versions.indices.reversed()) {
                    val created = index == 0
                    DropdownMenuItem(
                        // アイコンは項目の leadingIcon (文字との間が 12dp で広い) ではなく、文字の側に詰めて置く
                        text = { PostVersionLabel(current.versions[index], created = created) },
                        onClick = { onSelect(index) },
                    )
                }
            }
        }
    }
}

/**
 * 「(アイコン) **編集** <日時>」/「(アイコン) **Edited** <日時>」(最初の版なら作成 / Created)。言語に関わらず左に
 * アイコン (編集 = 鉛筆、作成 = post_add) と種類を太字で置き、種類の欄は「編集」「作成」の広い方の幅に揃えて、日時がどの行も
 * 同じ位置から始まるようにする。アイコンの色は項目の leadingIcon と同じ。
 * 誰の操作かはシートを開いた時点で分かるので名前は出さない。
 */
@Composable
internal fun PostVersionLabel(version: PostVersion, created: Boolean) {
    val context = LocalContext.current
    // ロケールや 12/24 時間設定の変更で再計算されるよう Configuration を読む
    LocalConfiguration.current
    val time = formatDetailedTime(version.createdAt, is24Hour = DateFormat.is24HourFormat(context))
    val edited = stringResource(R.string.post_history_kind_edited)
    val createdLabel = stringResource(R.string.post_history_kind_created)
    val kindStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val kindWidth = remember(edited, createdLabel, kindStyle, density) {
        val px = listOf(edited, createdLabel).maxOf { measurer.measure(it, kindStyle, softWrap = false, maxLines = 1).size.width }
        with(density) { px.toDp() }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (created) Symbols.Outlined.PostAdd else Symbols.Outlined.Edit,
            contentDescription = null,
            tint = MenuDefaults.itemColors().leadingIconColor,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (created) createdLabel else edited,
            style = kindStyle,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.width(kindWidth),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = time, fontWeight = FontWeight.Normal)
    }
}
