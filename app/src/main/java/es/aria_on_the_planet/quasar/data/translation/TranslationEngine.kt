package es.aria_on_the_planet.quasar.data.translation

/**
 * 端末内の翻訳のモデル (今は PLaMo 3 Translate を llama.cpp で動かす。`:llama` を持つ full のフレーバーだけ)。
 * 作るのはフレーバーごとの `createTranslationEngine` (src/full・src/lite)。
 */
interface TranslationEngine {
    /**
     * [prompt] の続き (訳文) を生成する。最初の呼び出しでモデルを読み込み (数秒)、しばらく使われなければ解放する。
     * 生成は 1 度に 1 つ (呼び出しが重なれば待つ)。
     */
    suspend fun generate(prompt: List<PromptSegment>, maxTokens: Int): GeneratedText

    /** 読み込んだモデルを今すぐ解放する (メモリが足りない・裏に回った・モデルを消す)。生成中なら終わるのを待つ。 */
    suspend fun release()
}

/** 生成した文字。[truncated] なら上限 (`maxTokens`・コンテキスト) で止まった (訳文が途中で切れている)。 */
data class GeneratedText(val text: String, val truncated: Boolean)
