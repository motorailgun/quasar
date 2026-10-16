package es.aria_on_the_planet.quasar.data.media

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import java.io.InputStream

/** アップロードするファイル。リポジトリを Android (ContentResolver) から切り離し、偽物でテストできるようにする。 */
interface UploadSource {
    val fileName: String
    /** 分からなければ `null` (サーバが中身から判定する)。 */
    val mimeType: String?
    /** バイト数。分からなければ `null`。 */
    val size: Long?
    /** 送る前のサムネイルに使う URI (Coil で読めるもの)。無ければ `null`。 */
    val previewUri: String?
    /** 中身を読む。送り直しのたびに呼ばれうる。 */
    fun open(): InputStream
}

/**
 * Photo Picker 等が返す `content://` の URI から読む。読める期間は選んだ画面が生きている間なので、選んだらすぐ送る。
 * 名前・サイズ・MIME 型は作るときに 1 回だけ問い合わせる。
 */
class ContentUriUploadSource(
    private val resolver: ContentResolver,
    private val uri: Uri,
) : UploadSource {
    override val mimeType: String? = resolver.getType(uri)
    override val previewUri: String = uri.toString()
    override val fileName: String
    override val size: Long?

    init {
        var name: String? = null
        var bytes: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let { name = cursor.getString(it) }
                cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let { bytes = cursor.getLong(it) }
            }
        }
        fileName = name ?: uri.lastPathSegment ?: "media"
        size = bytes
    }

    override fun open(): InputStream =
        resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
}
