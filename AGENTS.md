# Quasar — AGENTS.md

Jetpack Compose + Material 3 Expressive で作る Mastodon クライアント。
API リファレンス: https://docs.joinmastodon.org/api/ , https://docs.joinmastodon.org/methods/

## 技術スタック

| 用途 | ライブラリ |
| --- | --- |
| UI | Jetpack Compose, `androidx.compose.material3` 1.5.x (Expressive API: `MaterialExpressiveTheme`, `LoadingIndicator`, `ButtonGroup` 等) |
| 状態管理 | `androidx.lifecycle` ViewModel + `StateFlow` |
| 通信 | OkHttp + kotlinx.serialization (薄い自前 API クライアント) |
| 画像 | Coil 3 (`coil-compose`, `coil-network-okhttp`) |
| ビルド | AGP 9 (built-in Kotlin), version catalog `gradle/libs.versions.toml` |

DI フレームワークは現時点では使わず、`AppContainer` (手動 DI) で依存を組み立てる。

## パッケージ構成

```
es.aria_on_the_planet.quasar
├── QuasarApplication.kt / MainActivity.kt
├── di/AppContainer.kt              手動 DI
├── data/
│   ├── remote/
│   │   ├── MastodonApi.kt          REST 呼び出し (OkHttp)
│   │   └── dto/                    API の JSON そのままの形 (Account, Status, CustomEmoji)
│   ├── mapper/                     DTO → domain 変換
│   ├── session/SessionStore.kt     サーバ URL / アクセストークン保持
│   └── repository/TimelineRepository.kt
├── domain/model/                   UI が扱うモデル (User, Post, Timeline, CustomEmoji, PreviewCard, MediaAttachment)
└── ui/
    ├── theme/                      MaterialExpressiveTheme
    ├── component/
    │   ├── text/EmojiText.kt       カスタム絵文字対応テキスト (Text のラッパー)
    │   ├── post/PostCard.kt        ポスト 1 件
    │   ├── post/PostList.kt        ポストの集合 (LazyColumn)
    │   ├── card/LinkPreviewCard.kt リンクプレビュー
    │   ├── media/MediaAttachments.kt 添付メディアの並び + 1 件 (MediaAttachmentItem)
    │   ├── image/                  BlurHash デコーダ + プレースホルダ Painter
    │   └── modifier/               汎用 Modifier (FadingEdges 等)
    ├── login/                      サーバ URL + トークン入力 (暫定。OAuth は後で)
    └── timeline/                   タイムライン画面 + ViewModel
```

レイヤ依存は `ui → domain ← data` の一方向。UI は DTO を直接触らない。

## ドメインモデル

```kotlin
data class CustomEmoji(val shortcode: String, val url: String, val staticUrl: String)

data class User(
    val inServerId: String,   // サーバ内のアカウント ID (Account.id)
    val screenName: String,   // @username または @username@remote (Account.acct)
    val displayName: String,  // 表示名 (カスタム絵文字を含みうる)
    val serverUrl: String,    // ユーザーが所属するサーバ (Account.url から導出)
    val iconUrl: String,      // Account.avatar
    val note: String,         // プロフィール文 (HTML, カスタム絵文字を含みうる)
    val emojis: List<CustomEmoji>,
)

data class Post(
    val id: String,
    val author: User,              // ユーザー 1 : ポスト N
    val text: String,              // 本文 (HTML)
    val createdAt: Instant,
    val repliesCount: Int,         // replies_count
    val boostsCount: Int,          // reblogs_count
    val reactionsCount: Int,       // favourites_count (将来絵文字リアクション対応時に拡張)
    val emojis: List<CustomEmoji>,
    val mediaAttachments: List<MediaAttachment>,  // media_attachments (0〜15 件)
    val card: PreviewCard?,        // 本文中の URL のリンクプレビュー
    val viewer: PostViewerState,   // 自分がこのポストに行った操作
    val boostedBy: User?,          // ブーストの場合、ブーストしたユーザー
)

/** ログイン中ユーザーから見た状態。API では認証時のみ返るため、未認証時はすべて false。 */
data class PostViewerState(
    val bookmarked: Boolean,       // bookmarked
    val reblogged: Boolean,        // reblogged
    val favourited: Boolean,       // favourited
)

data class PreviewCard(
    val url: String,
    val title: String,
    val description: String,      // 空文字のことがある
    val imageUrl: String?,         // image
    val blurhash: String?,
)

enum class MediaType { Image, Video, Gifv, Audio, Unknown }

data class MediaAttachment(
    val id: String,
    val type: MediaType,
    val url: String,               // url ?: remote_url ?: preview_url
    val previewUrl: String?,
    val meta: MediaMeta?,          // original / small のサイズと duration だけを取り出す
    val blurhash: String?,
    val description: String?,      // 代替テキスト
)

data class MediaMeta(val original: MediaSize?, val small: MediaSize?, val durationSeconds: Double?) {
    val aspectRatio: Float?        // small 優先、なければ original。width/height から計算
}

enum class TimelineType { Home, Local, Global }

data class Timeline(val type: TimelineType, val posts: List<Post>)  // タイムライン 1 : ポスト N
```

* 件数・`viewer` フラグ・メディア・カードはブーストの場合も中身 (`reblog`) の値を使う。
* `meta` はメディア種別やサーバで中身が異なる (動画の original には `aspect` が無い等) ため、DTO では
  `width` / `height` / `duration` のみを nullable な `Double` で緩く受け、アスペクト比は自前で計算する。
* ブースト (`reblog != null`) は中身のポストを `Post` とし、`boostedBy` にブーストしたユーザーを入れる。
  ページングには元の Status ID が必要なので、`Post.id` は外側の Status ID を使い、表示用 ID と区別しない。
* `User` は `inServerId + serverUrl` で一意。

## タイムライン API 対応

| TimelineType | エンドポイント | 認証 |
| --- | --- | --- |
| Home | `GET /api/v1/timelines/home` | 必須 |
| Local | `GET /api/v1/timelines/public?local=true` | 任意 (サーバ設定次第) |
| Global | `GET /api/v1/timelines/public` | 任意 (サーバ設定次第) |

ページングは `max_id` (古い方向) を使う。`TimelineRepository.fetch(type, maxId)` が `List<Post>` を返す。

## 再利用 UI コンポーネント

### EmojiText (`ui/component/text/EmojiText.kt`)
アカウント名・ポスト本文・プロフィール文はカスタム絵文字 `:shortcode:` を含みうるため、**`Text` を直接使わず必ず `EmojiText` を使う**。

* `EmojiText(text: String, emojis: List<CustomEmoji>, isHtml: Boolean, ...)`
  * `isHtml = true` のとき Mastodon の HTML を `AnnotatedString.fromHtml` で変換 (リンク付き)。
  * `:shortcode:` を `emojis` と照合し、`InlineTextContent` + Coil の `AsyncImage` で画像に置換。
  * 絵文字サイズは `fontSize` に追従 (em 単位)。
* 変換処理 (`replaceEmojiShortcodes`, `simplifyMastodonHtml`) は純粋関数として分離し、ユニットテスト可能にする。

### PostCard (`ui/component/post/PostCard.kt`)
ポスト 1 件の表示。タイムライン・プロフィール・詳細画面などで再利用する。

* `PostCard(post: Post, modifier, mediaHeight: Dp = PostCardDefaults.MediaHeight, onUserClick, onPostClick, onMediaClick)`
* 構成: (ブースト表示) / アイコン + 表示名 (EmojiText) + screenName / 本文 (EmojiText) /
  添付メディア (MediaAttachments) / リンクプレビュー (LinkPreviewCard) / アクション行
* `mediaHeight` はそのまま `MediaAttachments` に渡す (`PostList` も同名パラメータで中継する)。
* アクション行: リプライ数・ブースト数・リアクション数・ブックマーク。`viewer` のフラグが立っているものは
  塗りアイコン + 色付き (ブースト=tertiary, リアクション=primary, ブックマーク=secondary) で表示する。
  現時点では表示のみで、操作 (favourite/reblog/bookmark API 呼び出し) は未実装。
* `Post` 以外の状態を持たない (stateless)。

### PostList (`ui/component/post/PostList.kt`)
ポストの集合。タイムライン以外 (プロフィールの投稿一覧など) でも使う。

* `PostList(posts, onLoadMore, isLoadingMore, modifier, contentPadding, header, ...)`
* `LazyColumn` + `key = post.id`。末尾付近で `onLoadMore` を呼ぶ。
* データ取得には関与しない (呼び出し側の ViewModel が担当)。

### LinkPreviewCard (`ui/component/card/LinkPreviewCard.kt`)
* `LinkPreviewCard(card: PreviewCard, modifier, onClick: ((PreviewCard) -> Unit)? = null)`
* `OutlinedCard` で、左に画像 (あれば。BlurHash プレースホルダ付き)、右にホスト名・タイトル・説明文。
* `onClick` 未指定時は `LocalUriHandler` でリンク先を開く。

### MediaAttachments / MediaAttachmentItem (`ui/component/media/MediaAttachments.kt`)
* `MediaAttachments(attachments, height: Dp, modifier, onClick)` が `MediaAttachmentItem` を横に並べる。
* 全アイテムを `height` に揃え、幅は `height * アスペクト比`。合計幅が収まらない場合は `horizontalScroll`。
* まだスクロールできる側の端は 24dp フェードさせる (`ScrollState.canScrollBackward/Forward` + アニメーション)。
  汎用の `Modifier.horizontalFadingEdges` (`ui/component/modifier/FadingEdges.kt`) を使い、スクロール Modifier より外側に付ける。
* アスペクト比不明時は 1:1、極端な比率は 1/3〜3 に丸めて Crop 表示する。
* 種別ごとの表示: video=再生アイコン、gifv=「GIF」ラベル、audio=プレビューが無ければ音声アイコン。
* プレースホルダ/エラー時は BlurHash (`rememberBlurHashPainter`) を使う。
* メディアビューア未実装のため、タイムラインではタップでメディア URL をブラウザで開いている。
* 未対応: `sensitive` (閲覧注意) によるぼかし表示。

## 画面

* **Login (暫定)**: サーバ URL とアクセストークン (任意) を入力。トークンなしでは Home を無効化。
  OAuth (`/api/v1/apps` + `/oauth/authorize`) は後続タスク。
* **Timeline**: 上部に Home / Local / Global の切り替え (Expressive の `ButtonGroup` 系トグル)、
  中身は `PostList`。各タイムラインの状態は `TimelineViewModel` が `TimelineType` ごとに保持。
  pull-to-refresh と追加読み込みに対応。読み込み中は `LoadingIndicator` を使う。

## 開発メモ

* ビルド: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug`
* テスト: `./gradlew :app:testDebugUnitTest`
* Expressive API は `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` が必要。
* 文字列リソースは `res/values/strings.xml` に置く。
