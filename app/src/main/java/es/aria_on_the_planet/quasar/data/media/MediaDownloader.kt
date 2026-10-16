package es.aria_on_the_planet.quasar.data.media

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import es.aria_on_the_planet.quasar.domain.model.MediaSaveTarget
import es.aria_on_the_planet.quasar.domain.model.SaveDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** メディアを端末に保存する。受け付けたら (取得の完了ではなく) `true`。 */
interface MediaDownloader {
    suspend fun save(target: MediaSaveTarget): Boolean
}

/**
 * システムの DownloadManager に任せる。取得はシステムのプロセスで行うので、画面を閉じても続く。
 *
 * * 権限は要らない (targetSdk Q 以上は、既知の公開ディレクトリへの保存に WRITE_EXTERNAL_STORAGE 不要)。
 * * アプリ固有でないディレクトリへの保存は必ずメディアスキャンされ、ギャラリー等に出る。
 * * 進み具合・完了・失敗はシステムの通知に出る (完了した通知のタップでファイルが開く)。アプリでは結果を受け取らない。
 */
class SystemMediaDownloader(context: Context) : MediaDownloader {
    private val downloadManager = context.applicationContext.getSystemService(DownloadManager::class.java)

    // 保存先の用意 (setDestinationInExternalPublicDir) と enqueue はどちらも DownloadProvider への IPC
    override suspend fun save(target: MediaSaveTarget): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = DownloadManager.Request(Uri.parse(target.url))
                .setDestinationInExternalPublicDir(target.directory.environmentName, target.subPath)
                .setTitle(target.fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            target.mimeType?.let(request::setMimeType)
            downloadManager.enqueue(request) != -1L
        }.onFailure { Log.w(Tag, "Failed to enqueue ${target.fileName}", it) }.getOrDefault(false)
    }

    private companion object {
        const val Tag = "MediaDownloader"
    }
}

private val SaveDirectory.environmentName: String
    get() = when (this) {
        SaveDirectory.Pictures -> Environment.DIRECTORY_PICTURES
        SaveDirectory.Music -> Environment.DIRECTORY_MUSIC
        SaveDirectory.Downloads -> Environment.DIRECTORY_DOWNLOADS
    }
