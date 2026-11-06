package es.aria_on_the_planet.quasar.data.translation

/**
 * PLaMo 3 Translate (オンデバイス翻訳のモデル) のプロンプト (PFML3) の組み立て。
 * 形式はモデルの README (https://huggingface.co/pfnet/plamo-3-translate-0.8b-GGUF の「PFML3 プロンプト形式」)。
 *
 * ```
 * <|plamo:tag|>dataset<|plamo:msg|>translation
 * <|plamo:tag|>context<|plamo:msg|>参考文脈                         (任意)
 * <|plamo:tag|>input…<|plamo:msg|>原文<|plamo:tag|>output…<|plamo:msg|>訳文   (対訳の履歴、任意・複数)
 * <|plamo:tag|>glossary<|plamo:index|>原語<|plamo:item|>訳語 …        (任意)
 * <|plamo:tag|>input<|plamo:key|>lang<|plamo:val|>en<|plamo:msg|>原文
 * <|plamo:tag|>output<|plamo:key|>lang<|plamo:val|>ja<|plamo:msg|>   ← この後に訳文が生成される
 * ```
 * (実際には改行は入らない。文頭の `<|plamo:bos|>` は llama.cpp が付ける。)
 */

/**
 * プロンプトの断片。[special] は `<|plamo:…|>` を特殊トークンとして読むか (構造の部分だけ true)。
 * 原文などユーザーの文字列 (ポストの本文は他人が書いたもの) は false の断片にし、さらに [neutralizeSpecialTokens]
 * を通す: llama.cpp の PLaMo 3 のトークナイザは、特殊トークンとして読まない指定 (`parse_special = false`) でも
 * `<|plamo:tag|>` 等の文字列を制御トークンにしてしまうため (b12129 で確認)。どちらか一方では足りない。
 * 断片は必ず特殊トークンの境目で切れているので、まとめて読んだ場合 (llama-server 等) とトークン列は変わらない。
 */
data class PromptSegment(val text: String, val special: Boolean)

/** 日本語への訳の文体 (`form`)。 */
enum class PlamoForm(val value: String) { Plain("plain"), Polite("polite") }

data class GlossaryEntry(val source: String, val target: String)

/** 訳した原文と訳文の組 (前の文脈として渡す)。 */
data class TranslationPair(val source: String, val target: String)

data class PlamoTranslationRequest(
    val text: String,
    /** 翻訳先 ([plamoLanguageOf] で求めた PLaMo の言語コード)。 */
    val targetLanguage: String,
    /** 翻訳元。null ならモデルが判断する。 */
    val sourceLanguage: String? = null,
    /** 文体。日本語への訳のときだけ使う。 */
    val form: PlamoForm? = null,
    /** 原文を解釈するための参考文脈 (返信先の本文等)。 */
    val context: String? = null,
    val glossary: List<GlossaryEntry> = emptyList(),
    val history: List<TranslationPair> = emptyList(),
)

/** 訳文の後にモデルが出す (ここで止める)。どちらも 1 トークンの特殊トークン。 */
val PlamoStopTokens: List<String> = listOf("<|plamo:tag|>", "<|plamo:bos|>")

fun plamoTranslationPrompt(request: PlamoTranslationRequest): List<PromptSegment> {
    val prompt = PromptBuilder()
    prompt.structure("<|plamo:tag|>dataset<|plamo:msg|>translation")
    request.context?.takeIf { it.isNotBlank() }?.let {
        prompt.structure("<|plamo:tag|>context<|plamo:msg|>")
        prompt.text(it)
    }
    val inputHeader = buildString {
        append("<|plamo:tag|>input")
        request.sourceLanguage?.let { append("<|plamo:key|>lang<|plamo:val|>").append(it) }
        append("<|plamo:msg|>")
    }
    val historyOutputHeader = "<|plamo:tag|>output<|plamo:key|>lang<|plamo:val|>${request.targetLanguage}<|plamo:msg|>"
    for (pair in request.history) {
        prompt.structure(inputHeader)
        prompt.text(pair.source)
        prompt.structure(historyOutputHeader)
        prompt.text(pair.target)
    }
    if (request.glossary.isNotEmpty()) {
        prompt.structure("<|plamo:tag|>glossary")
        for (entry in request.glossary) {
            prompt.structure("<|plamo:index|>")
            prompt.text(entry.source)
            prompt.structure("<|plamo:item|>")
            prompt.text(entry.target)
        }
    }
    prompt.structure(inputHeader)
    prompt.text(request.text)
    prompt.structure(
        buildString {
            append("<|plamo:tag|>output<|plamo:key|>lang<|plamo:val|>").append(request.targetLanguage)
            if (request.form != null && request.targetLanguage == "ja") {
                append("<|plamo:key|>form<|plamo:val|>").append(request.form.value)
            }
            append("<|plamo:msg|>")
        },
    )
    return prompt.segments
}

/**
 * ユーザーの文字列の中の特殊トークンの形 (`<|plamo:…|>`。語彙の制御トークンは `<|plamo:` で始まるものと `:plamo|>`) を、
 * `<|` / `|>` の間に見えない U+2060 (WORD JOINER) を挟んで崩す。普通の文章にはまず出てこない並び。
 */
fun neutralizeSpecialTokens(text: String): String =
    text.replace("<|", "<\u2060|").replace("|>", "|\u2060>")

/** 表示・ログ用に 1 つの文字列にする (特殊トークンとそうでない部分の区別は消える)。 */
fun List<PromptSegment>.render(): String = joinToString("") { it.text }

/**
 * BCP 47 の言語タグ (`Status.language`・端末のロケール) を PLaMo 3 Translate の言語コードにする。対応していなければ null。
 * 中国語は簡体字 (`zh-hans`) と繁体字 (`zh-hant`) を分ける (台湾・香港・マカオ・`Hant` は繁体字)。
 */
fun plamoLanguageOf(tag: String?): String? {
    val parts = tag?.lowercase()?.split('-', '_')?.filter { it.isNotEmpty() } ?: return null
    val language = when (val primary = parts.firstOrNull() ?: return null) {
        "zh" -> if (parts.any { it in TraditionalChineseSubtags }) "zh-hant" else "zh-hans"
        else -> LegacyLanguageCodes[primary] ?: primary
    }
    return language.takeIf { it in PlamoLanguages }
}

private val TraditionalChineseSubtags = setOf("hant", "tw", "hk", "mo")

// 古いコード (Java の Locale が返すことがある) と、同じ言語の別名
private val LegacyLanguageCodes = mapOf("iw" to "he", "in" to "id", "jw" to "jv", "fil" to "tl")

/** README の「対応言語」(53 言語)。 */
val PlamoLanguages: Set<String> = setOf(
    "ja", "en", "zh-hans", "zh-hant", "ko", "ar", "az", "be", "bg", "bn", "ca", "ceb", "de", "es", "et", "eu", "fa",
    "fr", "gu", "he", "hi", "hr", "hu", "hy", "id", "it", "jv", "ka", "kk", "km", "lt", "lv", "ml", "mr", "ms", "my",
    "ne", "nl", "pa", "pl", "ru", "si", "sk", "sl", "sr", "sw", "ta", "te", "th", "tl", "ur", "uz", "vi",
)

private class PromptBuilder {
    val segments = mutableListOf<PromptSegment>()

    fun structure(text: String) = add(text, special = true)

    fun text(text: String) = add(neutralizeSpecialTokens(text), special = false)

    private fun add(text: String, special: Boolean) {
        val last = segments.lastOrNull()
        if (last != null && last.special == special) {
            segments[segments.lastIndex] = PromptSegment(last.text + text, special)
        } else if (text.isNotEmpty()) {
            segments += PromptSegment(text, special)
        }
    }
}
