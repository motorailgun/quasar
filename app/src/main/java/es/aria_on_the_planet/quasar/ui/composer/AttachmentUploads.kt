package es.aria_on_the_planet.quasar.ui.composer

import es.aria_on_the_planet.quasar.data.media.UploadSource
import es.aria_on_the_planet.quasar.domain.model.ComposeLimits
import es.aria_on_the_planet.quasar.domain.model.DraftAttachment
import es.aria_on_the_planet.quasar.domain.model.UploadFailure
import es.aria_on_the_planet.quasar.domain.model.UploadState
import es.aria_on_the_planet.quasar.domain.model.checkUpload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 添付メディアのアップロードの段取り。一覧 ([attachments] / [setAttachments]) の状態を
 * 待ち → 送信中 (進み具合) → 変換待ち → 完了 / 失敗 と進める。
 *
 * * 追加したらすぐ 1 件ずつ送る。形式・サイズが上限に合わないものは送らずに失敗にする。
 * * [prepare] があれば、検査の前に 1 回だけ通す (上限を超える画像を端末で作り直す等)。その間は送信中 (進み具合なし) と出す。
 * * 削除したら送信を止める (サーバに残った分は投稿に使われなければサーバが後で消す)。
 * * 失敗したものは [retry] で送り直せる (上限に合わないものは除く)。
 * * 代替テキストは送るときに付ける。送った後に変えたら [updateDescription] で反映する。
 *
 * 通信は関数で受け取るので、偽物でテストできる (`AttachmentUploadsTest`)。[scope] は UI スレッド
 * (viewModelScope) を想定し、一覧の更新はすべてその上で行う。
 */
class AttachmentUploads(
    private val scope: CoroutineScope,
    private val attachments: () -> List<DraftAttachment>,
    private val setAttachments: (List<DraftAttachment>) -> Unit,
    private val limits: () -> ComposeLimits,
    private val upload: suspend (
        source: UploadSource,
        description: String?,
        onProgress: (Float?) -> Unit,
        onProcessing: (mediaId: String) -> Unit,
    ) -> String,
    private val updateDescription: suspend (mediaId: String, description: String) -> Unit,
    private val onError: (Throwable) -> Unit = {},
    /** 失敗の理由を決める (送り直せるのは [UploadFailure.Failed] のみ)。 */
    private val classifyFailure: (Throwable) -> UploadFailure = { UploadFailure.Failed },
    /** 送る前に中身を替える (`null` なら替えない)。失敗したら元のまま送る。 */
    private val prepare: (suspend (UploadSource, ComposeLimits) -> UploadSource)? = null,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val sources = mutableMapOf<String, UploadSource>()
    /** [prepare] を済ませたもの。 */
    private val prepared = mutableSetOf<String>()
    private val jobs = mutableMapOf<String, Job>()
    /** サーバ側の代替テキスト (送った / 反映済みのもの)。 */
    private val remoteDescriptions = mutableMapOf<String, String>()
    private val descriptionJobs = mutableMapOf<String, Job>()

    /** 追加して送り始める。上限枚数を超える分は捨てる。 */
    fun add(newSources: List<UploadSource>) {
        val room = limits().maxMediaAttachments - attachments().size
        val added = newSources.take(room.coerceAtLeast(0)).map { source ->
            val localId = newId()
            sources[localId] = source
            DraftAttachment(localId = localId, previewUri = source.previewUri, mimeType = source.mimeType)
        }
        if (added.isEmpty()) return
        setAttachments(attachments() + added)
        added.forEach { start(it.localId) }
    }

    fun remove(localId: String) {
        jobs.remove(localId)?.cancel()
        descriptionJobs.remove(localId)?.cancel()
        sources.remove(localId)
        prepared.remove(localId)
        remoteDescriptions.remove(localId)
        setAttachments(attachments().filterNot { it.localId == localId })
    }

    /** 失敗したものを送り直す。上限に合わないもの (送っても失敗するもの) は何もしない。 */
    fun retry(localId: String) {
        val state = find(localId)?.state as? UploadState.Failed ?: return
        if (state.reason == UploadFailure.Failed) start(localId)
    }

    /** 代替テキストを変える。送信済みならサーバにも反映し、送信中なら完了後に反映する。 */
    fun setDescription(localId: String, description: String) {
        update(localId) { it.copy(description = description) }
        syncDescription(localId)
    }

    private fun start(localId: String) {
        val source = sources[localId] ?: return
        if (prepare != null && localId !in prepared) {
            jobs.remove(localId)?.cancel()
            update(localId) { it.copy(state = UploadState.Uploading(null)) }
            jobs[localId] = scope.launch {
                val replaced = try {
                    prepare.invoke(source, limits())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onError(e)
                    source
                }
                sources[localId] = replaced
                prepared += localId
                jobs.remove(localId)
                start(localId)
            }
            return
        }
        checkUpload(source.mimeType, source.size, limits())?.let { failure ->
            update(localId) { it.copy(state = UploadState.Failed(failure)) }
            return
        }
        jobs.remove(localId)?.cancel()
        update(localId) { it.copy(state = UploadState.Uploading(null)) }
        jobs[localId] = scope.launch {
            val description = find(localId)?.description.orEmpty()
            try {
                val mediaId = upload(
                    source,
                    description.ifBlank { null },
                    { progress ->
                        // 通信のスレッドから来るので UI スレッドへ移す。完了後に遅れて届いた分は無視する
                        scope.launch {
                            update(localId) {
                                if (it.state is UploadState.Pending || it.state is UploadState.Uploading) {
                                    it.copy(state = UploadState.Uploading(progress))
                                } else {
                                    it
                                }
                            }
                        }
                    },
                    { mediaId -> update(localId) { it.copy(state = UploadState.Processing(mediaId)) } },
                )
                remoteDescriptions[localId] = description
                update(localId) { it.copy(state = UploadState.Uploaded(mediaId)) }
                // 送信中に代替テキストが変わっていたら反映する
                syncDescription(localId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
                update(localId) { it.copy(state = UploadState.Failed(classifyFailure(e))) }
            } finally {
                if (jobs[localId] == coroutineContext[Job]) jobs.remove(localId)
            }
        }
    }

    private fun syncDescription(localId: String) {
        val attachment = find(localId) ?: return
        val mediaId = (attachment.state as? UploadState.Uploaded)?.mediaId ?: return
        if (remoteDescriptions[localId] == attachment.description) return
        descriptionJobs.remove(localId)?.cancel()
        descriptionJobs[localId] = scope.launch {
            val description = attachment.description
            try {
                updateDescription(mediaId, description)
                remoteDescriptions[localId] = description
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    private fun find(localId: String): DraftAttachment? = attachments().firstOrNull { it.localId == localId }

    private fun update(localId: String, transform: (DraftAttachment) -> DraftAttachment) {
        val current = attachments()
        if (current.none { it.localId == localId }) return
        setAttachments(current.map { if (it.localId == localId) transform(it) else it })
    }
}
