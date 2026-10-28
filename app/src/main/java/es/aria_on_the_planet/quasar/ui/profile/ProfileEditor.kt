package es.aria_on_the_planet.quasar.ui.profile

import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import es.aria_on_the_planet.quasar.domain.model.ProfileFieldSource
import es.aria_on_the_planet.quasar.domain.model.ProfileSource
import es.aria_on_the_planet.quasar.domain.model.normalized

/**
 * 編集中のプロフィール。[original] (編集を始めたときの原文) から始まり、入力欄の中身 (TextFieldState) と
 * 選び直した画像を持つ。ViewModel が持ち、画面を組み直しても入力は残る (プロセスが終わると消える)。
 */
@Stable
class ProfileEditor(val original: ProfileSource) {
    val displayName = TextFieldState(original.displayName)
    val note = TextFieldState(original.note)
    val fields = mutableStateListOf<ProfileFieldEditor>().apply {
        addAll(original.fields.mapIndexed { index, field -> ProfileFieldEditor(field.name, field.value, key = index) })
    }

    /** 新しいアイコン (Photo Picker の URI)。`null` なら替えない。 */
    var avatar by mutableStateOf<Uri?>(null)

    /** 新しいヘッダ画像 (Photo Picker の URI)。`null` なら替えない。 */
    var header by mutableStateOf<Uri?>(null)

    /** 入力欄を開いているフィールド ([ProfileFieldEditor.key])。開けるのは 1 つだけ。 */
    var editingFieldKey by mutableStateOf<Int?>(null)
        private set

    private var nextFieldKey = fields.size

    /** 空のフィールドを足し、そのまま入力欄を開く。 */
    fun addField() {
        finishFieldEdit()
        val field = ProfileFieldEditor("", "", key = nextFieldKey++)
        fields.add(field)
        editingFieldKey = field.key
    }

    /** [field] の入力欄を開く (開いていたものは閉じる)。 */
    fun editField(field: ProfileFieldEditor) {
        if (editingFieldKey == field.key) return
        finishFieldEdit()
        editingFieldKey = field.key
    }

    /** 開いている入力欄を閉じる。名前も値も空なら、そのフィールドは除く。 */
    fun finishFieldEdit() {
        val field = fields.firstOrNull { it.key == editingFieldKey }
        editingFieldKey = null
        if (field != null && field.name.text.isBlank() && field.value.text.isBlank()) fields.remove(field)
    }

    fun removeField(field: ProfileFieldEditor) {
        if (editingFieldKey == field.key) editingFieldKey = null
        fields.remove(field)
    }

    /** 入力欄の今の中身。 */
    fun toSource(): ProfileSource = ProfileSource(
        displayName = displayName.text.toString(),
        note = note.text.toString(),
        fields = fields.map { ProfileFieldSource(it.name.text.toString(), it.value.text.toString()) },
    )

    /** 編集を始めたときから変わったか (空のフィールドを足しただけなら変わっていない)。 */
    val hasChanges: Boolean by derivedStateOf {
        avatar != null || header != null || toSource().normalized() != original.normalized()
    }
}

/** 編集中のフィールド 1 行。[key] は一覧の中で行を見分ける (並びが変わってもアニメーション・フォーカスがずれない)。 */
@Stable
class ProfileFieldEditor(name: String, value: String, val key: Int) {
    val name = TextFieldState(name)
    val value = TextFieldState(value)
}
