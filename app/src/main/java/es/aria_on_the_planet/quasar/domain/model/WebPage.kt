package es.aria_on_the_planet.quasar.domain.model

import java.net.URI

/** 接続中サーバ [serverUrl] の Web でのポストのページ。リモートのポストも接続中サーバで見られる。 */
fun Post.localWebUrl(serverUrl: String): String = "${serverUrl.trimEnd('/')}/@${author.screenName}/$statusId"

/** 投稿者のサーバの Web でのポストのページ。ローカルのポスト・URL が無いときは `null` ([localWebUrl] で足りる)。 */
fun Post.remoteWebUrl(): String? = url?.takeIf { author.isRemote && it.isWebUrl() }

/** 接続中サーバ [serverUrl] の Web でのプロフィールのページ。 */
fun User.localWebUrl(serverUrl: String): String = "${serverUrl.trimEnd('/')}/@$screenName"

/** 所属サーバの Web でのプロフィールのページ。ローカルのユーザー・URL が無いときは `null`。 */
fun User.remoteWebUrl(): String? = url?.takeIf { isRemote && it.isWebUrl() }

/** URL のホスト名 (メニューの「〜 で開く」用)。読めなければ `null`。 */
fun webHostOf(url: String): String? = runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotEmpty() }

private fun String.isWebUrl() = startsWith("https://") || startsWith("http://")
