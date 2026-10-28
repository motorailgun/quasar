package es.aria_on_the_planet.quasar.domain.model

/**
 * プロフィールの編集できる中身 (`verify_credentials` の `display_name` / `source.note` / `source.fields`)。
 * 表示用の [Profile] と違い、プロフィール文・フィールドの値は HTML ではなくユーザーが書いたままの文字列。
 */
data class ProfileSource(
    val displayName: String,
    val note: String,
    val fields: List<ProfileFieldSource>,
)

data class ProfileFieldSource(val name: String, val value: String)

/** プロフィールの上限 (`configuration.accounts`)。無いサーバ (4.x の途中まで) は Mastodon の既定値。 */
data class ProfileLimits(
    val maxDisplayNameCharacters: Int = 30,
    val maxNoteCharacters: Int = 500,
    val maxFields: Int = 4,
    val maxFieldNameCharacters: Int = 255,
    val maxFieldValueCharacters: Int = 255,
)

/** 保存できない理由。 */
enum class ProfileProblem {
    DisplayNameTooLong,
    NoteTooLong,
    TooManyFields,
    FieldTooLong,
}

/** 名前も値も空のフィールドを除く (サーバも保存しない)。 */
fun ProfileSource.normalized(): ProfileSource =
    copy(fields = fields.filterNot { it.name.isBlank() && it.value.isBlank() })

/** 保存できない理由 (無ければ空)。数え方はサーバに合わせる ([countDisplayNameCharacters] / [countNoteCharacters])。 */
fun ProfileSource.problems(limits: ProfileLimits): Set<ProfileProblem> = buildSet {
    if (countDisplayNameCharacters(displayName) > limits.maxDisplayNameCharacters) add(ProfileProblem.DisplayNameTooLong)
    if (countNoteCharacters(note) > limits.maxNoteCharacters) add(ProfileProblem.NoteTooLong)
    val filled = normalized().fields
    if (filled.size > limits.maxFields) add(ProfileProblem.TooManyFields)
    if (filled.any { countFieldCharacters(it.name) > limits.maxFieldNameCharacters || countFieldCharacters(it.value) > limits.maxFieldValueCharacters }) {
        add(ProfileProblem.FieldTooLong)
    }
}

/** 表示名の文字数。サーバは Rails の `length` (コードポイント単位) で数える。 */
fun countDisplayNameCharacters(text: String): Int = text.codePointCount(0, text.length)

/**
 * プロフィール文の文字数。サーバの `NoteLengthValidator` は投稿と同じく URL を 23 文字、`@user@domain` を `@user` として
 * 書記素単位で数える。
 */
fun countNoteCharacters(text: String): Int = countPostCharacters(text)

/** フィールドの名前・値の文字数 (書記素単位)。 */
fun countFieldCharacters(text: String): Int = graphemeCount(text)
