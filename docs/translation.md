# ポストの翻訳 — 設計

設計日: 2026-10-16。調査メモは `mlkit_plan.md`。

**実装状況**: タイムライン (`TimelineViewModel` / `TimelineScreen`) に実装済み。Profile / Post 詳細 / 通知は未対応。
実機では未確認 (サーバー側翻訳・ML Kit とも)。

## 要件

1. ML Kit によるオンデバイス翻訳は **opt-in** にする。GMS (Google Play 開発者サービス) が無い環境では使えないので、
   その場合は設定をグレーアウトする前提にする。設定画面はまだ無いので、当面は既定値をコードで切り替えるだけにする。
2. **サーバー側翻訳が使えるならそちらを優先する。**
3. 設定項目: 翻訳する言語 (翻訳元)、翻訳文を最初から表示するか (自動翻訳)、翻訳先言語。
   既定値は **韓国語 → 英語のみ**。
4. 言語の判定は `Status.language` だけを使う。端末上での言語判定 (Language ID) はしない。
5. UI は `x_translation.png` を参考にする (「Translated from Korean · Show original ⚙」 + 訳文)。

## 全体像

```
PostCard ──(translation: PostTranslationUi?, onTranslationToggle)──┐
   ▲                                                                │
PostList (中継。表示時に onPostShown)                               ▼
   ▲                                                   ViewModel.toggleTranslation(post)
TimelineScreen ── translations: PostTranslations ◀── PostTranslationController (ui/feed/)
                                                          │  状態 (StateFlow)・自動翻訳・プロバイダの切り替え
                                                          │  translationRequestFor (純粋関数) で対象と順序を決める
                                                          ▼
                                                  TranslationRepository (data/translation/)
                                        ┌────────────┴─────────────┐
                              サーバー側翻訳               OnDeviceTranslator
                              POST /statuses/:id/translate   ML Kit Translate (+ TranslatableHtml)
```

PostActions (`PostActionController`) と同じく、ViewModel が controller を 1 つ持ち、送信・状態管理はそちらに任せる。

## 1. 設定 (`TranslationSettings`)

```kotlin
// domain/model/Translation.kt (data 層の判定でも使うので domain に置く)
data class TranslationSettings(
    /** ML Kit によるオンデバイス翻訳を使う (opt-in)。GMS が無い端末では有効でも使われない。 */
    val onDeviceEnabled: Boolean = false,
    /** 翻訳ボタンを出す翻訳元の言語 (ISO 639-1、`Status.language` の主言語部分と比べる)。 */
    val sourceLanguages: Set<String> = setOf("ko"),
    /** 翻訳先の言語 (ISO 639-1)。 */
    val targetLanguage: String = "en",
    /** 翻訳できるポストは最初から訳文を表示する。 */
    val autoTranslate: Boolean = false,
)
```

* `AppContainer.translationSettings = MutableStateFlow(TranslationSettings())`。`displaySettings` と同じく、
  設定画面ができたら永続化した値で更新する。表示だけでなく ViewModel (controller) も読むので
  `DisplaySettings` には入れず別にする。
* `targetLanguage` を端末ロケールから決めることもできるが、要件 (既定は ko → en 固定) に合わせて固定値にする。
  設定画面で「端末の言語に合わせる」を選べるようにするのは後でよい。
* `autoTranslate` の既定は `false` (タップで訳す)。サーバー側翻訳は管理者の DeepL 等の枠を使うため、
  自動翻訳の既定を `true` にすると、閲覧するだけでサーバーへのリクエストが増える。
* 当面の切り替え: 既定値を書き換える (`TimelineViewModel.StreamGlobalTimeline` と同じ扱い)。

### GMS の有無 (`OnDeviceTranslationAvailability`)

* `OnDeviceTranslator.available` (`data/translation/OnDeviceTranslator.kt`):
  `GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(context) == SUCCESS` で判定する
  (play-services-basement は ML Kit から推移的に入る)。初回参照時に 1 回だけ求める (lazy)。
  使えなければ `languages` (ML Kit の対応言語) が空になり、プロバイダの候補に入らない。
* 設定画面からは `container.translationRepository.onDevice.available` を見る。
  設定画面ではこれが `false` のときスイッチを無効にし、理由 (「Google Play 開発者サービスが必要です」) を出す。
* 実際に使うかどうか: `settings.onDeviceEnabled && onDeviceTranslationAvailable`。
* microG 等で GMS 判定を通っても、モデルのダウンロードに失敗することがある。そのときはポスト単位の
  エラー表示にとどめ、自動で無効にはしない (実機で要確認。後で「失敗が続いたら無効にする」を足してもよい)。

## 2. データ層

### DTO / ドメインモデル

* `StatusDto.language: String? = null` を追加する。
* `Post.language: String?` を追加する (ブーストなら中身の値)。AGENTS.md のドメインモデル節も更新する。
* `InstanceConfigurationDto.translation: InstanceTranslationDto?` (`enabled: Boolean = false`) を追加する。
* 新規 DTO:
  * `TranslationDto` — `content` (HTML)、`spoiler_text`、`detected_source_language`、`provider`
    ([Translation](https://docs.joinmastodon.org/entities/Translation/))。投票・メディアの代替テキストの訳も返るが、今回は読まない。
  * 翻訳可能な言語の組: `Map<String, List<String>>` (翻訳元 → 翻訳先)。

### API (`MastodonApi`)

| 用途 | エンドポイント |
| --- | --- |
| 翻訳が有効か | `GET /api/v2/instance` → `configuration.translation.enabled` (既存の `streamingUrl` と同じ呼び出し) |
| 翻訳できる言語の組 | `GET /api/v1/instance/translation_languages` (4.2+) |
| 翻訳 | `POST /api/v1/statuses/:id/translate` (form `lang` = 翻訳先。要認証) |

### 翻訳プロバイダ (`data/translation/`)

```kotlin
data class TranslatedContent(
    val html: String,               // 訳文 (HTML。リンク・:shortcode: は原文のまま残る)
    val contentWarning: String?,    // CW の訳 (無ければ null)
    val sourceLanguage: String,     // 表示用 (「Translated from Korean」)
    val provider: TranslationProviderType,  // Server / OnDevice
    val sourceTextHash: Int,        // 訳した時点の本文 + CW。編集後に古い訳を出さないため
)

enum class TranslationProviderType { Server, OnDevice }

// TranslationRepository
suspend fun translate(session, request: TranslationRequest, provider: TranslationProviderType, onDownloadingModel: () -> Unit): TranslatedContent
```

* **サーバー側翻訳** (`TranslationRepository.translate`): `POST /translate`。応答の `content` は HTML のまま (DeepL 等はリンクを保つ)。
* **`OnDeviceTranslator`**: ML Kit。
  * `Translator` は言語の組ごとにアプリ全体で 1 つ作って使い回す (`AppContainer` が持つ)。
  * 初回は `downloadModelIfNeeded()`。ダウンロードが要るときは先に `onDownloadingModel()` を呼んで UI に伝える。
    回線条件は付けない (opt-in した上で翻訳を押しているため)。Wi-Fi 限定は後で設定項目にしてもよい。
  * `Task` は `kotlinx-coroutines-play-services` の `await()` で待つ。
  * HTML の扱いは下記「3. オンデバイス翻訳での HTML」。
* **`TranslationRepository`**: サーバーの能力の取得 (`serverCapabilities`) と、指定したプロバイダでの翻訳 (`translate`)。
  どちらを使うか・失敗時の切り替えは `PostTranslationController` が行う。

#### プロバイダの選択 (純粋関数 `translationRequestFor`、`data/translation/TranslationProviders.kt`)

入力: ポスト、設定、オンデバイスが使えるか、サーバーの能力 (`ServerTranslationCapabilities`: enabled + 言語の組)、ログイン有無。
出力: `TranslationRequest` (翻訳元・翻訳先・試す順のプロバイダ)。翻訳しないなら `null` (翻訳ボタンを出さない)。

1. 翻訳元 = `post.language` の主言語部分 (`Locale.forLanguageTag(it).language`)。null・空・`und` なら翻訳しない
   (端末上で判定しないため)。
2. 翻訳元 ∉ `sourceLanguages`、または翻訳元 = 翻訳先なら翻訳しない。本文と CW がどちらも空でも翻訳しない。
3. **サーバー**: ログイン中・`enabled`・言語の組に (翻訳元 → 翻訳先) がある・公開範囲が Public / Unlisted のとき候補にする。
   * 公式 Web UI も Public / Unlisted にしか翻訳ボタンを出さず、サーバーもそれ以外は拒否する (`distributable?`)。
     フォロワー限定・DM を外部の翻訳サービスに送らないという意味でもこれに合わせる。
   * 言語の組の API が無い (4.2 未満) サーバーでは、`enabled` だけで候補にする。
4. **オンデバイス**: `onDeviceEnabled && available` かつ ML Kit が両言語に対応 (`TranslateLanguage.fromLanguageTag != null`)。
5. 順序はサーバー → オンデバイス。サーバーが失敗 (403 / 503 / 通信エラー) したら次を試す。

* サーバーの能力は `ServerTranslationCapabilities` として ViewModel の生成時に 1 回取得し、`TranslationEnvironment` に入れる
  (取得前・取得失敗時は「サーバー翻訳なし」)。
* 対象: 本文 + CW。投票の選択肢・代替テキスト・プロフィール文・引用先は今回は訳さない。

## 3. オンデバイス翻訳での HTML

ML Kit はプレーンテキストしか受け付けないので、本文 HTML を分けて訳し、HTML に組み立て直す。
訳文を HTML で返せば、表示はサーバー側翻訳と同じ `EmojiText(isHtml = true)` に乗る (リンク・絵文字が生きる)。

純粋関数 (`data/translation/TranslatableHtml.kt`。入力は API の HTML そのまま。表示時に `EmojiText` が
`simplifyMastodonHtml` を通すので、invisible span 等は Keep の中にそのまま残しておけばよい):

```kotlin
sealed interface TranslationSegment {
    data class Text(val text: String) : TranslationSegment   // プレーンテキスト (エンティティは解決済み)
    data class Keep(val raw: String) : TranslationSegment    // タグ、<a>…</a>、:shortcode:、空白
}
fun splitHtmlForTranslation(html: String): List<TranslationSegment>
fun splitPlainTextForTranslation(text: String): List<TranslationSegment>   // CW 用
suspend fun translateHtml(html: String, translate: suspend (String) -> String): String   // 訳文は HTML エスケープ
suspend fun translatePlainText(text: String, translate: suspend (String) -> String): String
```

* タグ (`<p>`, `<br>`, `<b>` 等) はすべて区切りとして Keep。
* Keep にするもの: `<a>` 要素 (メンション・ハッシュタグ・URL) 全体、`:shortcode:`、テキストの前後の空白。
* 段落の先頭・末尾にある Keep (「@user こんにちは」「… #tag」) は文脈を切らないので問題にならない。
  文中の Keep では前後に分けて訳すことになり訳が不自然になりうるが、プレースホルダを入れて訳すと
  ML Kit が記号を崩すことがあるため、まずは分割で済ませる。
* 空白だけの Text は訳さずそのまま残す。
* テスト (`TranslatableHtmlTest`): 分割 → 恒等変換で組み立てると元の HTML と表示上同じになる、リンク・絵文字が
  そのまま残る、エスケープが必要な文字 (`<`, `&`) を含む訳文。

## 4. 状態 (`ui/feed/PostTranslations.kt`)

```kotlin
sealed interface PostTranslationState {
    data class Translating(val downloadingModel: Boolean = false) : PostTranslationState
    data class Translated(val content: TranslatedContent, val showing: Boolean = true) : PostTranslationState
    data object Failed : PostTranslationState
}

class PostTranslationController(
    scope: CoroutineScope,
    settings: StateFlow<TranslationSettings>,
    environment: StateFlow<TranslationEnvironment>,
    translate: suspend (TranslationRequest, TranslationProviderType, onDownloadingModel: () -> Unit) -> TranslatedContent,
    onError: (String, Throwable) -> Unit,
) {
    val translations: StateFlow<PostTranslations>   // 状態 + 設定 + 環境。PostTranslations.of(post) で表示を求める
    fun toggle(post: Post)        // 未翻訳 → 翻訳、表示中 → 原文、原文 → 訳 (キャッシュ)、失敗 → 再試行
    fun onShown(post: Post)       // autoTranslate のときだけ未翻訳なら翻訳を始める
}
```

* キーは `statusId`。同じポストが複数カラム・ブーストの外側に出ても状態は 1 つ。
* 翻訳済みでも `sourceTextHash` が今の本文と違えば (編集された) 未翻訳として扱う。
* 同じポストの翻訳が走っている間は次の要求を無視する (`PostActionController` の inFlight と同様)。
* 件数の上限: 400 件程度を超えたら古い順に捨てる (タイムラインの上限 `MaxTimelinePosts` と揃える)。
* 自動翻訳は「表示されたもの」だけ: `PostList` の各行の `LaunchedEffect(post.statusId) { onShown(post) }`。
  読み込んだ全件を訳すとサーバーへの要求が膨らむため。同時実行数は 2 程度に絞る (`Semaphore`)。
* 設定が変わったら (翻訳先が変わる等)、状態をすべて捨てる。
* UI 向けの形:

```kotlin
sealed interface PostTranslationUi {
    data object Available : PostTranslationUi                         // 「Translate post」
    data class Loading(val downloadingModel: Boolean) : PostTranslationUi
    data class Shown(val content: TranslatedContent) : PostTranslationUi   // 訳文 + 「Show original」
    data class Original(val content: TranslatedContent) : PostTranslationUi // 原文 + 「Show translation」
    data object Failed : PostTranslationUi                             // 「Couldn't translate · Retry」
}
```

### 画面への組み込み

* 最初は **タイムライン (`TimelineViewModel`) のみ**。Profile / Post 詳細 / 通知は後で同じ controller を足す
  (PostCard 側は `translation = null` なら今と同じ表示)。
* `PostCard(translation: PostTranslationUi? = null, onTranslationToggle: () -> Unit = {})` を追加し、
  `PostList(translationOf: (Post) -> PostTranslationUi? = { null }, onTranslationToggle: (Post) -> Unit = {}, onPostShown: (Post) -> Unit = {})`
  で中継する。画面側は `viewModel.translations` を collect して `translationOf = translations::of` で渡す。

## 5. UI (`x_translation.png` を参考に)

翻訳 UI は PostCard / PostDetail の本文の位置に置く (`ui/component/post/PostTranslation.kt`)。

* **訳文表示中 (`Shown`)**: 本文の直上に 1 行のラベル、その下に訳文。
  ```
  [文A] Translated from Korean  Show original
  訳文 (EmojiText, isHtml)
  ```
  * ラベル: `Icons.Outlined.Translate` (16dp) + 「Translated from Korean」(labelMedium, onSurfaceVariant) +
    「Show original」(labelMedium, primary、タップで切り替え)。
  * 言語名は `Locale.forLanguageTag(source).getDisplayLanguage(現在のロケール)`。
  * オンデバイスで訳したときは「Translated from Korean · on device」のように補足し、サーバーとの品質差が
    分かるようにする (サーバーは `provider` があれば「via DeepL」)。
  * X の歯車 (翻訳設定) は設定画面ができてから付ける。
  * 長文の折りたたみ (`CollapsiblePostText`) は訳文にもそのまま掛ける。CW があれば CW 文言も訳に差し替える
    (CW の開閉状態は共通)。
* **未翻訳 (`Available`) / 原文表示 (`Original`)**: 本文の下に「Translate post」/「Show translation」
  (labelMedium, primary)。ボタンは本文と投票の間に置き、メディアより上。
* **翻訳中 (`Loading`)**: 同じ位置に小さい `LoadingIndicator` + 「Translating…」/「Downloading Korean model…」。
  自動翻訳中も原文を出したままにし、訳が来たら差し替える (差し替えのアニメーションは未実装)。
* **失敗 (`Failed`)**: 「Couldn't translate · Retry」(onSurfaceVariant + primary)。
* 引用 (QuotedPost) と通知の抜粋には出さない。
* 部品は `TranslatedLabel` / `TranslationFooter` (`ui/component/post/PostTranslation.kt`)。文字列は `translation_*`。

## 6. 依存の追加

| 座標 | 用途 |
| --- | --- |
| `com.google.mlkit:translate:17.0.3` | オンデバイス翻訳 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2` | `Task.await()` |

Language ID は使わない (要件 4)。

## 7. テスト

* `TranslationRequestTest` (`translationRequestFor`): 言語の対象外・同じ言語・`language` 無し、未ログイン、
  フォロワー限定はサーバーを使わない、サーバー無効でオンデバイスのみ、どちらも無いと空。
* `TranslatableHtmlTest`: 上記。
* `PostTranslationControllerTest`: 偽の translator で、トグルの遷移、サーバー失敗 → オンデバイスへの切り替え、
  二重要求の無視、編集後の無効化、設定変更でのリセット、上限で捨てる。ログは `onError` で受ける。

## 8. 残り

* Profile (ピン留め含む) / Post 詳細 (`PostDetail`) / 通知への組み込み (同じ controller を各 ViewModel に持たせる)。
* 設定画面 (オンデバイスのスイッチは `onDevice.available` でグレーアウト、X の歯車からも開く)。
* 訳文への差し替えのアニメーション。

## 未決・要確認

* microG / GMS 無し端末でのモデルのダウンロード可否 (実機確認)。
* サーバー側の `lang` に `en` を渡して、翻訳元 `ko` の組が無いサーバーでの挙動 (`translation_languages` を信じる前提)。
* 翻訳元を「すべての言語」にする選択肢を設定に入れるか (`sourceLanguages` が空 = すべて、等)。
* 自動翻訳を、サーバーでなくオンデバイスが使えるときだけに限るか (サーバー負荷への配慮)。
