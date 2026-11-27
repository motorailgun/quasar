package es.aria_on_the_planet.quasar.ui.component.composer

import androidx.collection.intListOf
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.user.UserIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/** メンションの候補を引く (`@` を除いた `user` / `user@dom` → アカウント)。失敗は空で返す。 */
typealias MentionSearch = suspend (query: String) -> List<User>

/** 入力中の `@user` に対するアカウントの候補。 */
@Stable
class MentionSuggestions(
    private val field: TextFieldState,
    val query: AutocompleteQuery,
    val users: List<User>,
) {
    /** [user] で `@user` を `@acct ` に置き換え、後ろにカーソルを置く。 */
    fun select(user: User) {
        val (text, cursor) = insertMention(field.text, query.range, user.screenName)
        field.edit {
            replace(0, length, text)
            selection = TextRange(cursor)
        }
    }

    internal fun withQuery(query: AutocompleteQuery) = MentionSuggestions(field, query, users)
}

/**
 * [field] のカーソル直前の `@user` からアカウントの候補を求める ([search] でサーバに問い合わせる)。入力に追従し、
 * 打ち終えて [MentionDebounceMillis] 待ってから引く。新しい入力が来たら前の問い合わせは捨てる。候補が無ければ `null`。
 * 引いている間は前の候補を出したままにする (ちらつかないように)。[search] が `null` なら出さない。
 */
@Composable
fun rememberMentionSuggestions(field: TextFieldState, search: MentionSearch?): State<MentionSuggestions?> {
    val state = remember(field) { mutableStateOf<MentionSuggestions?>(null) }
    val currentSearch by rememberUpdatedState(search)
    LaunchedEffect(field) {
        snapshotFlow { findMentionQuery(field.text, field.selection) }
            .distinctUntilChanged()
            .collectLatest { query ->
                val search = currentSearch
                if (query == null || search == null) {
                    state.value = null
                    return@collectLatest
                }
                // 前の候補は範囲だけ今の `@…` に合わせる (待つ間に選ばれても今の入力を置き換える)
                state.value = state.value?.withQuery(query)
                delay(MentionDebounceMillis)
                val users = search(query.query)
                state.value = users.takeIf { it.isNotEmpty() }?.let { MentionSuggestions(field, query, it) }
            }
    }
    return state
}

/**
 * 候補を浮かぶ Material 3 Expressive のメニュー ([DropdownMenuPopup] + [DropdownMenuGroup]) で出す。アンカーにしたい要素 (クイック投稿バー全体・本文の打っている `@` の位置) を親にして置き、
 * 親の上か下の、空きの大きい方に開く。横はアンカーの始端 (`@` の位置) から始め、画面に収まらなければ収まる範囲で
 * アンカーにいちばん近い位置 (M3 の位置決めは最後の候補を画面の端から 8dp の内側に押し込む)。幅は候補の中身に合わせる
 * (上限は M3 の 280dp) ので、細いメニューほどアンカーの近くに出る。
 *
 * ポップアップはフォーカスを取らない (キーボードを閉じない)。外側のタップで閉じたら、`@…` を打ち直すまで出さない。
 * 高さはその空き (メニューは画面・キーボードの端から 48dp 空ける) に収め、収まらない分はスクロールする。収まらないと
 * M3 の位置決めはメニューを親に重ねてしまい、打っている本文が隠れる (キーボードが出ている小さい画面で起きる)。
 *
 * 項目は `@acct` の 1 行 (表示名は出さない)。[showAvatars] なら左にアカウントのアイコン (設定 `AccountSettings.mentionSuggestionAvatars`)。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MentionSuggestionMenu(suggestions: MentionSuggestions?, showAvatars: Boolean = true) {
    var dismissedQuery by remember { mutableStateOf<AutocompleteQuery?>(null) }
    // 閉じるアニメーションの間も直前の候補を出しておく
    val current = remember { LastNonNull<MentionSuggestions>() }
    val shown = current.update(suggestions)
    // 親 (アンカー) のウィンドウ内の位置
    var anchor by remember { mutableStateOf<Rect?>(null) }
    Spacer(Modifier.onGloballyPositioned { anchor = it.parentLayoutCoordinates?.boundsInWindow() })
    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val topInset = WindowInsets.statusBars.getTop(density)
    val bottomInset = maxOf(WindowInsets.ime.getBottom(density), WindowInsets.navigationBars.getBottom(density))
    val placement = anchor?.let { bounds ->
        val margin = with(density) { (MenuWindowMargin + MenuAnchorGap + MenuGroupPadding).toPx() }
        val above = bounds.top - topInset - margin
        // M3 の位置決めの「ウィンドウ」は見えている範囲 (ステータスバー・キーボードを除く) の大きさだが、アンカーの位置は
        // ウィンドウの上端からなので、下の空きはステータスバーの分も引く
        val below = windowHeight - topInset - bottomInset - bounds.bottom - margin
        val opensBelow = below > above
        opensBelow to with(density) { (if (opensBelow) below else above).toDp() }.coerceIn(MenuMinHeight, MenuMaxHeight)
    }
    val opensBelow = placement?.first ?: false
    val maxHeight = placement?.second ?: MenuMaxHeight
    val gap = with(density) { MenuAnchorGap.roundToPx() }
    val position = remember(opensBelow, gap) {
        MenuAnchorPosition.Custom(
            // 候補を 1 つにして、収まらないときはそれを画面内に押し込ませる (= 寄れるだけアンカーに寄る)
            xCandidates = {
                intListOf(
                    if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left else anchorBounds.right - menuSize.width,
                )
            },
            yCandidates = {
                val below = anchorBounds.bottom + gap
                val above = anchorBounds.top - gap - menuSize.height
                if (opensBelow) intListOf(below, above) else intListOf(above, below)
            },
        )
    }
    DropdownMenuPopup(
        expanded = suggestions != null && suggestions.query != dismissedQuery,
        onDismissRequest = { dismissedQuery = suggestions?.query },
        popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(position),
        properties = PopupProperties(focusable = false),
    ) {
        if (shown == null) return@DropdownMenuPopup
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
            val scrollState = rememberScrollState()
            LaunchedEffect(shown.users) { scrollState.scrollTo(0) }
            Column(Modifier.heightIn(max = maxHeight).verticalScroll(scrollState)) {
                shown.users.forEachIndexed { index, user ->
                    DropdownMenuItem(
                        onClick = { shown.select(user) },
                        text = { Text("@${user.screenName}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        shape = MenuDefaults.itemShape(index, shown.users.size).shape,
                        leadingIcon = if (showAvatars) ({ UserIcon(user, size = MenuAvatarSize) }) else null,
                    )
                }
            }
        }
    }
}

/** 打ち終えてから問い合わせるまでの時間。 */
private const val MentionDebounceMillis = 250L
private val MenuAvatarSize = 24.dp
/** アンカーとメニューの間。 */
private val MenuAnchorGap = 8.dp
/** メニューの高さの上限 (約 5.5 項目。半端な項目でスクロールできることを示す)。 */
private val MenuMaxHeight = 270.dp
/** 空きがこれより少なくても縮めない (メニューが親に重なる)。 */
private val MenuMinHeight = 64.dp
/** M3 の位置決めがメニューと画面の上下の端の間に空ける幅 (`MenuVerticalMargin`)。 */
private val MenuWindowMargin = 48.dp
/** グループの上下の余白 + 影の分の見込み。 */
private val MenuGroupPadding = 24.dp
