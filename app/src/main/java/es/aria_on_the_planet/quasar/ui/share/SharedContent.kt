package es.aria_on_the_planet.quasar.ui.share

import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * 他のアプリから共有された中身 (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`)。
 *
 * @param text 本文に入れる文字列 (無ければ空)。
 * @param media 添付する `content://` の URI (文字列)。共有された順。
 * @param rejectedMedia 安全でないので捨てた URI の数 (添付できなかったと知らせる)。
 */
data class SharedContent(
    val text: String = "",
    val media: List<String> = emptyList(),
    val rejectedMedia: Int = 0,
)

/**
 * 共有の intent の中身を組み立てる (純粋関数。intent から値を取り出すのは [readSharedContent])。
 *
 * * 本文は [subject] と [text] から ([sharedText])。
 * * `text/` の共有では [streams] を使わない (共有シートのサムネイル用に ClipData に画像が入っていることがある。
 *   .txt のファイルの共有もこれになるが、添付できないので捨てる)。
 * * URI は [isSafeSharedUri] のものだけ。
 *
 * @param isOwnProvider authority がこのアプリの ContentProvider か。
 */
fun sharedContentOf(
    type: String?,
    subject: String?,
    text: String?,
    streams: List<String>,
    isOwnProvider: (authority: String) -> Boolean,
): SharedContent {
    val candidates = if (type?.startsWith("text/") == true) emptyList() else streams.distinct()
    val (safe, unsafe) = candidates.partition { isSafeSharedUri(it, isOwnProvider) }
    return SharedContent(text = sharedText(subject, text), media = safe, rejectedMedia = unsafe.size)
}

/**
 * 本文。ブラウザ等は [subject] にページのタイトル、[text] に URL を入れてくるので「タイトル + 改行 + URL」にする。
 * [text] にタイトルが含まれていれば [text] だけ。
 */
fun sharedText(subject: String?, text: String?): String {
    val title = subject?.trim().orEmpty()
    val body = text?.trim().orEmpty()
    return when {
        title.isEmpty() -> body
        body.isEmpty() -> title
        title in body -> body
        else -> "$title\n$body"
    }
}

/**
 * 共有された URI を読んでよいか。`content://` で、このアプリ自身の provider ではないものだけ。
 *
 * `file://` (このアプリの内部のファイルを指せる) や自分の provider の URI を受けると、共有元のアプリが読めない
 * このアプリのデータ (トークン等) を、こちらの権限で読んでサーバに送ってしまう。authority の切り出しは
 * `Uri.parse` と同じ (`//` の後ろから `/`・`?`・`#` まで)。
 */
fun isSafeSharedUri(uri: String, isOwnProvider: (authority: String) -> Boolean): Boolean {
    val scheme = uri.substringBefore(':', missingDelimiterValue = "")
    if (!scheme.equals("content", ignoreCase = true)) return false
    val rest = uri.substring(scheme.length + 1)
    if (!rest.startsWith("//")) return false
    val authority = rest.substring(2).takeWhile { it != '/' && it != '?' && it != '#' }
    return authority.isNotEmpty() && !isOwnProvider(authority)
}

/**
 * 共有の intent から中身を取り出す。共有でなければ `null`。
 * extra は他のアプリが入れたもので、型が違う・壊れていることがあるので、読めなければ空として扱う (落とさない)。
 */
fun Intent.readSharedContent(isOwnProvider: (authority: String) -> Boolean): SharedContent? {
    if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null
    return try {
        val extraStreams = if (action == Intent.ACTION_SEND_MULTIPLE) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            listOfNotNull(getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        }
        // EXTRA_STREAM は OS が ClipData にも写すので、ClipData は EXTRA_STREAM が無いときだけ見る
        val streams = extraStreams.ifEmpty {
            clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }.orEmpty()
        }
        sharedContentOf(
            type = type,
            subject = getStringExtra(Intent.EXTRA_SUBJECT),
            text = getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            streams = streams.map(Uri::toString),
            isOwnProvider = isOwnProvider,
        )
    } catch (e: RuntimeException) {
        // BadParcelableException・ClassCastException 等。中身 (URI・本文) はログに出さない
        Log.w(TAG, "Unreadable share intent: ${e.javaClass.simpleName}")
        SharedContent()
    }
}

private const val TAG = "SharedContent"
