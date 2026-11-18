package es.aria_on_the_planet.quasar.domain.model.settings

/**
 * ポストのメニュー (︙) の項目。設定 ([AppSettings.hiddenPostMenuItems]) で 1 つずつ出さないようにできる。
 * 並びはメニューの並び (自分のポストの項目 → 共通 → 他人のポストの項目)。
 */
enum class PostMenuItem {
    /** 自分のポストの編集。 */
    Edit,

    /** 自分のポストの削除。 */
    Delete,

    /** 接続中サーバのページをブラウザで開く。 */
    OpenInBrowser,

    /** リモートのポストを元のサーバのページで開く。 */
    OpenOnServer,

    /** 本文をコピー。 */
    CopyText,

    /** 投稿者のミュート (他人のポスト)。 */
    Mute,

    /** 投稿者のブロック (他人のポスト)。 */
    Block,

    /** 投稿者のブーストの非表示 (他人のポスト。フォロー中のみ押せる)。 */
    HideBoosts,
    ;

    companion object {
        /** 自分のポストにだけ出す項目。 */
        val OwnPostItems: Set<PostMenuItem> = setOf(Edit, Delete)

        /** 他人のポストにだけ出す項目。 */
        val OtherPostItems: Set<PostMenuItem> = setOf(Mute, Block, HideBoosts)
    }
}

/**
 * ポストのメニューに出る項目 ([hidden] を除いたもの)。グレーアウトして出すもの (ブーストの非表示等) も含む。
 *
 * @param mine 自分のポストか。まだ分からなければ `null` (どちらにもなりうるので両方の項目を含める)。
 * @param canEditOwn 自分のポストの編集・削除の口があるか (ギャラリー等には無い)。
 * @param hasRemoteUrl 元のサーバのページがあるか (リモートのポスト)。
 * @param hasText 本文があるか (メディアだけのポストには「本文をコピー」を出さない)。
 */
fun visiblePostMenuItems(
    hidden: Set<PostMenuItem>,
    mine: Boolean?,
    canEditOwn: Boolean,
    hasRemoteUrl: Boolean,
    hasText: Boolean,
): Set<PostMenuItem> = PostMenuItem.entries.filterTo(LinkedHashSet()) { item ->
    item !in hidden && when (item) {
        PostMenuItem.Edit, PostMenuItem.Delete -> mine != false && canEditOwn
        PostMenuItem.OpenInBrowser -> true
        PostMenuItem.OpenOnServer -> hasRemoteUrl
        PostMenuItem.CopyText -> hasText
        PostMenuItem.Mute, PostMenuItem.Block, PostMenuItem.HideBoosts -> mine != true
    }
}
