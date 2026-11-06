package es.aria_on_the_planet.quasar.data.translation

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.core.content.getSystemService
import es.aria_on_the_planet.quasar.R
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 翻訳のモデル (PLaMo 3 Translate 0.8B の GGUF Q4_0) の取得元。HF のリビジョンを固定し、落とした中身は sha256 で確かめる
 * (別のモデルに差し替えるときはここを全部変える)。
 */
object TranslationModelSource {
    const val FileName = "plamo-3-translate-0.8b-Q4_0.gguf"
    const val Url = "https://huggingface.co/pfnet/plamo-3-translate-0.8b-GGUF/resolve/" +
        "dbeb0856cb0b004b484f190bb71dd65876297013/$FileName"
    const val Size = 547_539_584L
    const val Sha256 = "39300577d714b108a509ca9d582ea7335ae2fa4504ee73d3358c00b4980fb32a"
}

/** モデルのファイルの状態。 */
sealed interface TranslationModelState {
    data object Missing : TranslationModelState

    /** [waiting] はネットワーク (モバイルデータを許していなければ Wi-Fi) を待っている。[total] は分からなければ 0。 */
    data class Downloading(val downloaded: Long, val total: Long, val waiting: Boolean) : TranslationModelState

    /** 落とし終えて中身を確かめている。 */
    data object Verifying : TranslationModelState
    data object Ready : TranslationModelState
    data class Failed(val reason: TranslationModelFailure) : TranslationModelState
}

enum class TranslationModelFailure {
    /** 空きが足りない。 */
    Storage,

    /** 通信・サーバの失敗。 */
    Network,

    /** 落としたものが壊れている (sha256 が合わない)。 */
    Corrupted,
}

/**
 * 端末のモデルのファイル (`Android/data/<pkg>/files/models/`。バックアップ・端末間の移行からは外している)。
 *
 * ダウンロードは OS の DownloadManager に任せる (アプリが終わっても続き、切れたら再開し、通知に進み具合が出る)。
 * 落とす先は `<名前>.download` で、終わったら sha256 を確かめてから本来の名前にする。進み具合は DownloadManager に
 * 問い合わせて [state] に流す (ダウンロード中だけ 1 秒ごと)。アプリを開き直したら [refresh] で続きを見る。
 */
class TranslationModelFiles(context: Context, private val scope: CoroutineScope) {
    private val context = context.applicationContext
    private val downloads = checkNotNull(this.context.getSystemService<DownloadManager>())

    /** 外部ストレージが使えなければ null (まず無い)。 */
    private val dir: File? get() = context.getExternalFilesDir(null)?.resolve("models")

    /** モデルのファイル (無いことがある)。 */
    val file: File get() = (dir ?: File(context.filesDir, "models")).resolve(TranslationModelSource.FileName)

    private val partial: File get() = file.resolveSibling(TranslationModelSource.FileName + PartialSuffix)

    private val _state = MutableStateFlow<TranslationModelState>(TranslationModelState.Missing)
    val state: StateFlow<TranslationModelState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var watcher: Job? = null

    /** ファイルとダウンロードの状態を見直す (起動時・設定を開いたとき)。ダウンロード中なら進み具合を追う。 */
    fun refresh() {
        scope.launch {
            mutex.withLock { update() }
        }
    }

    /** ダウンロードを始める (済み・ダウンロード中なら何もしない)。 */
    fun download() {
        scope.launch {
            mutex.withLock {
                when (update()) {
                    TranslationModelState.Missing, is TranslationModelState.Failed -> Unit
                    else -> return@withLock
                }
                val dir = dir ?: return@withLock fail(TranslationModelFailure.Storage)
                dir.mkdirs()
                partial.delete()
                if (dir.usableSpace < TranslationModelSource.Size + FreeSpaceMargin) return@withLock fail(TranslationModelFailure.Storage)
                val request = DownloadManager.Request(Uri.parse(TranslationModelSource.Url))
                    .setTitle(context.getString(R.string.translation_model_download_title))
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationInExternalFilesDir(context, "models", partial.name)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)
                downloads.enqueue(request)
                _state.value = TranslationModelState.Downloading(0, TranslationModelSource.Size, waiting = true)
                watch()
            }
        }
    }

    /** ダウンロードをやめ、モデルのファイル (落としかけも) を消す。読み込み中のモデルは先に解放しておく。 */
    suspend fun delete() {
        mutex.withLock {
            watcher?.cancel()
            withContext(Dispatchers.IO) {
                ourDownloads().forEach { downloads.remove(it.id) }
                file.delete()
                partial.delete()
            }
            _state.value = TranslationModelState.Missing
        }
    }

    private fun fail(reason: TranslationModelFailure) {
        _state.value = TranslationModelState.Failed(reason)
    }

    private fun watch() {
        if (watcher?.isActive == true) return
        watcher = scope.launch {
            while (true) {
                delay(PollInterval)
                val state = mutex.withLock { update() }
                if (state !is TranslationModelState.Downloading) break
            }
        }
    }

    /** DownloadManager とファイルを見て [state] を決める。終わったダウンロードは確かめて片付ける。 */
    private suspend fun update(): TranslationModelState {
        val state = withContext(Dispatchers.IO) { inspect() }
        // 失敗は次に見直しても失敗のまま見せる (やり直しは download)
        if (state != TranslationModelState.Missing || _state.value !is TranslationModelState.Failed) _state.value = state
        if (state is TranslationModelState.Downloading) watch()
        return state
    }

    private suspend fun inspect(): TranslationModelState {
        val download = ourDownloads().maxByOrNull { it.id }
        if (download == null) {
            // 手で置いたもの (adb) も大きさが合えば使う
            return if (file.length() == TranslationModelSource.Size) TranslationModelState.Ready else TranslationModelState.Missing
        }
        return when (download.status) {
            DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> TranslationModelState.Downloading(
                downloaded = download.downloaded,
                total = download.total.takeIf { it > 0 } ?: TranslationModelSource.Size,
                waiting = download.status != DownloadManager.STATUS_RUNNING,
            )
            DownloadManager.STATUS_SUCCESSFUL -> {
                _state.value = TranslationModelState.Verifying
                val verified = partial.isFile && sha256(partial) == TranslationModelSource.Sha256
                val moved = verified && partial.renameTo(file)
                // remove は落としたファイルも消す (名前を変えた後なら消えない)
                downloads.remove(download.id)
                partial.delete()
                if (moved) {
                    TranslationModelState.Ready
                } else {
                    Log.w(TAG, "model download corrupted (verified=$verified)")
                    TranslationModelState.Failed(TranslationModelFailure.Corrupted)
                }
            }
            else -> {
                Log.w(TAG, "model download failed: reason=${download.reason}")
                downloads.remove(download.id)
                TranslationModelState.Failed(
                    if (download.reason == DownloadManager.ERROR_INSUFFICIENT_SPACE) {
                        TranslationModelFailure.Storage
                    } else {
                        TranslationModelFailure.Network
                    },
                )
            }
        }
    }

    private class Download(val id: Long, val status: Int, val reason: Int, val downloaded: Long, val total: Long)

    /** このアプリが DownloadManager に頼んだもの (メディアの保存も含む) のうち、モデルのもの。 */
    private fun ourDownloads(): List<Download> = downloads.query(DownloadManager.Query()).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val uri = cursor.string(DownloadManager.COLUMN_URI) ?: continue
                // 版を変えた後に前の版の残りも拾えるよう、ファイル名で見る
                if (!uri.substringBefore('?').endsWith(".gguf") || !uri.contains("plamo-3-translate")) continue
                add(
                    Download(
                        id = cursor.long(DownloadManager.COLUMN_ID),
                        status = cursor.long(DownloadManager.COLUMN_STATUS).toInt(),
                        reason = cursor.long(DownloadManager.COLUMN_REASON).toInt(),
                        downloaded = cursor.long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                        total = cursor.long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                    ),
                )
            }
        }
    }

    private companion object {
        const val TAG = "TranslationModel"
        const val PartialSuffix = ".download"
        const val PollInterval = 1000L

        /** 空きはモデルに加えてこれだけ残す。 */
        const val FreeSpaceMargin = 200L * 1024 * 1024
    }
}

private fun Cursor.string(column: String): String? = getColumnIndex(column).takeIf { it >= 0 }?.let(::getString)
private fun Cursor.long(column: String): Long = getColumnIndex(column).takeIf { it >= 0 }?.let(::getLong) ?: 0

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1 shl 20)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
