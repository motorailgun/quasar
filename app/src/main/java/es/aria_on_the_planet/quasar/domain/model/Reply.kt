package es.aria_on_the_planet.quasar.domain.model

/**
 * 返信を始める・やめるときに書き換える投稿欄の値。本文以外 (添付・投票) は返信とは関係なく残す。
 */
data class ReplyFields(
    val text: String,
    val visibility: PostVisibility,
    val contentWarningEnabled: Boolean,
    val contentWarning: String,
)

/**
 * 返信のために自動で変えた内容の記録。ユーザーが後から変えていなければ、返信先の付け替え・取り消しで
 * 元に戻したり置き換えたりする。
 *
 * @param before 返信を始める前の公開範囲・CW (本文は使わない)。
 * @param mentions 本文に自動で入れた宛先。入れていなければ空文字。
 * @param visibility 自動で設定した公開範囲。
 * @param contentWarning 返信先から引き継いだ CW の文言。引き継いでいなければ `null`。
 */
data class ReplyMemory(
    val before: ReplyFields,
    val mentions: String,
    val visibility: PostVisibility,
    val contentWarning: String?,
)

/**
 * 返信の宛先の acct。Mastodon Web と同じく作者を先頭にし、自分 ([myAcct]) と重複は除く。
 * メンションの無い自分のポストへの返信なら空。
 */
fun replyRecipients(target: Post, myAcct: String?): List<String> =
    (listOf(target.author.screenName) + target.mentions.map { it.acct })
        .filterNot { myAcct != null && it.equals(myAcct, ignoreCase = true) }
        .distinctBy { it.lowercase() }

/** 本文の先頭に入れる宛先 (`@作者 @メンション先 … `)。宛先が無ければ空文字。 */
fun replyMentions(target: Post, myAcct: String?): String {
    val accts = replyRecipients(target, myAcct)
    return if (accts.isEmpty()) "" else accts.joinToString(separator = " ", postfix = " ") { "@$it" }
}

/** 返信の公開範囲。[base] と返信先の公開範囲のうち狭い方 (ダイレクトへの返信はダイレクト)。 */
fun replyVisibility(base: PostVisibility, target: PostVisibility): PostVisibility =
    if (target.ordinal > base.ordinal) target else base

/**
 * [target] への返信を始める (既に返信中なら返信先を付け替える)。ユーザーが書いた本文・自分で選んだ公開範囲・CW は
 * 変えない。
 *
 * @param memory 前回の返信で自動で変えた内容。返信中でなければ `null`。
 */
fun startReply(fields: ReplyFields, memory: ReplyMemory?, target: Post, myAcct: String?): Pair<ReplyFields, ReplyMemory> {
    val before = memory?.before ?: fields
    // 本文: 空か、前回自動で入れた宛先のままなら新しい宛先にする
    val mentions = replyMentions(target, myAcct)
    val textIsAuto = fields.text.isBlank() || fields.text == memory?.mentions
    val text = if (textIsAuto) mentions else fields.text
    // 公開範囲: 自分で選び直していなければ、返信前の値と返信先の狭い方
    val visibilityIsAuto = memory == null || fields.visibility == memory.visibility
    val visibility = if (visibilityIsAuto) replyVisibility(before.visibility, target.visibility) else fields.visibility
    // CW: 自分で書いていなければ返信先の CW を引き継ぐ (無ければ返信前に戻す)
    val previousCw = memory?.contentWarning
    val cwIsAuto = !fields.contentWarningEnabled || fields.contentWarning.isBlank() ||
        (previousCw != null && fields.contentWarning == previousCw)
    val targetCw = target.contentWarning
    val (cwEnabled, cw) = when {
        !cwIsAuto -> fields.contentWarningEnabled to fields.contentWarning
        targetCw != null -> true to targetCw
        else -> before.contentWarningEnabled to before.contentWarning
    }
    return ReplyFields(text, visibility, cwEnabled, cw) to ReplyMemory(
        before = before,
        mentions = if (textIsAuto) mentions else "",
        visibility = visibility,
        contentWarning = targetCw.takeIf { cwIsAuto },
    )
}

/** 返信をやめる。自動で変えた内容のうち、ユーザーが変えていないものを返信前に戻す。 */
fun cancelReply(fields: ReplyFields, memory: ReplyMemory): ReplyFields {
    val cwIsAuto = memory.contentWarning != null &&
        fields.contentWarningEnabled && fields.contentWarning == memory.contentWarning
    return ReplyFields(
        text = if (fields.text == memory.mentions) "" else fields.text,
        visibility = if (fields.visibility == memory.visibility) memory.before.visibility else fields.visibility,
        contentWarningEnabled = if (cwIsAuto) memory.before.contentWarningEnabled else fields.contentWarningEnabled,
        contentWarning = if (cwIsAuto) memory.before.contentWarning else fields.contentWarning,
    )
}

/**
 * 何も書いていないか (自動で入れた宛先・CW だけか)。ポスト画面を開いたときに返信先を自動で付ける・離れるときに
 * 外す判定に使う。
 */
fun ReplyFields.isUntouched(memory: ReplyMemory?, hasAttachments: Boolean, hasPoll: Boolean): Boolean {
    if (hasAttachments || hasPoll) return false
    val textUntouched = text.isBlank() || text == memory?.mentions
    val cwUntouched = !contentWarningEnabled || contentWarning.isBlank() ||
        (memory?.contentWarning != null && contentWarning == memory.contentWarning)
    return textUntouched && cwUntouched
}
