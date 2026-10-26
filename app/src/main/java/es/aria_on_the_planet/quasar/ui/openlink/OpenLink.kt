package es.aria_on_the_planet.quasar.ui.openlink

import android.content.ComponentName
import android.content.Intent
import android.util.Log
import java.net.URI
import java.net.URISyntaxException

/**
 * 他のアプリから「Quasar で開く」(manifest の `OpenLink` = MainActivity の別名) に共有された文字列から、開く対象を
 * 取り出す。URL があれば最初の URL、無ければ最初の `@user@domain`。どちらも無ければ `null`。
 * 本文 ([text]) を先に見て、無ければ件名 ([subject]、ブラウザはページのタイトルを入れてくる) を見る。
 */
fun linkQueryOf(subject: String?, text: String?): String? {
    val sources = listOfNotNull(text, subject)
    return sources.firstNotNullOfOrNull(::findUrl) ?: sources.firstNotNullOfOrNull(::findAcct)
}

/** [text] の最初の http(s) の URL。文末の句読点・閉じ括弧は URL に含めない。 */
fun findUrl(text: String): String? =
    UrlPattern.findAll(text)
        .map { it.value.trimEnd { c -> c in TrailingPunctuation } }
        .firstOrNull { hostOf(it) != null }

/** [text] の最初の `user@domain` (先頭の `@` は有っても無くてもよい) を `@user@domain` にして返す。 */
fun findAcct(text: String): String? =
    AcctPattern.find(text)?.let { "@${it.groupValues[1]}@${it.groupValues[2]}" }

/**
 * ハッシュタグのページの URL (`https://host/tags/名前`。Pleroma 系は `/tag/名前`) ならタグの名前 (`#` 無し)。
 * タグは ActivityPub のオブジェクトではなく検索では引けないので、URL から直接開く。どのサーバのページでも
 * 接続中サーバのタグのタイムラインになる。
 */
fun hashtagOfUrl(url: String): String? {
    val path = try {
        URI(url).path
    } catch (_: URISyntaxException) {
        return null
    } ?: return null
    val segments = path.split('/').filter { it.isNotEmpty() }
    if (segments.size != 2 || segments[0] !in HashtagPathPrefixes) return null
    return segments[1].removePrefix("#").takeIf { it.isNotBlank() }
}

private fun hostOf(url: String): String? = try {
    URI(url).host?.takeIf { it.isNotEmpty() }
} catch (_: URISyntaxException) {
    null
}

private val UrlPattern = Regex("""https?://[^\s<>"'「」『』（）]+""", RegexOption.IGNORE_CASE)
private const val TrailingPunctuation = ".,;:!?)]}…。、"

/** 前が英数字・`.`・`/`・`@` (メールアドレスの途中・URL の中) のものは除く。 */
private val AcctPattern = Regex(
    """(?<![\w.@/])@?([A-Za-z0-9_]+(?:[.-][A-Za-z0-9_]+)*)@((?:[A-Za-z0-9-]+\.)+[A-Za-z0-9-]+)""",
)

private val HashtagPathPrefixes = setOf("tags", "tag")

/** MainActivity の別名 (manifest)。共有シートで「Quasar で開く」として出る。 */
private const val OpenLinkAliasSimpleName = "OpenLink"

/** 「Quasar で開く」(MainActivity の別名) に共有された intent か。 */
fun Intent.isOpenLinkShare(): Boolean = action == Intent.ACTION_SEND && isOpenLinkAlias(component)

/** 「Quasar で開く」に共有された intent の開く対象 ([linkQueryOf])。無い・読めなければ `null`。 */
fun Intent.readOpenLinkQuery(): String? = try {
    linkQueryOf(getStringExtra(Intent.EXTRA_SUBJECT), getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString())
} catch (e: RuntimeException) {
    // extra の型が違う・壊れている等。中身はログに出さない
    Log.w(TAG, "Unreadable open-link intent: ${e.javaClass.simpleName}")
    null
}

private fun isOpenLinkAlias(component: ComponentName?): Boolean =
    component?.className?.substringAfterLast('.') == OpenLinkAliasSimpleName

private const val TAG = "OpenLink"
