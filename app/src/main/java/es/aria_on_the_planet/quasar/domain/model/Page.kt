package es.aria_on_the_planet.quasar.domain.model

/**
 * 一覧の 1 ページと、次 (古い側) のページを取るための `max_id`。
 * フォロー / フォロワー一覧のように `max_id` が項目の ID ではない (Link ヘッダで渡される) 場合に使う。
 */
data class Page<T>(val items: List<T>, val nextMaxId: String?) {
    /** これ以上古いページは無い。 */
    val endReached: Boolean get() = items.isEmpty() || nextMaxId == null
}
