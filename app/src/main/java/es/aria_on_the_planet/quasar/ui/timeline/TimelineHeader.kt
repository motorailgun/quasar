package es.aria_on_the_planet.quasar.ui.timeline

import es.aria_on_the_planet.quasar.ui.component.blur.surfaceBlurBorder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import es.aria_on_the_planet.quasar.ui.component.blur.SurfaceBlurStyle
import es.aria_on_the_planet.quasar.ui.component.blur.surfaceBlur
import es.aria_on_the_planet.quasar.ui.component.blur.rememberSurfaceBlurStyle
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.user.UserIcon

/**
 * メイン画面の上端に浮かぶヘッダ (Google マップの検索欄・チップ風)。背景は無く、一覧はステータスバーの裏まで描かれる。
 *
 * ```
 * (icon)  [⌂ Home ][🔔][👥][🌐]
 * ```
 *
 * * 左: ログイン中のアカウントのアイコン。タップでメニュー (名前・@acct = タップで自分のプロフィール・設定)。未取得は汎用のアイコン。
 * * 右: カラムの切り替え (Expressive の連結 `ToggleButton`)。全体の後ろに後ろの一覧をぼかしたカプセルを 1 枚敷き
 *   ([hazeState])、未選択のボタンは透明、選択中は primary で塗る。選択中のみラベルを出し、幅はページ位置 (小数) から
 *   決めてスワイプに追従する。選択中のボタンを押したときも [onSelect] を呼ぶ (画面側で一覧を先頭へスクロールする)。
 *   [visibleColumns] が 2 (タブレット表示) なら、見えている隣り合った 2 つを 1 つのまとまりとして塗る
 *   (`[[Home | Notifications] | Local | Global]`)。
 *
 * ステータスバーの部分には読みやすさのための薄いグラデーションを敷く ([StatusBarScrim])。
 */
@Composable
fun TimelineHeader(
    account: User?,
    pagerState: PagerState,
    visibleColumns: Int,
    hazeState: HazeState,
    onSelect: (ColumnType) -> Unit,
    onOpenProfile: (User) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val blurStyle = rememberSurfaceBlurStyle()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountButton(
            account = account,
            hazeState = hazeState,
            blurStyle = blurStyle,
            onOpenProfile = onOpenProfile,
            onOpenSettings = onOpenSettings,
        )
        Spacer(Modifier.width(10.dp))
        ColumnTabs(
            pagerState = pagerState,
            visibleColumns = visibleColumns,
            hazeState = hazeState,
            blurStyle = blurStyle,
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
        )
    }
}

/** ステータスバーの裏に敷く薄いグラデーション (一覧が裏に入ってもアイコンが読めるように)。 */
@Composable
fun StatusBarScrim(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.surface
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0f)))),
    )
}

private fun Modifier.blurredSurface(hazeState: HazeState, style: SurfaceBlurStyle, shape: Shape): Modifier = this
    .clip(shape)
    .surfaceBlur(hazeState, style)

@Composable
private fun AccountButton(
    account: User?,
    hazeState: HazeState,
    blurStyle: SurfaceBlurStyle,
    onOpenProfile: (User) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        val description = account?.nameOrAcct
        if (account != null) {
            UserIcon(
                user = account,
                size = AccountIconSize,
                // ヘッダのアイコンは設定のアイコンの形に関わらず円
                shape = CircleShape,
                modifier = Modifier
                    // 影は凸形 (円) で付ける。凹みのある形の影は RenderThread での計算が非常に重い (AGENTS.md)
                    .shadow(HeaderShadowElevation, CircleShape)
                    .clickable(role = Role.Button) { menuOpen = true },
            )
        } else {
            Box(
                Modifier
                    .size(AccountIconSize)
                    .shadow(HeaderShadowElevation, CircleShape)
                    .blurredSurface(hazeState, blurStyle, CircleShape)
                    .clickable(role = Role.Button) { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Symbols.Outlined.Person, contentDescription = stringResource(R.string.account_menu))
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (account != null) {
                // 名前の部分のタップで自分のプロフィール
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) {
                            menuOpen = false
                            onOpenProfile(account)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        description.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "@${account.fullScreenName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings)) },
                leadingIcon = { Icon(Symbols.Outlined.Settings, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onOpenSettings()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColumnTabs(
    pagerState: PagerState,
    visibleColumns: Int,
    hazeState: HazeState,
    blurStyle: SurfaceBlurStyle,
    onSelect: (ColumnType) -> Unit,
    modifier: Modifier = Modifier,
) {
    // スワイプ中も指に合わせて幅が移るよう、ページの位置 (小数) から各ボタンの重みを決める
    val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
    // 見えているカラムの左端。ページ幅が表示幅より狭い (2 カラム) と、末尾のページは左端に来ない
    val start = pagerState.targetPage.coerceAtMost(ColumnType.entries.size - visibleColumns)
    val selected = start until start + visibleColumns
    val colors = MaterialTheme.colorScheme
    // 連結ボタンは押下・選択で形が変わるので、ぼかしはボタンごとではなく全体の後ろに 1 枚 (形の変わらないカプセル) 敷く。
    // 未選択のボタンは透明にして、この面を見せる
    Row(
        modifier = modifier
            .height(TabHeight)
            // 一覧の上に浮いて見えるよう軽い影を付ける (視認性のため)
            .shadow(HeaderShadowElevation, CircleShape)
            .blurredSurface(hazeState, blurStyle, CircleShape)
            .border(surfaceBlurBorder(), CircleShape)
            // ボタンはカプセルの内側に少し寄せる。カプセルと同じ高さだと、枠 (中身の上に描かれる) と切り抜きが選択中の
            // ボタンの上下の縁に掛かり、切れて見える
            .padding(TabInset),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        ColumnType.entries.forEachIndexed { index, type ->
            val checked = index in selected
            // 4 カラムでは等幅にラベルが収まらないので、選択中のみラベルを出して幅を広げる
            val closeness = columnCloseness(index, position, visibleColumns)
            val weight = 1f + (SelectedWeight - 1f) * closeness
            val label = stringResource(type.label())
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(type) },
                modifier = Modifier
                    .weight(weight)
                    .height(TabHeight - TabInset * 2)
                    .semantics { role = Role.RadioButton },
                shapes = columnTabShapes(index, selected),
                colors = ToggleButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = colors.onSurface,
                    checkedContainerColor = colors.primary,
                    checkedContentColor = colors.onPrimary,
                ),
            ) {
                Icon(type.icon(), contentDescription = if (checked) null else label, modifier = Modifier.size(18.dp))
                if (checked) {
                    Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * タブの形。見えているカラムが 2 つ以上なら、選択中のまとまりの外側だけを丸め、まとまりの中で接する角は締める
 * (隣り合う 2 つが 1 つのボタンに見えるように)。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun columnTabShapes(index: Int, selected: IntRange): ToggleButtonShapes {
    val base = when (index) {
        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
        ColumnType.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
    }
    if (selected.first == selected.last || index !in selected) return base
    val start = if (index == selected.first) CircleCorner else GroupInnerCorner
    val end = if (index == selected.last) CircleCorner else GroupInnerCorner
    return ToggleButtonShapes(
        shape = base.shape,
        pressedShape = base.pressedShape,
        checkedShape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end),
    )
}

private val CircleCorner = CornerSize(50)
private val GroupInnerCorner = CornerSize(6.dp)

private const val SelectedWeight = 1.8f
private val AccountIconSize: Dp = 44.dp
private val TabHeight: Dp = 44.dp
private val TabInset: Dp = 4.dp
private val HeaderShadowElevation: Dp = 12.dp
