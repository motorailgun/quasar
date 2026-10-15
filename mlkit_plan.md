# ML Kit によるポストのオンデバイス翻訳 — 調査メモ

調査日: 2026-10-15 (コード変更なし)

## 結論

Status の `language` を使い、無い場合のみ Language ID で判定 → ML Kit Translate で翻訳する。
HTML のリンクやカスタム絵文字 (`:shortcode:`) を壊さないよう本文を分割して訳し、翻訳状態は ViewModel 側で持つ。

## 1. 依存ライブラリ (`gradle/libs.versions.toml` / `app/build.gradle.kts`)

| 用途 | 座標 | 備考 |
| --- | --- | --- |
| 翻訳 | `com.google.mlkit:translate:17.0.3` | 言語モデルは別途ダウンロード (1 言語あたり約 30MB)。API 23 以上 |
| 言語判定 (任意) | `com.google.mlkit:language-id:17.0.6` (アプリ同梱、約 900KB) または `com.google.android.gms:play-services-mlkit-language-id:17.0.0` (Play 開発者サービス経由、約 200KB) | `language` が null のポスト用 |
| Task → coroutine | `org.jetbrains.kotlinx:kotlinx-coroutines-play-services` (既存の coroutines 1.10.2 に合わせる) | `Task.await()` で suspend 化。現状は依存に無い |

* minSdk 33 なので要件 (API 23 以上) は満たす。
* 2024-08 のリリースで 16KB ページサイズに対応済み。
* release ビルドは現状 `optimization.enable = false`。有効化しても ML Kit 側が consumer rules を持っている。
* Translate の最新版は 2024-08 から更新が無く、保守状態はやや停滞気味。

## 2. データ層の変更

* **`StatusDto` (`data/remote/dto/Dtos.kt`)**: 以下が未定義。
  * `language: String?` — ISO 639 コード。null のこともある。
  * `spoiler_text` — CW も翻訳対象にするなら必要。
* **`Post` / マッパー (`data/mapper/Mappers.kt`)**: `Post.language: String?` を追加。ブーストの場合は件数等と同様に中身 (`body`) の値を使う。
* AGENTS.md のドメインモデル節も合わせて更新する。

## 3. 翻訳ロジック (新規)

例: `data/translation/PostTranslator` (または `TranslationRepository`) を作り、`AppContainer` で組み立てる。

* **翻訳元言語の決定**: `post.language` → `TranslateLanguage.fromLanguageTag()`。null / 未対応なら Language ID を使い、`und` なら翻訳不可とする。
  * Language ID は 1 つの文字列内の多言語混在に非対応。
  * ローマ字表記の判定は日本語など一部の言語のみ対応。
* **翻訳先言語**: 端末またはアプリのロケール (`LocaleList` / per-app locale) から決める。翻訳元と同じならボタンを出さない。
* **Translator の管理**:
  * `Translation.getClient(options)` は言語ペアごとに作る。
  * ペアをキーにキャッシュして使い回し、不要になったら `close()`。現状の構成では Activity 単位の Lifecycle observer より、アプリ全体で使い回す方が合う。
* **モデルのダウンロード**:
  * `downloadModelIfNeeded(DownloadConditions)` の成功後に `translate()` を呼ぶ。
  * ダウンロード済みかの確認・削除は `RemoteModelManager` で行う。
  * 約 30MB × 言語数になるため、以下を仕様として決める:
    * Wi-Fi 限定 (`requireWifi()`) にするか
    * 初回は確認ダイアログを出すか
    * 設定画面で削除できるようにするか
* **結果のキャッシュ**: `postId (+ 翻訳先言語)` をキーにメモリ上でキャッシュする。

## 4. HTML とカスタム絵文字の扱い (一番の難所)

ML Kit が受け付けるのはプレーンテキストで、ドキュメントにも HTML の扱いの記載は無い。そのまま翻訳すると以下が壊れたり訳されたりするおそれがある:

* `<a>` タグ (メンション・ハッシュタグ・URL)
* `:shortcode:`
* `simplifyMastodonHtml` で処理している invisible / ellipsis の span

方針案:

* **案 A (簡単)**: HTML をプレーンテキスト化して訳し、訳文はリンク無しのプレーン表示にする。メンション・URL・`:shortcode:` はプレースホルダに退避し、訳した後に戻す。
* **案 B (高品質)**: `AnnotatedString.fromHtml` の結果を段落 (`<p>` / `<br>`) ごと、リンク span の外側のテキストごとに分けて訳し、リンク範囲はそのまま再構成する。

いずれの案でも、変換部分は `replaceEmojiShortcodes` と同様に純粋関数として切り出してユニットテスト可能にする (AGENTS.md の方針)。

入力長の上限はドキュメントに記載が無いため、長文は段落単位で分けて渡すのが安全。

## 5. UI と状態

* **`PostCard`**: stateless なので、以下を追加する形にする。
  * 翻訳状態パラメータ。例: `translation: PostTranslationState?` (Idle / Downloading / Translating / Done(text) / Error)
  * `onTranslateClick`
  * 表示: 本文の下に「翻訳する / 原文を表示」ボタン、翻訳中は `LoadingIndicator`、訳文は `EmojiText` で表示。
* **`PostList`**: 上記パラメータを中継する。呼び出し元 3 か所も変更が必要:
  * `ui/timeline/TimelineScreen.kt:119`
  * `ui/profile/ProfileScreen.kt:103`
  * `ui/profile/PinnedPosts.kt:33`
* **状態の持ち場所**: `PostFeedState` に `Map<postId, TranslationState>` を足すか、`PostFeed` と並ぶ共通部品を作る。ピン留めポストは `PostFeed` の外にあるため、`ProfileViewModel` でも同じ状態を扱う必要がある。
* **`strings.xml`**: 「翻訳」「原文を表示」「〜から翻訳」「モデルをダウンロード中」などを追加。

## 6. 方針として決めておくこと

* **プライバシー・配布**: ML Kit はプロプライエタリで、モデルは Google のサーバーから取得する。F-Droid 配布を考えるなら障害になる。翻訳自体は端末上で行われる。
* **Google Play 開発者サービスの無い端末で動くか**: Translate のモデル取得がこうした端末で動くかは未確認。必要なら実機で確認する。
* **サーバー側翻訳との併用**: Mastodon には `POST /api/v1/statuses/:id/translate` があり、`GET /api/v2/instance` の `configuration.translation.enabled` で利用可否が分かる。オンデバイス翻訳と切り替えられる設計にしておくと後で楽。
* **翻訳の範囲**: CW、投票の選択肢、画像の代替テキスト、プロフィール文も翻訳するか (今回は本文のみでよいか)。

## 参照

* [ML Kit Translation (Android)](https://developers.google.com/ml-kit/language/translation/android)
* [ML Kit Language ID (Android)](https://developers.google.com/ml-kit/language/identification/android)
* [ML Kit release notes](https://developers.google.com/ml-kit/release-notes)
