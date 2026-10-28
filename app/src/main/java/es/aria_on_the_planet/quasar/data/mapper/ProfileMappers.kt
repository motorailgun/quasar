package es.aria_on_the_planet.quasar.data.mapper

import es.aria_on_the_planet.quasar.data.remote.dto.AccountDto
import es.aria_on_the_planet.quasar.data.remote.dto.InstanceDto
import es.aria_on_the_planet.quasar.domain.model.ProfileFieldSource
import es.aria_on_the_planet.quasar.domain.model.ProfileLimits
import es.aria_on_the_planet.quasar.domain.model.ProfileSource
import es.aria_on_the_planet.quasar.domain.model.normalized

/** 返らなかった項目は [ProfileLimits] の既定値。 */
fun InstanceDto.toProfileLimits(): ProfileLimits {
    val defaults = ProfileLimits()
    val accounts = configuration?.accounts
    return ProfileLimits(
        maxDisplayNameCharacters = accounts?.maxDisplayNameLength ?: defaults.maxDisplayNameCharacters,
        maxNoteCharacters = accounts?.maxNoteLength ?: defaults.maxNoteCharacters,
        maxFields = accounts?.maxProfileFields ?: defaults.maxFields,
        maxFieldNameCharacters = accounts?.profileFieldNameLimit ?: defaults.maxFieldNameCharacters,
        maxFieldValueCharacters = accounts?.profileFieldValueLimit ?: defaults.maxFieldValueCharacters,
    )
}

/**
 * 編集用の原文 (`verify_credentials` の CredentialAccount)。`source` が無ければ (返さないサーバ) `null`。
 * 表示名はもともと HTML ではないのでそのまま使う。
 */
fun AccountDto.toProfileSource(): ProfileSource? {
    val source = source ?: return null
    return ProfileSource(
        displayName = displayName,
        note = source.note.orEmpty(),
        fields = source.fields.map { ProfileFieldSource(it.name, it.value) },
    )
}

/**
 * `PATCH /api/v1/accounts/update_credentials` のフォームの文字列の項目 (画像は別に multipart で足す)。
 *
 * フィールドは送ったもので丸ごと置き換わり、送らなければ変わらない。全部消したときに何も送らないと消えないので、
 * 空なら名前・値とも空のものを 1 つ送る (サーバは空のフィールドを捨てる)。
 */
fun ProfileSource.toUpdateCredentialsParams(): List<Pair<String, String>> = buildList {
    add("display_name" to displayName)
    add("note" to note)
    val fields = normalized().fields.ifEmpty { listOf(ProfileFieldSource("", "")) }
    fields.forEachIndexed { index, field ->
        add("fields_attributes[$index][name]" to field.name)
        add("fields_attributes[$index][value]" to field.value)
    }
}
