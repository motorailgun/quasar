# Quasar — AGENTS.md

Jetpack Compose + Material 3 Expressive で作る Mastodon クライアント。
API リファレンス: https://docs.joinmastodon.org/api/ , https://docs.joinmastodon.org/methods/

## 技術スタック

| 用途 | ライブラリ |
| --- | --- |
| UI | Jetpack Compose, `androidx.compose.material3` 1.5.x (Expressive API: `MaterialExpressiveTheme`, `LoadingIndicator` 等) |
| ぼかし | Haze (`dev.chrisbanes.haze:haze-blur`。クイック投稿バー・タイムラインのヘッダ) |
| 状態管理 | `androidx.lifecycle` ViewModel + `StateFlow` |
| 設定 | Preferences DataStore (`androidx.datastore:datastore-preferences`) |
| 画面遷移 | Navigation 3 (`androidx.navigation3` + `lifecycle-viewmodel-navigation3`)。Activity は MainActivity 1 つ |
| 通信 | OkHttp + kotlinx.serialization (薄い自前 API クライアント) |
| 画像 | Coil 3 (`coil-compose`, `coil-network-okhttp`, `coil-gif`)。APNG は LINE の apng-drawable (`com.linecorp:apng`) |
| 色 | Material Components (`com.google.android.material`) の `MaterialColors` (Custom colors の harmonize のみに使用) |
| ビルド | AGP 9 (built-in Kotlin), Kotlin 2.4 (compose / serialization プラグイン), version catalog `gradle/libs.versions.toml` |
| SDK | compileSdk 37 (Compose / Coil の最新版が要求)、targetSdk 36、minSdk 33 |

DI フレームワークは現時点では使わず、`AppContainer` (手動 DI) で依存を組み立てる。

## パッケージ構成

```
es.aria_on_the_planet.quasar
├── QuasarApplication.kt / MainActivity.kt   MainActivity が唯一の Activity (中身は QuasarNavHost)
├── di/AppContainer.kt              手動 DI
├── data/streaming/                 Streaming API (StreamingClient / StreamEvent)
├── data/media/                     アップロード元 (UploadSource / ContentUriUploadSource) と進み具合付きの RequestBody、
│                                   メディアの保存 (MediaDownloader / SystemMediaDownloader = DownloadManager)
├── data/
│   ├── remote/
│   │   ├── MastodonApi.kt          REST 呼び出し (OkHttp)
│   │   └── dto/                    API の JSON そのままの形 (Account, Status, CustomEmoji)
│   ├── mapper/                     DTO → domain 変換
│   ├── session/SessionStore.kt     サーバ URL / アクセストークン保持
│   ├── settings/                   設定の保存 (SettingsStore = DataStore / SettingsPreferences = 項目とキーの対応)
│   └── repository/                 TimelineRepository, AccountRepository, StatusRepository, InstanceRepository, EmojiRepository,
│                                   MediaRepository
├── domain/model/                   UI が扱うモデル (User, Profile, Relationship, Page, Post, PostAction, Timeline, CustomEmoji, PreviewCard, MediaAttachment, Poll, Quote,
│                                   PostDraft, ComposeLimits, PostLength, MediaSave, Reply, Mention, Hashtag)
└── ui/
    ├── theme/                      MaterialExpressiveTheme
    ├── component/
    │   ├── text/EmojiText.kt       カスタム絵文字対応テキスト (Text のラッパー)
    │   ├── post/PostCard.kt        ポスト 1 件 (+ PostActionRow)
    │   ├── post/PostActionHandler.kt アクション行のハンドラ (操作の決定 + 確認の差し込み口)
    │   ├── post/PostList.kt        ポストの集合 (LazyColumn)
    │   ├── post/PostDetail.kt      詳細画面の主役ポスト (大きめ本文 + 詳細日時)
    │   ├── post/PostTime.kt        詳細日時のフォーマット
    │   ├── post/QuotedPost.kt      引用ポストの縮小表示 / 表示不可時のプレースホルダ
    │   ├── post/ThreadLines.kt     スレッドの縦線 (ポスト画面・返信画面の返信先)
    │   ├── poll/PollView.kt        投票 (選択肢 / 結果) + PollMath.kt (割合・残り時間)
    │   ├── scaffold/DetailScaffold.kt 詳細画面共通の骨組み (戻る付きトップバー + スクロールでタイトル切替 + PTR)
    │   ├── card/LinkPreviewCard.kt リンクプレビュー
    │   ├── media/MediaAttachments.kt 添付メディアの並び + 1 件 (MediaAttachmentItem、インライン再生) + MediaClick
    │   ├── user/UserList.kt        ユーザーの集合 (LazyColumn) + UserListItem (フォロー / フォロワー一覧)
    │   ├── tabs/ConnectedTabRow.kt 画面内のタブ (Expressive の連結 ToggleButton。ExtraSmall 32dp・角丸 10dp で控えめに)
    │   ├── dialog/ConfirmDialog.kt 確認ダイアログ (投稿の破棄・フォロー / 解除)
    │   ├── dialog/ActionConfirmation.kt 設定に従う操作前の確認 (ActionConfirmationHost / LocalActionConfirmer)
    │   ├── settings/               AOSP 風の設定の部品 (SettingsScaffold / SettingsGroup / Switch・Choice・Slider 等の項目)
    │   ├── blur/SurfaceBlur.kt     浮かぶ UI の背景のぼかし (rememberSurfaceBlurStyle。半径・濃さは DisplaySettings)
    │   ├── player/                 再生部品 (PlaybackController / MediaPlayer / InlineControls / FullControls / LocalInlinePlayback)
    │   ├── image/                  RemoteImage / LocalImage (画像表示の唯一の入口) + ImageKind / BlurHash デコーダ + プレースホルダ Painter
    │   │   └── loader/             Coil の ImageLoader とデコーダ (AnimationGate / APNG のデコーダ・再生・時刻計算・ヘッダの判定)
    │   ├── list/LoadMoreEffect.kt  末尾付近で追加読み込みを呼ぶ (PostList / NotificationList 共通)
    │   ├── time/Now.kt             表示用の現在時刻 (LocalNow、一定間隔で進む)
    │   ├── composer/               投稿欄の部品 (ComposerState / Composer / ComposerTextField / VisibilityButton / ComposerToolbar /
    │   │                           AttachmentStrip / PollEditor / ComposerAccountIcon / EmojiSuggestions + Autocomplete / EmojiPicker /
    │   │                           ReplyTarget = 返信先の表示: ReplyTargetPreview / ReplyContext / ReplyingToLabel)
    │   ├── layout/                 折りたたみ (CollapsibleContent / rememberExpandedState / ShowMoreToggle / ChevronToggle) /
    │   │                           CollapsingHeaderLayout (スクロールで畳まれるヘッダ + 残るタブ + 本体)
    │   └── modifier/               汎用 Modifier (FadingEdges: 左右 / 下端)
    ├── feed/                       PagedFeed<T> (max_id ページングの状態管理) / PostFeed / PollVoting / PostActions
    ├── notification/               通知一覧 (NotificationList / NotificationItem)
    ├── login/                      サーバ URL + トークン入力 (暫定。OAuth は後で)
    ├── navigation/                 画面遷移 (Route = 画面のキー / Navigator + LocalNavigator = 遷移の口 / QuasarNavHost = NavDisplay と
    │                               各画面の配線 / OverlaySceneStrategy = 重ねて出す画面 / Transitions = 横スライド)
    ├── settings/                   設定画面 (SettingsScreen = 各ページ) + LocalDisplaySettings
    ├── timeline/                   タイムライン画面 + ViewModel
    ├── profile/                    プロフィール (Screen / Header / ActionButton / ViewModel)
    ├── follows/                    フォロー / フォロワー一覧 (Screen / ViewModel)
    ├── mediaviewer/                メディアビューア (Entry / Screen / ViewModel / Args / ページ / DismissibleViewer)
    ├── hashtag/                    ハッシュタグのタイムライン (Screen / ViewModel)
    ├── post/                       ポスト詳細 (Screen / ViewModel / ReplyList)
    └── composer/                   投稿 (ComposerSession / DraftStore / ComposerScreen / QuickPostBar /
                                    QuickComposerHost / AttachmentUploads / MediaPickerHost)
```

レイヤ依存は `ui → domain ← data` の一方向。UI は DTO を直接触らない。

debug / benchmark ビルドだけに入るもの (`app/src/debug/`、release には入らない):

```
es.aria_on_the_planet.quasar.debug  DebugGalleryActivity / GalleryScenario (+ 表示設定の上書き) / GalleryFixtures
assets/gallery/                     固定データ (API と同じ形の JSON + 画像)。scripts/gallery/generate.py が生成
```

`scripts/gallery/generate.py` (ギャラリーの固定データの生成)、`scripts/perf/measure.sh` (描画の負荷の測定)。

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
    val iconStaticUrl: String = iconUrl, // Account.avatar_static (アニメーションを止めるとき用)
)

// User は一覧表示に必要な最小限。プロフィール画面用の詳細は Profile に分ける
// User.fullScreenName: ローカルユーザーにも所属ドメインを付けた username@domain

data class Profile(
    val user: User,
    val headerUrl: String?,        // header。未設定時の .../missing.png は null にする
    val headerStaticUrl: String?,  // header_static
    val postsCount: Int,           // statuses_count
    val followingCount: Int,       // following_count
    val followersCount: Int,       // followers_count
    val fields: List<ProfileField>,
    val createdAt: Instant?,
    val locked: Boolean,
    val bot: Boolean,
)

data class ProfileField(val name: String, val value: String /* HTML */, val verifiedAt: Instant?)

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
    val poll: Poll?,               // poll
    val quote: Quote?,             // quote (Quote / ShallowQuote の両方を QuoteDto で受ける)
    val application: PostApplication?, // application (name, website)。リモートのポストでは null
    val contentWarning: String?,   // spoiler_text (空なら null)
    val sensitive: Boolean,        // sensitive (メディアの閲覧注意)。CW とは独立
    val visibility: PostVisibility, // visibility (Public / Unlisted / Private / Direct)。未知の値は Public
    val viewer: PostViewerState,   // 自分がこのポストに行った操作
    val boostedBy: User?,          // ブーストの場合、ブーストしたユーザー
    val statusId: String,          // 本文を持つ Status の ID (ブーストなら中身)。詳細画面・スレッドはこれを使う
    val inReplyToId: String?,      // in_reply_to_id (中身の値)
    val mentions: List<Mention>,   // mentions (id / username / acct / url)。返信の宛先・本文のメンションのタップに使う
    val tags: List<Hashtag>,       // tags (name / url)。本文のハッシュタグのタップに使う
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

data class Poll(
    val id: String, val expiresAt: Instant?, val expired: Boolean, val multiple: Boolean,
    val votesCount: Int, val votersCount: Int?, val options: List<PollOption>, val emojis: List<CustomEmoji>,
    val voted: Boolean, val ownVotes: List<Int>,   // voted / own_votes は認証時のみ。未認証は false / 空
)
data class PollOption(val title: String, val votesCount: Int? /* 結果非公開中は null */)

data class Quote(val state: QuoteState, val post: Post?)   // post は accepted 等で quoted_status がある時のみ
enum class QuoteState { Pending, Accepted, Rejected, Revoked, Deleted, Unauthorized, BlockedAccount, BlockedDomain, MutedAccount }
// 未知の state は Unauthorized 扱い (API ドキュメントの指示)

enum class MediaType { Image, Video, Gifv, Audio, Unknown }

data class MediaAttachment(
    val id: String,
    val type: MediaType,
    val url: String?,              // url (処理中は null。リモートのメディアは接続中サーバのキャッシュ)
    val remoteUrl: String?,        // remote_url (元サーバの原本。ローカルなら null)
    val previewUrl: String?,
    val meta: MediaMeta?,          // original / small のサイズと duration だけを取り出す
    val blurhash: String?,
    val description: String?,      // 代替テキスト
) {
    val viewUrls: List<String>     // 表示・再生の候補 [url, remote_url]。読めなければ次を試す
    val originalUrl: String?       // 保存・ブラウザで開く用 (remote_url ?: url)
}

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

| 用途 | エンドポイント |
| --- | --- |
| プロフィール | `GET /api/v1/accounts/:id` |
| プロフィールの投稿一覧 | `GET /api/v1/accounts/:id/statuses` (初回 `limit=15`、以降 40) |
| ピン留めポスト | `GET /api/v1/accounts/:id/statuses?pinned=true&limit=20` |
| メディア一覧 | `GET /api/v1/accounts/:id/statuses?only_media=true` (40 件ずつ。ブーストは含まれない) |
| フォロー / フォロワー一覧 | `GET /api/v1/accounts/:id/following` / `followers` (**Link ヘッダ**でページング。下記) |
| 相手との関係 | `GET /api/v1/accounts/relationships?id[]=:id` (要認証) |
| フォロー / 解除 | `POST /api/v1/accounts/:id/follow` / `unfollow` (要認証。鍵アカウントは `requested` になる) |
| 自分がフォローしている人のうちのフォロワー | `GET /api/v1/accounts/familiar_followers?id[]=:id` (要認証) |
| 通知 (グループ) | `GET /api/v2/notifications` (要認証、Mastodon 4.3+、既定) |
| 通知 (個別) | `GET /api/v1/notifications` (要認証、max_id ページング。`NotificationApi.Ungrouped` 時) |
| ハッシュタグのタイムライン | `GET /api/v1/timelines/tag/:hashtag` (max_id ページング) |
| ポスト | `GET /api/v1/statuses/:id` |
| 投票する | `POST /api/v1/polls/:id/votes` (form `choices[]`, 要認証) |
| ブースト / 取り消し | `POST /api/v1/statuses/:id/reblog` / `unreblog` (要認証) |
| お気に入り / 取り消し | `POST /api/v1/statuses/:id/favourite` / `unfavourite` (要認証) |
| ブックマーク / 取り消し | `POST /api/v1/statuses/:id/bookmark` / `unbookmark` (要認証) |
| スレッド | `GET /api/v1/statuses/:id/context` (未認証では祖先 40・子孫 60・深さ 20 まで) |
| 投稿 | `POST /api/v1/statuses` (form、`Idempotency-Key` 付き、要認証) |
| 編集用の原文 | `GET /api/v1/statuses/:id/source` (本文・CW のプレーンテキスト、要認証) |
| 編集 | `PUT /api/v1/statuses/:id` (form。公開範囲・返信先は送らない。代替テキストは `media_attributes[][id/description]`) |
| 削除 | `DELETE /api/v1/statuses/:id` (要認証) |
| ログイン中のアカウント | `GET /api/v1/accounts/verify_credentials` (要認証。`source.privacy` が既定の公開範囲) |
| メディアのアップロード | `POST /api/v2/media` (multipart、要 `write:media`)。動画等は 202 で処理待ち → `GET /api/v1/media/:id` で `url` が入るまで待つ |
| 代替テキストの更新 (投稿前) | `PUT /api/v1/media/:id` (`description`) |
| カスタム絵文字 (補完・ピッカー) | `GET /api/v1/custom_emojis` (認証不要。`EmojiRepository` がプロセス内でキャッシュ、`visible_in_picker` のみ) |
| サーバ情報 (上限・ストリーミング URL) | `GET /api/v2/instance` (`InstanceRepository` がプロセス内でキャッシュ) |

`:id` は接続中サーバ内の ID (`User.inServerId`)。リモートユーザーでも接続中サーバに問い合わせる。

### PagedFeed / PostFeed (`ui/feed/`)
`PagedFeed<R, T>` は max_id ページングの状態 (`FeedState<T>`: items / isRefreshing / isLoadingMore / endReached / error /
loaded / nextMaxId) と `refresh()` / `loadMore()` / `updateItems()` をまとめた汎用クラス。`R` は API の 1 件、`T` は表示の
1 件で、`append(current, page)` (純粋関数) で一覧を組み立て、`nextMaxIdOf(page)` で取得した生ページから次の `max_id` を
決める。`appendDistinct` は重複を捨てる標準の append。`PostFeed` はそのポスト版 (`PostFeedState = FeedState<Post>`、
`.posts` は `.items` の別名)。通知は `PagedFeed<NotificationGroup, NotificationEntry>`。
`max_id` が項目の ID ではない一覧 (フォロー / フォロワー: `max_id` はフォロー関係の ID で、Link ヘッダの `rel="next"` で
渡される) は、主コンストラクタの `fetchPage` で `Page(items, nextMaxId)` を返す (`nextMaxIdFromLinkHeader`、
`MastodonApi.accountRelations`)。`nextMaxId == null` で終端。`ensureLoaded()` はタブを初めて開いたとき用。

## 再利用 UI コンポーネント

### EmojiText (`ui/component/text/EmojiText.kt`)
アカウント名・ポスト本文・プロフィール文はカスタム絵文字 `:shortcode:` を含みうるため、**`Text` を直接使わず必ず `EmojiText` を使う**。

* `EmojiText(text: String, emojis: List<CustomEmoji>, isHtml: Boolean, ...)`
  * `isHtml = true` のとき Mastodon の HTML を `AnnotatedString.fromHtml` で変換 (リンク付き)。
  * `:shortcode:` を `emojis` と照合し、`InlineTextContent` + Coil の `AsyncImage` で画像に置換。
  * `onLinkClick` を渡すとリンクのタップを横取りする (引数は href とリンクの文字列、未指定なら `LocalUriHandler` で開く)。
    Compose のリスナーにはリンク (href) しか渡らないので、文字列は変換後の AnnotatedString から引いている。
  * `postContent = true` はポストの中身 (本文・CW・投票・通知の抜粋)。絵文字のアニメーションの設定が「本文」と
    「それ以外」(表示名・bio・フィールド等、既定の `false`) で分かれているため。
  * 絵文字の高さは文字スタイルの行の高さ (`lineHeight`、未指定なら 1.2em) に合わせ、幅は画像の縦横比から決める
    (横長の絵文字は横長に表示)。API はサイズを返さないため、画像読み込み時に `EmojiAspectRatios` (URL ごとの
    アプリ全体キャッシュ) へ記録し、記録されると再レイアウトする。未読み込みの間は正方形。比率は 1/4〜8 に丸める。
* 変換処理 (`replaceEmojiShortcodes`, `simplifyMastodonHtml`) は純粋関数として分離し、ユニットテスト可能にする。

### RemoteImage / LocalImage (`ui/component/image/`)
画像は**`AsyncImage` を直接使わず必ず `RemoteImage` (サーバ上の画像) / `LocalImage` (投稿欄の添付等、端末内) を使う**。
Coil を触るのは `ui/component/image/` の中だけ、apng-drawable (`com.linecorp.apng`) は `loader/ApngDecoder.kt` と `loader/ApngAnimationDrawable.kt` だけ。

* `RemoteImage(url, contentDescription, kind: ImageKind, staticUrl, placeholder, error, contentScale, onLoaded(Size), onError)`。
  Coil の API は包み切らず、使っているものだけ通す (`onLoaded` は画像の本来の大きさ、別 URL の再試行は呼び出し側)。
* アニメーション (GIF / APNG / Motion WebP) を動かすかは `DisplaySettings.animates(kind)`。`ImageKind` は PostEmoji /
  OtherEmoji / Avatar / Other。止めるときは `staticUrl` (`static_url` / `avatar_static` / `header_static`) があればそれを読み、
  無ければ先頭 (既定) のフレームを静止画で出す。
* 仕組み (`loader/`):
  * `newQuasarImageLoader` が ImageLoader を作る。**ServiceLoader の自動登録は切っている** (`serviceLoaderEnabled(false)`)。
    自動登録された coil-gif は下記の止める仕組みを通らずアニメーションさせてしまうため。コンポーネントは全部ここで並べる。
  * リクエストの `animate(enabled)` (Extras + メモリキャッシュのキー) を `AnimationGate` が見て、止めるならアニメーション用の
    デコーダを飛ばす (Coil 標準のデコーダが静止画で読む)。メモリキャッシュのキーを分けないと、切り替えても前の結果が出る。
  * GIF / Motion WebP は coil-gif の `AnimatedImageDecoder` (Android の ImageDecoder)。APNG は ImageDecoder がアニメーション
    しないので `ApngDecoderFactory` (apng-drawable)。APNG の判定は `readApngInfo` (純粋関数、`ApngHeaderTest`): `acTL` が
    最初の `IDAT` より前にあれば APNG。拡張子・Content-Type は普通の PNG と同じなので見ない。2 フレーム未満は静止画扱い。
  * apng-drawable は全フレームを先に展開する (ネイティブ)。`newDrawable()` / `mutate()` (Coil が描く前に呼ぶ) は内部で
    `Apng.copy()` を呼ぶが、フレームは共有され、コピーごとに増えるのは 1 フレーム分の Bitmap だけ (96x96・30 フレームで
    全体 1.1MB に対し 1 回あたり約 130KB と計測)。展開後 16MB を超える APNG はアニメーションさせない。同じ画像を
    何度も展開しないよう `ApngFrameCache` (URL = `diskCacheKey` ごと、32MB の LRU) に元を持ち、表示には
    `ConstantState.newDrawable()` を返す。元は描かない。追い出しても `recycle()` しない (表示中の別インスタンスがありうる)。
  * **再生は `ApngAnimationDrawable` が行い、`ApngDrawable` を `start()` しない**。`ApngDrawable` は再生中ずっと毎フレーム
    `invalidateSelf()` し、描くたびにネイティブでコマを Bitmap へ書き出すので、14〜50fps の APNG でも 120Hz で描き直し続ける
    (実機のピッカーで 124fps・1 コアの 18%)。自前の Drawable で、コマの位置は純粋関数 `ApngTimeline` (`ApngTimelineTest`。
    10ms 以下のコマは GIF と同じく 100ms 扱い) で求め、コマが変わる時刻にだけ `scheduleSelf` する (同じ場面で 78fps・12.5%)。
    コマの指定は `seekTo(開始時刻)`。**`seekToFrame` は `loopCount = 0` (無限) で例外を投げる** (ライブラリの不具合) ので使わない。
    `Apng` クラスは Kotlin の `internal` で参照できないため、`ApngDrawable` を「再生させない入れ物」として使っている。
  * フレームレート上限: `DisplaySettings.emojiAvatarAnimationMaxFps` (既定 30、`null` で上限なし。設定画面で変える) を
    `DisplaySettings.animationMaxFps(kind)` が絵文字・アイコンにだけ渡し (添付メディア等は上限なし)、リクエストの Extras
    (`animate(enabled, maxFps)`) → `ApngDecoderFactory` → `ApngAnimationDrawable` と届く。次の描き直しの時刻を
    `nextDrawTime` (純粋関数) で時刻 0 起点・`1000 / maxFps` ms の共通の刻みに切り上げるので、1 つあたり上限以下になり、
    同じ画面の APNG の描き直しも同じフレームにまとまる。**APNG のみ** (GIF / WebP は上記のとおり Coil 任せ)。
  * メモリ不足: `ApngFrameCache` は `ComponentCallbacks2` (`newQuasarImageLoader` で登録)。Coil のメモリキャッシュと同じく
    `TRIM_MEMORY_BACKGROUND` 以上 / `onLowMemory` で空にする (logcat の `ApngFrameCache` に空けた量)。表示中のコピーはフレームを
    共有しているが、空けた後も再生・展開し直しとも問題ないことを実機で確認済み (`am send-trim-memory <pkg> BACKGROUND`)。
  * Bitmap 以外 (アニメーション) の結果は Coil のメモリキャッシュに入らない (1 つの Drawable は 1 箇所でしか再生できない
    ため)。GIF / WebP は表示のたびにデコードされるが、ImageDecoder は少しずつ読むので軽い。

### PostCard (`ui/component/post/PostCard.kt`)
ポスト 1 件の表示。タイムライン・プロフィール・詳細画面などで再利用する。

* `PostCard(post: Post, modifier, mediaHeight: Dp = PostCardDefaults.MediaHeight, onUserClick, onPostClick, onMediaClick)`
* リプライ表示: `inReplyToId` があるポストは、ブースト表示と同じ形 (アイコン + tertiary 色) で「Reply to @acct」を出す。
  acct は `in_reply_to_account_id` を、自分へのリプライなら投稿者、それ以外は `mentions` から引いて求める
  (見つからなければ「Reply」)。Post 画面のスレッドでは縦線で関係が分かるので `showReplyIndicator = false`。
* 本文のリンク: 行き先は純粋関数 `Post.linkTarget(href, text)` (`domain/model/Hashtag.kt`、`PostLinkTargetTest` / `MentionTest`)
  で決め、`rememberPostLinkHandler` (`PostLinks.kt`) が PostCard / PostDetail / QuotedPost の本文に渡す。
  * メンション: href と `Post.mentions` の `url` の一致 (`findByUrl`。Mastodon Web と同じ)。`class="mention"` は
    ハッシュタグにも付き、他実装では付き方が揃わないので見ない。タップで `onUserClick` (`Mention.asUser()` は ID・acct・
    サーバだけの `User`。プロフィールを開く用で表示には使わない)。url が一致しない実装 (本文が AP の id を指す等) はブラウザ。
  * ハッシュタグ: href と `Post.tags` の `url` の一致、またはリンクの文字列 `#名前` と `tags` の名前の一致 (大文字小文字無視)。
    `tags` の url は接続中サーバのページなので、他サーバ (Misskey 等) のポストは文字列の方で当たる。タップで
    `onHashtagClick(name)` (`onReply` と同じく PostList / NotificationList / PostScreen 等は中継するだけ。`null` ならブラウザ)。
  * それ以外はブラウザ。
* 公開範囲: 投稿者行 (名前 / @acct) と同じ高さの右端に `VisibilityIcon` (公式 Web UI を元に、公開・フォロワー限定は出さず、
  未収載=月、ダイレクト=手紙)。`PostDetail` も 2 段の名前 / @acct の右端 (縦中央)。引用 (QuotedPost) には出さない。
* 構成: (ブースト表示) / (リプライ表示) / アイコン + 表示名 (EmojiText) + screenName / 本文 (EmojiText) / 投票 (PollView) /
  添付メディア (MediaAttachments) / リンクプレビュー (LinkPreviewCard) / 引用 (QuotedPost) / アクション行
* `mode = PostCardMode.ReplyContext` は返信画面の上の返信先用: 本文 (CW・長文の折りたたみ込み) とメディアだけで、ラベル・
  公開範囲・投票・カード・引用・アクション行を出さず、カード全体のタップも受けない。`authorTrailing` は投稿者行の右端 (✕ 等)。
* `mediaHeight` はそのまま `MediaAttachments` に渡す (`PostList` も同名パラメータで中継する)。
* アクション行: リプライ数・ブースト数・リアクション数・ブックマーク。`viewer` のフラグが立っているものは
  塗りアイコン + 色付き (ブースト=extendedColors.reblog (緑), リアクション=extendedColors.favourite (黄),
  ブックマーク=secondary) で表示する。
  ブースト・お気に入り・ブックマークは押せる (`onAction`、未ログインなら `null` で表示のみ)。リプライは `onReply`
  (トグルではないので `PostAction` とは別の口。`PostList` / `NotificationList` / `PostDetail` 等は中継するだけ。「投稿」の「返信」)。
* `Post` 以外の状態を持たない (stateless)。

### PostList (`ui/component/post/PostList.kt`)
ポストの集合。タイムライン以外 (プロフィールの投稿一覧など) でも使う。

* `PostList(posts, onLoadMore, isLoadingMore, modifier, contentPadding, header, ...)`
* `LazyColumn` + `key = post.id`。末尾付近で `onLoadMore` を呼ぶ。
* データ取得には関与しない (呼び出し側の ViewModel が担当)。
* 末尾付近にいる間は項目数が増えるたびに `onLoadMore` を再度呼ぶ (1 ページが画面に収まる場合も続きを読む)。

### 相対時刻の更新 (`ui/component/time/Now.kt`)
* 「3m」等の相対時刻・投票の残り時間は `LocalNow.current` を現在時刻として計算する (`relativeTime(at, LocalNow.current)`、
  `timeLeft(expiresAt, LocalNow.current)`)。`Instant.now()` を直接使うと時間が経っても表示が進まない。
* `QuasarAppContent` が `rememberTickingNow()` で `NowTickIntervalMillis` (15 秒) ごとに進めて提供する。
  前面 (STARTED) の間だけ進め、前面に戻ったら即更新する。間隔はこの定数で調整する。

### 折りたたみ (`ui/component/layout/`)
* 開閉状態は `rememberExpandedState(key)` (statusId 等のキー付き rememberSaveable) で持ち、CW・長文・ピン留めで共通。
* 「上限を超えたら切ってフェード + 開閉ボタン」は `CollapsibleContent(collapsedMaxHeight, expanded, toggle)` に一本化。
  ボタンは差し替え式で、`ShowMoreToggle` (「Show more >」、CW・長文) と `ChevronToggle` (中央の `>` ボタン、ピン留め)。
* 長文 (`CollapsiblePostText`): PostCard の本文を `LocalDisplaySettings.current.longPostMaxLines` 行 (既定 8) で折りたたむ。
  本文は行の高さ固定 (カスタム絵文字も行の高さに揃えている) なので、N 行 = 行の高さ × N として `CollapsibleContent`
  に渡す。Post 画面の本体 (`PostDetail`) は全文を読む場所なので折りたたまない。
* CW は本文側を丸ごと差し替える (切るのではない) ので `CollapsibleContent` には乗せず、状態とボタンだけ共有する。

### Content Warning / NSFW
* CW (`ContentWarning.kt`): `spoiler_text` があるポストは、本文の位置に警告文 (EmojiText) と、その下に primary 色の
  「Show more >」を出す。タップで本文・投票・リンクプレビュー・引用を展開 (`>` は上向きに回り「Show less」)。
  展開状態は `rememberContentWarningExpanded(post)` (statusId ごとの rememberSaveable)。PostCard / PostDetail / QuotedPost 共通。
* NSFW: `sensitive` のポストのメディアは、初期状態でぼかし (BlurHash があればそれを表示し本体は読み込まない。無ければ
  `Modifier.blur`) + 右上に目アイコン。アイコン (またはぼかし中のメディア自体) のタップで切り替え。状態はメディアごと。
  `sensitive` でないポストでは目アイコンを出さない。CW とは独立 (CW で隠れるのは本文側のみで、メディアは sensitive に従う)。

### ブースト・お気に入り・ブックマーク (`PostAction`)
* ドメイン: `PostActionType` (Boost / Favourite / Bookmark) と `PostAction(type, undo)`。`type.toggleOn(post)` で
  押されたときの状態から「する / 取り消す」を決める (済みなら取り消し)。
* 流れ: `PostCard(onAction)` → `rememberPostActionHandler(perform, confirmation)` (`ui/component/post/PostActionHandler.kt`)
  → `ViewModel.performAction(post, action)` → `PostActionController` (`ui/feed/PostActions.kt`) → `StatusRepository.perform`。
  画面側は `rememberPostActionHandler(viewModel::performAction).takeIf { viewModel.canInteract }`。
* **確認モーダルの差し込み口**: `PostActionConfirmation.confirm(post, action): PostAction?`。既定は
  `rememberPostActionConfirmation()` で、設定 (`AppSettings.confirmations`) で確認する操作なら `LocalActionConfirmer` のダイアログで
  答えを待ち、`null` で取りやめ (下記「設定」)。操作を変えて返してもよい (将来ブーストの公開範囲を選ばせる等)。
  ViewModel 側は確認の有無を知らない。
* `PostActionController`: 見込みで反映 (`Post.withAction`) → 送信 → 応答の件数・状態で上書き (`Post.withInteractionsOf`)、
  失敗なら巻き戻し (`withAction(action.inverse())`) + `onError`。同じポスト・同じ種類が送信中の間は次の操作を無視する。
  反映は `statusId` 一致で、ブーストの外側・引用先も含めて、ViewModel が持つ全一覧 (全カラム + 通知、ピン留め、スレッド全体) に当てる。
  `withAction` は既にその状態なら何もしない (重ねても件数がずれない)。テストは `PostActionTest` / `PostActionControllerTest`。
* `reblog` の応答はブースト自体の Status (中身は `reblog`) だが、`toDomain` で中身の件数・状態が取り出される。
* フォロワー限定・ダイレクトのポストはブーストボタンをグレーアウトする (`PostVisibility.boostable`。既にブースト済みなら
  取り消しのため押せる)。自分のフォロワー限定ポストはサーバ上はブーストできるが、ログイン中のアカウントをまだ持っていない
  ので一律に不可としている。
* 失敗時の UI 通知 (スナックバー等) は無い (ログのみ)。
* 押下時のアニメーション: 押している間の縮小・オン時の弾み (motionScheme の spring)、件数の上下スライド、オン時に
  アイコンの後ろから粒が散る `Modifier.actionBurst` (`ActionBurst.kt`、clip より外側で描く)。

### PollView (`ui/component/poll/PollView.kt`)
* `PollView(poll, modifier, onVote: (suspend (List<Int>) -> Boolean)?)`。`PostCard` / `PostDetail` が本文の直後に置く。
* 結果表示: 終了済み・投票済み・`onVote == null` (未ログイン、引用内) のとき。割合バー (最多得票は primaryContainer +
  太字、0% でも細いバーを出す)・右端に割合 (10% 未満は小数 1 桁)・自分の選択にチェック。複数選択の分母は voters_count。
* 投票: 枠線のピル型ボタンを中央寄せで並べる。単一選択はタップで即投票、複数選択はトグルで選んで「Vote」。
  投票中はボタン無効、失敗時はエラー文言。
* フッタ: 「N votes · 6 days left」/「Final results」(複数選択は「N people」)。
* 投票処理は ViewModel の `suspend fun vote(post, choices)` (`ui/feed/PollVoting.kt` の `submitVote`)。
  viewModelScope 上で実行し、成功したら `Post.withPoll()` で各一覧 (引用先含む) の該当投票を差し替える。
  画面側は `onVote = if (viewModel.canInteract) viewModel::vote else null` で渡す。

### QuotedPost (`ui/component/post/QuotedPost.kt`)
* 角丸の OutlinedCard に、小さいアイコン (20dp) + 表示名 + @acct · 時刻 / 本文 / 投票 (結果のみ) / メディア (0.75 倍の高さ)。
  タップで引用元のポスト詳細を開く。カード (リンクプレビュー) とアクション行は出さない。
* 本文側 (ヘッダ・本文・投票) の高さは呼び出し側の `mediaHeight` (添付画像と同じ高さ) を上限とし、超える分は
  下端フェードで切る。メディアはこの上限とは独立して、本文の下に常に全体を表示する。
* 引用の引用はさらに入れ子にせず、プレースホルダ 1 行。
* 表示不可 (pending / deleted / blocked / muted / その他) は状態に応じた文言のプレースホルダ。
* 本文の引用フォールバック「RE: <リンク>」は `simplifyMastodonHtml` で除去する
  (Mastodon は `<p class="quote-inline">`、Misskey 由来は `<br><br><span class="quote-inline">`)。

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
* タップ: メディアのタップはメディアビューア (unknown は元ファイルをブラウザ)。video / gifv / audio は**中央の再生ボタンだけ**
  その場でミュート再生 (`LocalInlinePlayback`。**自動再生はしない**。帯域の細いサーバが多いため)、再生中のタップで再生位置ごとビューアへ。
  呼び出し側へは `onMediaClick: (MediaClick) -> Unit` (ポスト + メディア + 再生位置 + 一覧での各メディアの位置) で伝え、
  `Navigator.openMedia` に渡す。位置はビューアを上下スワイプで閉じるときに縮んで戻る先。
* 再生・ビューアの詳細は [docs/media-viewer.md](docs/media-viewer.md)。
* `sensitive` (閲覧注意) のぼかしと目アイコンは「Content Warning / NSFW」を参照。

## 画面

* **画面遷移** (`ui/navigation/`): Activity は MainActivity 1 つで、画面の行き来は Navigation 3 (`QuasarNavHost` の `NavDisplay`)。
  計画と経緯は [docs/navigation3-migration.md](docs/navigation3-migration.md)。
  * 画面のキーは `Route` (`@Serializable`。引数は開き直すのに要る最小限 = ID 等。`rememberNavBackStack` がプロセスの再生成でも
    復元する)。画面からは `Navigator` (`openProfile` / `openPost` / `openMedia` 等、`LocalNavigator`) で遷移する。
    各画面 (`ProfileScreen` 等) はコールバックで遷移を受け、`QuasarNavHost` が `Navigator` から組み立てる。
  * ViewModel は画面 (エントリ) ごと (`rememberViewModelStoreNavEntryDecorator`)。下に積まれた画面はコンポジションから外れる
    (状態は `rememberSaveable` で残る)。`LaunchedEffect` / `repeatOnLifecycle` で動くもの (ストリーミング・インライン再生) は、
    別の画面を開くと止まり、戻ると再開する。
  * ログイン状態でバックスタックを作り直す (ログイン → `[Timeline]`、ログアウト → `[Login]`)。書きかけの投稿 (`DraftStore`) は
    `QuasarNavHost` がセッションごとに開く。
  * 遷移は横スライド (`Transitions.kt`、AOSP の設定画面と同じ。予測型「戻る」も同じ動き)。メディアビューアだけは
    `OverlaySceneStrategy` で下の画面に重ね、出入りは自分で描く。
  * 画面の中の `NavigationBackHandler` (投稿の破棄確認・クイック投稿バーのフル画面・ビューア) は `NavDisplay` より先に「戻る」を受ける。
* **Profile** (`Route.Profile(accountId)`)。
  * X のプロフィールを参考にした構成: ヘッダ画像 + 半分重なるアイコン (UserIcon, 88dp) / 表示名 (EmojiText) +
    鍵・bot バッジ / `@username@domain` / bio (EmojiText, HTML) / フィールド / 登録日 / 投稿・フォロー・フォロワー数。
  * フィールドは角丸の面に key: value で並べ、`verified_at` があるものは tertiary 色 + 認証アイコン。
  * 件数は 1 万以上で短縮表記 (`CompactDecimalFormat`)。
  * ヘッダ部分は `PostList` の `header` スロットに入れ、投稿一覧と 1 本の LazyColumn にする。
    ヘッダが画面外に出るとトップバーに表示名と投稿数を出す。
  * フィールドの認証アイコン枠は常に確保し、認証有無で key/value の列位置がずれないようにする。
  * **ピン留めポスト**: `GET /api/v1/accounts/:id/statuses?pinned=true`。サーバによっては多いので 20 件で打ち切る
    (`AccountRepository.MaxPinnedPosts`)。ヘッダ直下に `PinnedPosts` として 1 つの LazyColumn アイテムで置く
    (通常一覧にも同じポストが含まれうるため、キー衝突を避ける)。セクション見出しは置かず、各ポストに
    `PostCard(pinned = true)` で小さな Pinned ラベル (12dp アイコン + labelSmall) を付ける。画面高の 30% を超える場合は `CollapsibleContent`
    で折りたたみ、下端フェード + `>` (下向き、展開時は上向き) ボタンで展開する。
  * **自分がフォローしている人のうちのフォロワー** ("followers you follow"): `GET /api/v1/accounts/familiar_followers?id[]=:id`
    (要認証。未ログインなら呼ばない)。統計行の下に、重ねたアイコン最大 3 つ + 「Followed by A, B, and N others you follow」。
  * ピン留め・フォロワー情報は補助情報 (`ProfileExtras`) 扱いで、取得失敗時はログのみで非表示にする。
  * **フォローボタン** (`ProfileActionButton`): ヘッダ画像の下・アイコンの右 (X と同じ位置) に置く。未ログイン・関係の取得前 / 失敗は
    出さない。自分 (`fetchMe` の ID と一致) なら「Edit profile」(押しても何もしない。編集画面は未実装)。他人は
    `Relationship.followState` (Relationship エンティティの `following` / `requested` / `blocking` / `blocked_by`) で決める:
    * NotFollowing: Follow (塗り)。相手からフォローされていれば Follow back、**鍵アカウントなら Request to follow** (リクエスト送信)。
    * Requested (リクエスト送信済み・承認待ち。リモートの応答待ちでも立つ): Requested (枠線)。押すとリクエストの取り下げ (`unfollow`)。
    * Following: Following (枠線)。押すと解除。
    * Blocking / BlockedBy: 押せないボタン (ブロック解除は未実装)。
    フォロー・リクエスト送信・解除・取り下げはどれも `ConfirmDialog` で確認してから `ProfileViewModel.setFollowing`。
    成功したら応答の関係で置き換え (鍵アカウントへの follow は `requested = true` で返る)、フォロワー数を手元で ±1。失敗はログのみで元に戻す。
  * @acct の横に相手 → 自分の関係のラベル: `requested_by` (Mastodon 4.1+) なら「Requested to follow you」、`followed_by` なら「Follows you」。
    リクエストの承認・拒否は未実装。
  * **構成**: `CollapsingHeaderLayout` で 上からヘッダ (`ProfileHeader`) / タブ (`ConnectedTabRow`) / `HorizontalPager`
    (タブごとの `PostList`、それぞれ別の LazyListState)。一覧を上へスクロールするとまずヘッダが畳まれ (nested scroll の
    pre-scroll)、タブは上端に残る。下へは一覧が先頭に着いてからヘッダが開き、開き切った余りが pull-to-refresh へ回る。
    ヘッダ自体の上でのドラッグも同じ扱い (`scrollable` で拾って nested scroll に流す)。
    一覧はタブの裏から描き (`pinnedOverlapsContent = true`、一覧の先頭にタブの高さ分の余白)、タブの背景は Haze のぼかし
    (ページャが `hazeSource`、`backgroundColor` 必須は他と同様)。表示中のページの一覧が先頭から動いていない間は不透明な
    surface を重ねて今までどおりに見せ、スクロールしたらそれを消してぼかしにする。トップバーは不透明のまま。ヘッダが畳まれ切ったらトップバーに名前
    (`DetailScaffold(titleRevealed = ...)`)。ヘッダの畳み具合は両タブで共有し、タブごとのスクロール位置とは同期しない (X と同じ)。
  * **タブ** (Posts / Media): ボタンでも左右スワイプでも切り替わる (`pagerState.targetPage` を `selectTab` に渡す)。
    表示中のタブをもう一度押すとその一覧を先頭へ (`scrollToTop`)。ヘッダは開かない (畳み具合は nested scroll でしか
    動かず、プログラムからのスクロールは届かない。開くなら `CollapsingHeaderState.offset` を別に 0 へ動かす)。Media は
    `only_media=true` で絞り込んだポストの一覧 (別の `PostFeed`。初めて開いたときに読む) を同じ `PostList` で出すだけ。
    ピン留めは Posts のみ。投票・ブースト等の反映は両方の一覧に当てる。
  * フォロー数・フォロワー数のタップで `openFollowList(accountId, tab)`。
  * アイコン・ヘッダ画像のタップでメディアビューア (`openMediaViewer(MediaViewerArgs.singleImage(...))`、1 枚ずつで左右スワイプ無し)。
    ID は合成 (`avatar:<id>` / `header:<id>`)、読み込み中は static の URL を出す。開く前にピン留めポストのインライン再生を止める。
  * 未実装: 返信のタブ、familiar followers の一覧画面、プロフィール編集。
* **ハッシュタグ** (`Route.Hashtag(name)`、`#` 無しの名前)。
  `GET /api/v1/timelines/tag/:hashtag` (max_id ページング、`TimelineRepository.fetchHashtag`) を `PostFeed` で読み、
  `DetailScaffold` (タイトル `#名前`、PTR) + `PostList`。ストリーミングは受けない。返信はプロフィールと同じく投稿画面 (`Route.Composer`)。
  同じタグのタップでは遷移しない。未実装: タグのフォロー (`POST /api/v1/tags/:id/follow`)・利用状況 (`history`)、
  複数タグ (`any[]` / `all[]`)、`local` / `only_media` の絞り込み。
* **フォロー / フォロワー一覧** (`Route.FollowList(accountId, tab)`)。
  トップバー (表示名 + @acct) の下に `ConnectedTabRow` (Following / Followers) + `HorizontalPager` (スワイプでも切り替え)。
  各タブは開いたときに読み込み、PTR と追加読み込みあり。行は `UserList` (アイコン / 名前 / @acct / bio 3 行)。タップでプロフィール。
  一覧を隠しているアカウント (`hide_collections`) は空で返るので「Nothing to show」。行ごとのフォローボタンは未実装 (`trailing` スロットのみ)。
  * 注: API の Profile エンティティ (`/api/v1/profile`) はログイン中ユーザー自身の編集用 (raw な値) で、
    他人のプロフィール表示には Account を使う。ドメインの `Profile` は表示用で、API の Profile とは別物。
* **Post** (`Route.Post(statusId)`、ブーストなら中身の ID)。
  * 構成: 祖先 (古い順、PostCard をアイコン位置の縦線で連結) / 本体 (`PostDetail`) / リプライ一覧。
    初回表示時は本体ポストまでスクロールする。
  * `PostDetail`: PostCard の全要素 + 大きめの本文・メディア高さ 240dp + 詳細日時 + 区切り線付きアクション行。
    詳細日時は `yyyy MM/dd HH:mm:ss` (システム設定が 12 時間なら `yyyy MM/dd h:mm:ss a`)。
    日時の隣に投稿アプリ (`Status.application`) を「日時 · アプリ名」で出し、`website` があればリンクにする。
    application は他サーバからのポストでは返らないため、表示されるのは主にローカルユーザーのポスト。
  * リプライ (`ReplyList.kt`): ツリーにはせず、API (`descendants`) の順のまま一列に並べる。インデント・折りたたみは無し。
    直前の行へのリプライが続く場合はアイコン位置の縦線で繋ぎ (X と同様)、繋がらない行の間は区切り線。
  * トップバーは「Post」、本体が画面外に出ると投稿者名 + `@acct` に切り替わる。
  * **自分のポストの編集・削除**: 本体が自分のポスト (`fetchMe` の ID と投稿者が一致) なら、`PostDetail(menu = ...)` で投稿者行の
    右端に︙ (`OwnPostMenu`: 編集 / error 色の削除)。
    * 編集: `PostViewModel.startEdit` が原文 (`/source`) を取り、編集用の `ComposerSession` (`EditTarget`、`AppContainer.composerSession`)
      を作る。**`DraftStore` の下書きとは別**で、ポスト画面の上に `ComposerScreen` を重ねる。中身は純粋関数 `editDraftOf`
      (`PostEditTest`): 添付は投稿済みのメディアを Uploaded 扱い、投票の期間は残り時間に最も近い選択肢 (編集で数え直しになる)。
      公開範囲は変えられない (`Composer(visibilityEditable = false)`)。送信は Save (`toEditFormParams`、`DraftMappersTest`)、
      保存できたら本体を差し替えて閉じる。× / 戻るは変更があれば破棄の確認 (`ComposerSession.hasChanges`)。
      投稿済みのメディアは `PUT /api/v1/media/:id` で変えられないので、編集中の代替テキストは編集の送信でまとめて送る。
    * 削除: 確認の後 `PostViewModel.delete`、成功したら画面を閉じる。他の画面の一覧からは消さない (ストリーミングの
      `delete` が届けば消える)。
    * 失敗はスナックバー。
* **DetailScaffold** (`ui/component/scaffold/`): Profile と Post で共通の骨組み。戻る付き TopAppBar、
  pull-to-refresh、`revealTitleAfterIndex` 番目のアイテムが画面外に出たら `title` → `revealedTitle` に切り替え。
  補助として `DetailTitle` (名前 + 補足の 2 段)、`DetailLoading`、`DetailMessage` を持つ。
* 遷移: `PostCard` のアイコン・表示名 (+ screenName)・「〜 boosted」ラベルのタップで `onUserClick`。
  タイムラインとプロフィールの両方から `openProfile` する (プロフィール中の本人の投稿からは遷移しない)。
  ポスト本体のタップ (`onPostClick`) はタイムライン・プロフィール (ピン留め含む)・ポスト詳細から `openPost` する。
* 既知の挙動: Compose はテキスト内リンクのタッチ判定を最小 48dp に広げるため、本文先頭のリンクの直上
  (投稿者行・時刻) をタップするとリンクが開くことがある。
* **メディアビューア** (`Route.MediaViewer(MediaViewerArgs)`、`Navigator.openMedia(MediaClick)`)。下の画面に重ねて出す (`OverlaySceneStrategy`)。
  同じポストのメディアを左右スワイプ、上下スワイプ (ゴムのように引っかかり、離すと一覧の元の位置へ縮んで戻る。下の画面が透ける)、
  右上メニュー (保存・ブラウザで開く。保存は DownloadManager で `Pictures/Quasar/` 等へ、権限なし)。video / gifv / audio は Media3 で再生し、ダブルタップ ±10 秒・シークバー・「現在 / 全体」・ミュート。
  設計・PiP への準備・注意点は [docs/media-viewer.md](docs/media-viewer.md)。

* **Login (暫定)**: サーバ URL とアクセストークン (任意) を入力。トークンなしでは Home を無効化。
  OAuth (`/api/v1/apps` + `/oauth/authorize`) は後続タスク。
* **カラム** (`ColumnType`): Home / Notifications / Local / Global。Home と通知はログイン時のみ。
  4 つでは等幅にラベルが収まらないため、選択中のボタンだけラベルを出して幅を広げる (他はアイコンのみ)。
* **通知カラム** (`ui/notification/`): `NotificationGroup` 単位の一覧。
  * 取得 API は設定 (`AppSettings.notificationApi`) で切り替え、`NotificationRepository.fetch(session, api, ...)` に渡す。既定は `Grouped` (`GET /api/v2/notifications`、Mastodon 4.3+、
    サーバが favourite / reblog / follow を「同じ種類・同じ対象・近い時間帯」でまとめる)。`Ungrouped` は v1 で、
    各通知を 1 件のグループ (`Notification.asGroup()`) にする。切り替えたら通知を読み直す。サーバのバージョン確認は未実装。
    グルーピングの仕様はサーバ任せとし、クライアント側で独自にまとめることはしない。
  * 一覧の行は `NotificationEntry` (`Single` = サーバ側グループ 1 つ / `AccountReactions` = 1 人のユーザーが複数ポストに
    した同じ種類のリアクションをクライアント側でまとめたもの「〇〇 favourited 3 of your posts」)。
  * 一覧の組み立ては純粋関数 `appendNotificationPage(current, page)` (`ui/notification/NotificationGrouping.kt`) が行い、
    `PagedFeed(append = ...)` から呼ぶ。refresh は空の一覧に足す。手順:
    1. ページ境界で分かれたサーバ側グループの続き (同じ `group_key`) は既存行の中で `NotificationGroup.mergeOlder` で統合
       (件数は総数なので足さない、アカウントは和集合)。
    2. 既存の行のうち下から 2 番目までを固定部分、最後の行 (分解) + 新しいページを再評価部分とする。
    3. 再評価部分のリアクションのユーザーが固定部分にいれば、ユーザー単位の行へ吸収 (位置は変えない)、または単体の行と
       合わせて閾値以上なら最も新しい位置に作り直す (他の単体の行は削除)。
    4. 残りは再評価部分の中だけでまとめる。閾値 (`AccountGroupingThreshold` = 3 ポスト) 未満はまとめない。
    * 対象は favourite / reblog (別々に数える) で、サーバ側グループが 1 人・1 件・ポスト付きのもの。複数人がまとまった
      サーバ側グループには手を付けない。時間の窓は設けていない (同じユーザーなら古い分も同じ行に入る)。
    * 変更時は `NotificationGroupingTest` を必ず更新する (キーの一意性・取りこぼし無しの不変条件テストあり)。
  * 次ページの `max_id` は表示上の並びではなく、取得した生ページの最古 ID (`nextNotificationMaxId`、各グループの
    `page_min_id` の最小) から決める (`FeedState.nextMaxId`)。並び替えで末尾が最古でなくなっても正しく辿れる。
  * コンパクト表示ではアイコンを最大 6 つ並べ、「**A** and N others 〜」。行のタップは対象ポスト、個々のアイコンの
    タップはそのユーザーのプロフィール (グループのアカウント一覧画面は作らない)。
  * リアクション (favourite / reblog) とフォロー (follow / follow_request) はコンパクト表示 (x_notifications.png 参考):
    左に種別の塗りアイコン (お気に入り=extendedColors.favourite, ブースト=extendedColors.reblog, フォロー=extendedColors.follow)、右に**した側**のアイコン →
    「**名前** 〜しました · 時刻」(名前は HTML エスケープして `<b>`) → 対象ポストの抜粋 (onSurfaceVariant・最大 3 行、
    CW があれば警告文)。メディアがあれば右端に 56dp のサムネイル (閲覧注意は BlurHash のみ)。タップで対象ポスト。
  * mention は見出しを付けず PostCard だけ (本文に @ユーザー名 が入るため。Reply to も出さない)。
  * それ以外 (status / quote / update / poll 等) は「アイコン + 〜しました + 時刻」の見出しと PostCard。
  * 未対応の種別 (admin.* 等) は `Unknown` として汎用文言。
* **Timeline**: トップバーは無く、上端にヘッダ (`TimelineHeader`、Google マップの検索欄・チップ風) を浮かべる。
  中身は `PostList`。各タイムラインの状態は `TimelineViewModel` が `TimelineType` ごとに保持。
  * ヘッダ: 左にログイン中のアカウントのアイコン (`ComposerSession` が取得したものを使う。タップでメニュー: 名前・@acct・
    ログアウト。未取得 / 未ログインは汎用のアイコン)、右にカラムのタブ (Expressive の連結 `ToggleButton`)。
    連結ボタンは押下・選択で形が変わり、ボタンごとのぼかしは切り抜きを合わせられないので、ぼかしはタブ全体の後ろに
    形の変わらないカプセルを 1 枚敷く (クイック投稿バーと同じ `hazeState`。`backgroundColor` 必須は同様)。
    未選択のボタンは透明 (カプセルが見える)、選択中は primary で塗る。表示中のカラムのボタンをもう一度押すと一覧の先頭へ
    スクロールする (`LazyListState.scrollToTop`、`ui/component/list/`。遠いときは 10 件目まで飛んでから動かす)。
  * 一覧は画面の最上端から描き (edge-to-edge)、上端の余白はヘッダの高さ (ステータスバー込み) を `contentPadding` で取る。
    スクロールするとステータスバー・ヘッダの裏に入る。ステータスバーの部分には読みやすさのため薄いグラデーション
    (`StatusBarScrim`)。PTR のインジケータはヘッダの下から出す。
  * カラムは `HorizontalPager` で、左右スワイプでも切り替わる (ページごとに PTR・LazyListState を持つ)。吸着とボタンでの
    切り替えは `motionScheme.defaultSpatialSpec()` の spring。タブの幅はページ位置 (小数) から決め、指に追従する。
  * 読み込み: 指で引き出し始めたカラム / 落ち着いたカラムだけ `ensureLoaded`。ボタンで遠くへ飛ぶ時に通過するカラムは読まない。
    選択 (`viewModel.select`) はボタン押下時と `settledPage` の変化時。
  * ドラッグにゴムの抵抗を付けた版は `timeline-swipe-rubber-band` ブランチにある (60fps 出ていないように見えたので保留)。
  pull-to-refresh と追加読み込みに対応。読み込み中は `LoadingIndicator` を使う。

## 投稿 (`ui/composer/`, `ui/component/composer/`)

* **入口**: メイン画面とポスト画面の下のクイック投稿バー (`QuickPostBar`、ログイン時のみ。FAB は廃止)。ピルを上へ引っ張ると
  (または投票・CW のチップ / 添付の ALT のタップ)、同じ画面内でフルの投稿画面 (`ComposerScreen`) が下から伸びて開く。
  バー + フル画面 + 送信エラーのスナックバーは `QuickComposerHost` にまとめてあり、両画面はそれで一覧を包む
  (一覧は `hazeSource` を付け、下端の余白に引数の `barSpace` を足す)。
  単独の投稿画面 (`Route.Composer`、`Navigator.openComposer()`) はバーの無い画面 (プロフィール・ハッシュタグからの返信) 用。閉じても下書きは残す。
* **下書きの共有**: 書きかけの投稿は `ComposerSession` (入力 `ComposerState`・添付のアップロード・送信・上限・アカウント・絵文字)。
  `DraftStore` (`AppContainer.draftStore`) がアプリ全体で 1 つ持ち、バー・フル画面・単独の投稿画面が同じものを使う。
  session は自分のコルーチンの範囲 (`SupervisorJob` + Main) で動くので、画面を切り替えてもアップロードは止まらない。
  投稿・破棄で `DraftStore.finish()` → 空の下書きに替わる。アカウントが変わったら作り直す。プロセスが終わると消える (保存しない)。
  * 投稿できたら `DraftStore` 自身が `finish()` し、`DraftStore.published` (SharedFlow) に投稿を流す。同じ下書きを複数の画面
    (メイン画面とポスト画面) が表示しているので、後始末は画面ではなくここで 1 回だけ行う。画面はそれを受けてフル画面を閉じる・
    スレッドに足す (`PostViewModel.addPublishedReply`) だけ。
  * フル画面 (バーから): × / 戻るはバーに戻るだけ (下書きは残るので確認しない)。単独の投稿画面も同じ (閉じても下書きは残る)。
* **クイック投稿バー** (`ui/composer/QuickPostBar.kt`): 浮かぶ角丸の面で、後ろの一覧を Haze (`dev.chrisbanes.haze:haze-blur`) で
  ぼかす (一覧の `HorizontalPager` に `hazeSource`、バーに `hazeBlur(HazeInput.Sources(state))`)。
  * **ぼかしには `backgroundColor` (surface) が必須**: 一覧そのものは背景が透明 (Scaffold が塗っている) なので、下地が無いと
    ぼかした結果が透けて後ろの実物がくっきり見える。
  * 内容: ピル (引っ張って広げる) / 絵文字の候補 / 添付 (64dp) / 投票・CW のチップ (フル画面で編集) /
    アイコン + 本文 (最大 5 行) + 送信 / フォーカス中か入力があるときだけ「残り文字数・公開範囲 (アイコン)・絵文字・メディア」。
  * 引っ張る: ピルの縦ドラッグでバーが伸び、96dp を超えるか上へ速く払うと広げる。足りなければ戻る。
  * 入力していない間はバー全体がタップ領域 (どこを押しても本文にフォーカスしてキーボードを出す)。ボタンが受けたタップは
    ボタン優先、押せないもの (空の送信ボタン等)・アイコン・ピルのタップはバー全体に回る。ピルはドラッグのみ。
  * 一覧はバーの後ろまで描き (ぼかしの元)、下端の余白は `contentPadding` (ナビゲーションバー + バーの高さ + 余白) で取る。
    バーはキーボードの上に乗る (`WindowInsets.ime ∪ navigationBars`)。スナックバーはバーの上。
  * 「戻る」でキーボードだけ閉じられたらフォーカスも外して縮める (`WindowInsets.isImeVisible` が出ていた → 消えたとき)。
    公開範囲のメニュー・絵文字 / メディアのピッカー・OS 標準のピッカーは別ウィンドウ (別画面) に移ってキーボードが閉じるので、
    閉じてから少し待って、まだこの画面のウィンドウにフォーカスがある (`LocalWindowInfo.isWindowFocused`) ときだけ縮める。キーボードが出ていないときの「戻る」もフォーカスを外すだけ。バーからの投稿の結果・エラーはタイムラインのスナックバー。
  * メディアのピッカー (`rememberMediaPicker`、埋め込み / OS 標準) と絵文字の挿入 (`TextFieldState.insertEmoji`) はフル画面と共通。
* 画面 (フル): 下記は `ComposerScreen` の説明。
  X の投稿画面 (`x_posting.jpg`) を参考に、トップバー (× / Post)・アイコン + 本文・下端のツールバー
  (左端に残り文字数、右寄せで公開範囲 / 絵文字 / メディア / 投票 / CW)。
  アイコンの色はオン = primary、オフ = primary を背景色へ寄せた色、選べない (メディアと投票の排他等) = 標準の無効色
  (`ComposerIconColors`)。
  `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)` + `consumeWindowInsets` でツールバーをキーボードの直上に置く。
  入力があるときの「戻る」/ × は破棄の確認 (`NavigationBackHandler`)。成功したら閉じる (タイムラインにはストリーミングで載る)。
  失敗はスナックバー (Mastodon の `{"error": ...}` の文言) で、入力は残す。
* 部品は**クイック投稿バーでも組み直せるよう分離**している。どれも状態を持たず、値とコールバックだけを受ける:
  `ComposerAccountIcon` (`UserIcon` + 取得前のプレースホルダ) / `ComposerTextField` (枠無しの `BasicTextField` + `TextFieldState`) /
  `ContentWarningField` / `VisibilityButton` (メニューで選ぶ。`compact` でアイコンのみ) / `ComposerToolbar` (スロット) +
  `AttachMediaButton` / `PollButton` / `ContentWarningButton` / `CharacterCounter` / `AttachmentStrip` + `SensitiveMediaToggle` / `PollEditor`。
  `Composer` はそれらを X の配置で並べた全体のラッパー (高さいっぱいを使う前提。別の配置では部品を直接使う)。
* 状態は `ComposerState` (`TextFieldState` + snapshot state)。バー・フル画面では `ComposerSession` が持ち (共有)、単独で使うときは `rememberComposerState()`
  (`rememberSaveable`。添付は保存しない)。`toDraft()` で `PostDraft` にする。
* ドメイン: `PostDraft` / `PollDraft` / `DraftAttachment` (+ `UploadState`)。送信可否は純粋関数 `PostDraft.problems(limits)`
  (空・文字数超過・メディアと投票の併用・未アップロード・選択肢の数/空/長さ)。メディアと投票は同時に付けられない (Mastodon の制約)
  ので、片方があるともう片方のボタンを無効にする。
* 文字数は `countPostCharacters` (`PostLength.kt`): Mastodon の `StatusLengthValidator` に合わせ、CW + 本文、URL は一律
  `characters_reserved_per_url` (23)、`@user@domain` は `@user` 分、書記素単位。URL の判定は twitter-text より簡略化している。
* 上限 (`ComposeLimits`) は `InstanceRepository` から。取得前・失敗時は Mastodon の既定値 (500 字 / 4 枚 / 4 択)。
  `InstanceRepository` はサーバごとにプロセス内で 1 回だけ取得し (失敗はキャッシュしない)、ストリーミング URL もここから取る。
* 公開範囲・閲覧注意の初期値は `AccountRepository.fetchMe` (`verify_credentials` の `source`、セッションごとにキャッシュ)。
  取得前にユーザーが変えていたら上書きしない。
* **カスタム絵文字の補完**: カーソル直前の `:sho` (2 文字以上) で、ツールバー直上に候補の帯 (`EmojiSuggestionStrip`) を出す。
  タップで `:shortcode: ` に置き換える。フォーカスのある欄 (本文 / CW) が対象。
  * 純粋関数 (`Autocomplete.kt`、テストは `AutocompleteTest`): `findAutocompleteQuery` (検出。全角 `：` も可、`12:30` や
    英数字・かな直後の `:` では出さない)、`rankEmojis` (前方一致 → 部分一致、短い順)、`insertEmoji`、`findEmojiShortcodes`。
    `AutocompleteQuery` は引き金の文字を持つので、メンション・ハッシュタグの補完も同じ形に載せる想定。
  * 状態は `rememberEmojiSuggestions(field, emojis)` (`derivedStateOf` で `TextFieldState` に追従)。
  * 入力中の実在する `:shortcode:` は `OutputTransformation` (`EmojiShortcodeHighlight`、`TextFieldBuffer.addStyle`) で
    primary 色 + SemiBold にする (本文は変えない)。判定はサーバ (`CustomEmoji::SCAN_RE`) と同じ前後条件で、Ruby の
    `[[:alnum:]]` に合わせ、かな・漢字の直後も絵文字にならない。
  * `BasicTextField(TextFieldState)` は `InlineTextContent` 非対応のため、入力欄内で画像にはしない。
* **絵文字ピッカー** (`EmojiPicker.kt`): ツールバーの絵文字ボタンで `ModalBottomSheet` を開く (開くときにキーボードは閉じる)。
  `EmojiPicker` (検索欄 + `LazyVerticalGrid`) は入れ物に依存しない部品で、`EmojiPickerSheet` がシートに載せる。
  * 並びは `groupEmojisByCategory` (分類なし「Custom」が先頭、残りはカテゴリ名順、カテゴリ内はサーバの順)。検索中は
    カテゴリを外して `rankEmojis` の順。グリッド・補完候補の帯のアニメーションは「それ以外の絵文字」の設定
    (`ImageKind.OtherEmoji`) に従い、止めるときは `static_url`。
  * 選ぶと直前にフォーカスのあった欄 (本文 / CW) のカーソル位置へ `insertEmojiAtCursor` で挿入し (直前が英数字・かななら
    空白を前に足す)。シートは開いたままで続けて選べ、閉じたらその欄にフォーカスとキーボードを戻す。
  * 検索欄にフォーカスが来たらシートを全体に広げ、`imePadding` でキーボードの上に収める (半分の高さでは隠れる)。
  * 検索欄は `KeyboardType.Ascii` (`IME_FLAG_FORCE_ASCII`) で英字入力を求めるが、Mozc 系 IME (実機の Arte) は無視して
    かなのまま (`KeyboardType.Uri` でも同じ)。その場合はユーザーが英字に切り替える必要がある。
  * 未実装: 最近使った絵文字、長押しでのショートコード表示。
* **メディアの添付**:
  * 選ぶのは Photo Picker。**権限は宣言しない** (`READ_MEDIA_*` は使わない)。返る `content://` は一時的な許可なので、選んだらすぐ送る。
    * 埋め込み版 (`MediaPickerSheet`、`androidx.photopicker:photopicker-compose`。**alpha**): Android 14 + SDK 拡張 15 以上
      (`isEmbeddedPhotoPickerAvailable()`) で使う。`ModalBottomSheet` に載せ、最初は半分の高さ (ピッカーは簡易表示)、
      引き上げると全体表示 (タブ・Done バー付き) に切り替える (`isExpanded` をシートの状態に合わせる)。
      選択はその場で反映 (選ぶ → `addMedia`、外す → `removeMedia`。URI は `DraftAttachment.previewUri` で対応付け)。
      添付済みのものは `initialMediaSelection` で選択済みとして出す。Done (`onSelectionComplete`) で閉じる。
      半分の高さでは Done バーが画面外なので、閉じるのは下へのスワイプか外側のタップ (選択は反映済みなので問題ない)。
      セッションのエラー時は以後 OS 標準のピッカーに切り替える。alpha の API 変更はこのファイルで吸収する。
      見た目はピッカー側 (別プロセス) が描くので、アプリから変えられるのはアクセントカラーとダーク / ライトのみ。
      * アクセント: ピッカーはアクセントの上の文字色を輝度で決める (0.6 以上で黒、未満で白。AOSP の `AccentColorHelper`)。
        受け付けるのは輝度 0.05〜0.9。アクセントには primary をそのまま渡しており、ダークの primary (トーン 80) は 0.6 未満の
        ため選択中のタブが「明るい面に白文字」で読みにくい。ライブラリ (alpha) 側の挙動なので回避はしていない (回避するなら
        ダークで `primaryFixed` (トーン 90) を渡せば黒文字になることは確認済み)。`setThemeNightMode` は明示する
        (渡さないとダークのシートにライトの面が出る)。
      * タブ (Photos / Collections) が右に寄るのはピッカー側のレイアウト。シートを外して置いても同じだった (シートや絵文字
        ピッカーとは無関係)。AOSP main の `NavigationBar.kt` は左右対称なので、端末に入っている Photo Picker の版の問題と
        思われる。アプリからは直せない。
    * OS 標準版 (`PickMultipleVisualMedia` / 残り 1 枚なら `PickVisualMedia`): 埋め込み版が使えない端末。開き方 (高さ) は
      OS が決めるので調整できない。既に画像があれば画像だけを選ばせる。
  * 段取りは `AttachmentUploads` (`ui/composer/`、テストは `AttachmentUploadsTest`): 追加 → 送る前の検査 (`checkUpload`:
    `supported_mime_types`・`image_size_limit` / `video_size_limit`) → 送信中 (進み具合) → 変換待ち (`Processing`) → 完了 / 失敗。
    ✕ で送信を止める (サーバに残った分は投稿されなければサーバが消すので削除 API は呼ばない)。通信エラー (`UploadFailure.Failed`)
    だけタップで再送でき、権限なし (401/403 → `NotAllowed`)・拒否 (422 → `Rejected`)・上限超えは再送しない。
  * 代替テキスト (ALT): ダイアログ (`AttachmentDescriptionDialog`、上限は `description_limit`)。送る前に入れた分はアップロードに含め、
    送った後に変えたら `PUT` で反映 (送信中に変えた分は完了後に反映)。
  * 動画・GIF は他のメディアと一緒に付けられない (`DraftAttachment.isVideoLike`、`DraftProblem.VideoWithOtherMedia`)。
    動画があれば追加ボタンを無効にする。動画のサムネイルは `coil-video` (`VideoFrameDecoder`)。
  * `UploadSource` でデータ層を `ContentResolver` から切り離している (`AppContainer.uploadSource(uri)` が作る。IO スレッドで呼ぶ)。
  * アクセストークンに `write:media` が無いとアップロードは 403 になる (手入力のトークンで起きる。OAuth 実装時は scope に含める)。
  * 未実装: 画像の縮小・圧縮、焦点 (focus) の指定、カメラ撮影、プロセス再生成後の添付の復元。
* 二重投稿の防止: 同じ内容の再送は同じ `Idempotency-Key` を使う (内容を変えたら新しいキー)。
* フォームの組み立ては `PostDraft.toFormParams()` (`data/mapper/DraftMappers.kt`)。テストは `PostLengthTest` / `PostDraftTest` /
  `DraftMappersTest`。
* **返信**:
  * 返信先は下書きの一部 (`ComposerState.inReplyTo`)。**返信と通常の投稿で下書きを分けない** (アプリ全体で 1 つ)。
    返信ボタン (`onReply`) は `session.startReply(post)` + `QuickPostBarState.requestInput()` (バーにフォーカス)。
    返信中に別のポストの返信ボタンを押すと返信先を上書きし、本文は残す。プロフィール (バーが無い) は `startReply` → `Navigator.openComposer()`。
  * `startReply` は宛先 (`@作者 @メンション先 `、自分は除く) を本文に入れ、公開範囲を返信前の値と返信先の狭い方にし、返信先の CW を
    引き継ぐ。ユーザーが書いた本文・選び直した値は変えない。`cancelReply` (✕) は自動で変えたもののうち変えられていないものを
    返信前に戻す。計算は純粋関数 (`domain/model/Reply.kt`: `replyMentions` / `replyVisibility` / `startReply` / `cancelReply` /
    `isUntouched`、`ReplyDraftTest`)。`ReplyMemory` が「返信前の値」と「自動で入れた値」を持つ。
  * 表示: バーは返信中なら本文の上に `ReplyTargetPreview` (縮小・本文 2 行・メディア 48dp・✕)。フル画面は `Composer(header = ...)`
    に `ReplyContext` (X の返信画面の形: `PostCard(ReplyContext)` + 縦線 + 「Replying to @acct」 + 投稿者行の右端に ✕)。
    どちらも入口に関係なく `inReplyTo` だけで決まる。本文欄の文言は「Post your reply」。
  * **ポスト画面の自動の返信** (X と同じ): 前面に来たとき (`LifecycleResumeEffect`) 何も書いていなければ本体への返信にし
    (`startAutoReply`、`ComposerState.autoReply = true`)、離れるとき (pause) まだ何も書いていなければ外す (`releaseAutoReply`)。
    下書きを共有しているので、外さないとタイムラインのバーまで返信になる。返信ボタンで付けた返信先・書きかけがあれば付け替えない。
    ✕ で外した自動の返信は、その下書きの間は同じポストに付け直さない (`dismissedAutoReplyId`)。
  * ポスト画面はストリーミングを受けないので、自分の返信は `published` から `insertReply` (`ReplyList.kt`、深さ優先の順で
    返信先の子孫の末尾へ) で足し、返信先のリプライ数を +1 する。
* **未実装**: 下書き保存、
  メンション・ハッシュタグの補完、
  言語の指定、予約投稿。

## 自動更新 (Streaming API)

* `StreamingClient` (`data/streaming/`) が OkHttp の WebSocket で `wss://<configuration.urls.streaming>/api/v1/streaming`
  に 1 本接続し、設定 (`StreamingSettings`、カラムごとのオン / オフ) でオンのカラムの分を購読する: `user` (ホーム + 通知。
  どちらかがオンなら)・`public:local`・`public`。グローバルは流量が多いので既定はオフ。オフのカラムは新着・再接続時の補完を
  受けない (同じ `user` で Home 宛ての新着が届いても足さない。削除・編集はどのカラムにも当てる)。対応は `streamChannelsFor` /
  `streamedTimelinesFor`。`stream()` は設定が変わると接続し直す (全部オフなら接続しない)。
  認証は `Authorization: Bearer` (4.2 以降は全ストリームで要ログイン。未ログインでは接続しない)。
* 切断されたら 1 秒から倍々 (最大 60 秒) で再接続し続け、接続のたびに `StreamEvent.Connected` を流す。
  メッセージの解釈は純粋関数 `parseStreamMessage` (payload は JSON 文字列なので二重デコード)。
* 前面のみ: `TimelineScreen` が `repeatOnLifecycle(STARTED)` で `TimelineViewModel.stream()` を collect する。
  gap の読み込みボタンは下方向に読むので下向き矢印。
  背面に回る・別画面を開くと切断し、戻ると再接続して補完する。
* 構成 (変更時はこの分担を保つ):
  * 一覧の書き換えは純粋関数: `applyStreamEvent` (タイムライン / 通知)、`applyNewerPage` (補完、gap を挟むかの判断)、
    `applyNotificationBackfill` (通知の補完、1 ページ埋まったら `null` = 読み直し)、`fillGap`。
    `ui/feed/TimelineItem.kt` / `ui/notification/NotificationGrouping.kt`。
  * 段取りは `TimelineSync` (`ui/timeline/TimelineSync.kt`): どのカラムに当てるか、`since_id` に何を使うか、読み込み前・
    読み込み中のカラムを飛ばす、取得失敗は `onError` に報告して一覧はそのまま。取得は関数で受け取るので、偽物の取得関数で
    テストする (`TimelineSyncTest`)。ストリームとカラムの対応は `streamChannelsFor` / `StreamChannel.timelineType`。
  * `TimelineViewModel` は `TimelineSync` を組み立てて `stream()` / `fillGap()` から呼ぶだけ。
* ポスト一覧の要素は `TimelineItem` (`PostItem` / `Gap`)。各イベントの反映 (gap は位置を保って素通し):
  * `update` → `mergeNewerPosts` (ID 順の位置へ挿入、既知 ID は無視)、`delete` → `removePost` (ブーストも)、
    `status.update` → `replaceEditedPost` (ブーストの外側は保つ)。
  * `notification` → `prependNotification(isSingleNotification = true)`。ストリームの通知は v2 の `group_key` で既存グループへ
    合流 (件数 +1、移動して先頭へ)。グループの最新通知 ID 以下なら取り込み済みとして無視。ユーザー単位の行への吸収・作り直し、
    メンバーが複数人グループになったら行から外す (閾値未満なら単体に戻す) も行う。
* 抜けの補完: `Connected` のたびに、各カラムの最新 ID 以降を `since_id` で取得して加える。
  * ポスト (Home / Local): 1 ページ (40 件) 埋まったら間に未取得区間がありうるので、`mergeNewerPostsWithGap` で
    「取得分 → gap (読み込みボタン) → 補完前の一覧」と並べる。gap の下端は補完に使った `since_id` (補完中にストリームの新着が
    先頭に入っても位置がずれない)。ボタンで `max_id = gap.maxId`・`since_id = gap.sinceId` を取得し `fillGap` で上側から
    40 件ずつ埋める (40 件未満なら gap を消す)。
  * 通知: `prependNotificationGroups` (件数は総数で置き換え)。1 ページ埋まったら gap は作らず丸ごと読み直す。
* 最大件数: ストリーム・補完で一覧が伸びたら (`PagedFeed.updateList` の後の `trim`) 古い側を捨てる。ポストは
  `MaxTimelinePosts` = 400 件 (`trimTimeline`、gap は数えず末尾に残った gap も捨てる)、通知は 400 行。捨てたら次ページの
  `max_id` を残った末尾から求め直すので、下へスクロールすれば読み直せる。読み進めて伸ばした分 (loadMore) は捨てない。
* 表示: 先頭にいるときに先頭へ新着が入ったら `StickToTopEffect` で先頭へスクロールして見せる。読み進めている途中は
  LazyColumn がキーで位置を保つ。項目の出入りは `Modifier.animateItem()`。
* 未対応: グローバルの自動更新、背面での受信 (Web Push)、通知の gap、通知の削除イベント。

## 設定

* 設定全体は `AppSettings` (`domain/model/settings/`): 表示 (`DisplaySettings`)・通知の取得 API・カラムごとの自動更新
  (`StreamingSettings`)・確認する操作 (`Set<ConfirmTarget>`、既定は自分のポストの編集の保存・削除 = `DefaultConfirmations`)・新しい投稿の公開範囲 (`defaultVisibility`、`null` =
  アカウントの既定 `source.privacy`。ダイレクトは選べない)。
* 保存は `SettingsStore` (`data/settings/`、Preferences DataStore、`AppContainer.settingsStore`)。項目ごとに 1 キーで、
  対応表は `SettingsPreferences.kt` の `SettingFields` (純粋関数、`SettingsPreferencesTest`)。**項目を足すときはここに 1 行足す**。
  * 確認する操作は 1 つのキー (`confirm_targets`) にまとめるが、既定でオンのもの (`DefaultConfirmations`) だけは個別の
    boolean のキー (`confirm_edit_post` / `confirm_delete_post`)。まとめたキーに足すと、追加前に保存したユーザーにはオフで読めてしまうため。
  * キーが無ければ既定値。書き込みは変わった項目だけ (`writeSettingsChanges`)。`null` を値に持つ項目は番兵値
    (折りたたみ・fps は 0、公開範囲は `"account"`) で保存し、読めない値 (知らない列挙値・範囲外) はその項目だけ既定値。
    `null` 自体が値なので、decode は「読めない」と区別するため `Decoded` で包んで返す。
  * 読み込みは非同期: `settings: StateFlow<AppSettings?>` は読み込み前 `null` (起動時から読み始める)。`QuasarAppContent` は
    読み込みまで中身を描かない (既定値で描いてから切り替わらない)。UI 以外は `loaded` (Flow) / `current()` で待つ
    (`TimelineViewModel` の自動更新・通知 API、`ComposerSession` の公開範囲)。
* `QuasarAppContent` が `LocalDisplaySettings` (`ui/settings/`) と `ActionConfirmationHost` を提供する。Activity の
  setContent は `QuasarAppContent(container)` で包む (MainActivity とデバッグ用ギャラリー)。
  表示設定の項目: `longPostMaxLines` (長文の折りたたみ行数、null で無効)、
  `animateImages` (画像のアニメーション全体。オフなら他の 3 つに関わらず止める) / `animatePostEmojis` (ポストの中身の絵文字) /
  `animateOtherEmojis` (それ以外の絵文字) / `animateAvatars` (アイコン)。判定は `DisplaySettings.animates(ImageKind)`、
  `surfaceBlurRadiusDp` (ui では `surfaceBlurRadius: Dp`) / `surfaceBlurTintAlpha`
  (一覧の上に浮かぶ UI の背景のぼかしの半径と色味の濃さ。既定 6dp / 0.8)、`emojiAvatarAnimationMaxFps`。
* **操作前の確認**: `LocalActionConfirmer.current.confirm(target)` (suspend) が、設定で確認する操作ならダイアログ
  (`ActionConfirmationHost` が描く `ConfirmDialog`) を出して答えを待ち、そうでなければすぐ `true`。新しい確認が来たら前のは
  取りやめ、呼び出し側のコルーチンがキャンセルされたらダイアログも消える。ブースト等は `rememberPostActionConfirmation`、
  投稿は `rememberConfirmedSubmit(session)` (クイック投稿バー・投稿画面の送信ボタン。編集なら `EditPost`)、削除はポスト画面から使う。
  設定画面の Confirmations の「My posts」で編集・削除の確認を切り替える。
* **新しい投稿の公開範囲**: `ComposerSession` が「設定 → アカウントの既定」の順で入れる。最後に自動で入れた値
  (`appliedVisibility`) から変わっていたら (ユーザーが選び直した・返信で狭めた) 触らない。設定を変えたときも同様に追従する。
* **設定画面** (`Route.Settings` ほか、`Navigator.openSettings()`。入口はタイムラインのアカウントメニュー): AOSP
  (Android 16) の設定画面の形。ページ (`Route.Settings` / `SettingsDisplay` / `SettingsAnimation` / `SettingsTimelines` /
  `SettingsPosting` / `SettingsConfirmations`) は他の画面と同じバックスタックに積む。
  変更は `SettingsStore.launchUpdate` (ストアのスコープで書くので画面を閉じても止まらない)。
  * 部品 (`ui/component/settings/`): `SettingsScaffold` (`LargeFlexibleTopAppBar` が縮む + 丸い戻るボタン)、`SettingsGroup`
    (見出し + `SegmentedListItem` を 2dp の隙間で並べ、まとまり全体を 24dp の角丸で切る。項目は 4dp の角丸)、
    `SettingsSwitchItem` / `SettingsSplitSwitchItem` (左は詳細ページ、縦線の右はスイッチ) / `SettingsNavigationItem` /
    `SettingsChoiceItem` (ラジオのダイアログ) / `SettingsSliderItem` (スライダーのダイアログ、OK で決定)。
  * 面の色は surfaceBright (背景は surfaceContainer)。`SegmentedListItem` の checked 版は選択中の色・形に変わるので、
    selected 系の色と `selectedShape` を通常と同じにしている (AOSP は面の色でオン / オフを示さない)。
  * 未ログインでの利用は考慮しない (ログイン必須の項目もそのまま出す。非ログインの経路は今後なくす方針)。
* **ぼかし (Haze) のスタイルは必ず `rememberSurfaceBlurStyle(tintColor)` (`ui/component/blur/`) から取る**。半径・濃さを
  個別に書かない (タイムラインのヘッダ・クイック投稿バー・プロフィールのタブで共通)。部品ごとに変えてよいのは色味の色だけ
  (クイック投稿バーは surfaceContainer、他は既定の surfaceContainerHigh)。下地の surface もここで敷く。
  ぼかした面に縁を付けるときは `surfaceBlurBorder()` (outlineVariant 0.5 の 1dp) を使う。
  閲覧注意メディアの `Modifier.blur` は隠すためのものなので対象外。

## テーマ / 色

* `QuasarTheme` は dynamic color + `MaterialExpressiveTheme`。
* **ウィンドウの背景は最上位で決める (各画面・部品で後ろに背景を敷かない)**: Compose が描いていない所 (画面・ページの
  切り替えで半透明になった間、最初のフレームの前) に見えるのはウィンドウの背景。
  * Compose が描き始める前: テーマ (`res/values[-night]/themes.xml` の `Theme.Quasar`) の `windowBackground`。端末の
    dynamic color のニュートラル (ライト `system_neutral1_10` / ダーク `system_neutral1_900`) で、Material 3 の background に近い。
  * 描き始めたら: `QuasarAppContent` の `SyncWindowBackground()` (`ui/theme/WindowBackground.kt`) がテーマの `background` に合わせる。
  * 以前はテーマが Light 固定でウィンドウが白く、ダークテーマで設定画面のページ切り替えの間に光っていた。
* アプリ独自の意味を持つ色は Material 3 の Custom colors として `MaterialTheme.extendedColors` (`ui/theme/ExtendedColors.kt`)
  で参照する。元の色 (お気に入り=黄 #FFC107、ブースト=緑 #4CAF50、フォロー=青 #2196F3) を dynamic color の primary に harmonize し、
  ライト/ダークに応じた役割色 (color / onColor / container / onContainer) を MDC の `MaterialColors` で作る。
  色を直接ハードコードせず、新しい意味の色が要るときはここに追加する。
* メディアの上・メディアビューアの黒背景の上に重ねる白文字・半透明の黒は `MediaOverlayColors` (`ui/component/player/`) に
  集める (テーマに依らず固定してよいのはこの用途だけ)。インラインのメディア (再生ボタン・GIF ラベル・目アイコン) や
  投稿欄の添付 (アップロード中の表示・ALT) もこれを使い、`Color.Black.copy(...)` 等を個別に書かない。
* 自前で色を決める無効状態のアイコン・文字は `onSurface.copy(alpha = DisabledContentAlpha)` (`ui/theme/ContentAlpha.kt`、0.38)。

## 開発メモ

* ビルド: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug`
  (システムに Java が無いので Android Studio 同梱の JBR を使う)
* テスト: `./gradlew :app:testDebugUnitTest` (JVM のユニットテストのみ。instrumentation / UI テストは無い)。
  ギャラリーの固定データの検査 (`GalleryFixturesTest`) は `src/testDebug` にあり、これも同じコマンドで走る。
* 性能の測定用ビルド: `./gradlew :app:assembleBenchmark` (下記「デバッグ用ギャラリー」「実機での確認」)。
* Expressive API は `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` が必要。material3 は BOM ではなく 1.5.x を個別指定。
* **できる限り pure Jetpack Compose で、モダンな書き方にする**。XML は Android の仕組み上 Compose で代わりがきかないもの
  (manifest、Activity のテーマ = ウィンドウ属性、文字列リソース等) に限る。
  * アニメーションは Compose (`animate*` / `Animatable` / `AnimatedVisibility` 等) で書く。`res/anim` の XML や
    `overridePendingTransition` / `overrideActivityTransition` での Activity 遷移アニメーションは使わない
    (画面遷移は Navigation 3 の `NavDisplay` の transitionSpec、メディアビューアの出入りは `DismissibleViewer` で描く)。
  * 「戻る」の横取りは `androidx.navigationevent` の `NavigationBackHandler` (予測型「戻る」の進み具合も取れる) を使い、
    `BackHandler` / `OnBackPressedCallback` は使わない。manifest で `enableOnBackInvokedCallback="true"`。
* 文字列リソースは `res/values/strings.xml` に置く (今は英語のみ)。
* コミットはユーザーが行う。エージェントはコミットしない (頼まれた場合を除く)。

### デバッグ用ギャラリー (`app/src/debug/`)
固定データで本物の部品 (`PostList` / `PostCard` / `ProfileHeader` / `EmojiPicker`) を組み立てて表示する、debug / benchmark
ビルドだけの画面。条件に合う投稿をサーバで探さずに、見た目・動作・負荷を確かめるためのもの。ランチャーにも「Quasar Gallery」で出る。

* adb から開く (`--activity-clear-task` を付ける。付けないと開いている間に別のシナリオを指定しても切り替わらない。
  Android 16 の実機では、同じ Activity への intent が「届けた」扱いのまま `onNewIntent` が呼ばれなかった):
  ```
  adb shell am start --activity-clear-task -n es.aria_on_the_planet.quasar/.debug.DebugGalleryActivity \
      --es scenario animated-emoji [--ez animate_images false] [--ez animate_avatars false] [--ei max_fps 60]
  ```
  `scenario` 無しなら一覧。シナリオ: animated-emoji / animated-avatars / emoji-stress / emoji-picker / profile / post-variety /
  reply (返信先の 2 つの表示。データは post-variety を使い回す)。
  表示設定の extra (`GalleryExtras`) は指定した項目だけを上書きし (`--ei max_fps 0` で上限なし)、ビルドし直さずに比べられる。
* 固定データ: `assets/gallery/*.json` は API と同じ形 (Status / Account / CustomEmoji) で、本番と同じ DTO と mapper で読む
  (`GalleryFixtures`)。画像の URL は `file:///android_asset/gallery/images/...` (Coil が assets から読む。`RemoteImage` と
  APNG のデコーダもそのまま通る)。手で編集せず `python3 scripts/gallery/generate.py` で作り直す (中身は毎回同じ)。
  * テスト画像もスクリプトで描く: APNG (20ms / 70ms / 0ms のコマ、3 回で止まる、横長)、GIF、アニメーション WebP、静止画、
    アニメーションするアイコン (GIF / APNG / WebP)・ヘッダ。それぞれ静止版 (`*_static.png`) も作る。
  * **実在の投稿・名前・アイコン・サーバを入れない**。ドメインは予約済みの `.invalid`、画像は自作。
* シナリオを足す: `generate.py` に JSON を足す → `GalleryScenario` に 1 行 → `GalleryScreen` の `when` に表示を足す。
  `GalleryFixturesTest` (`src/testDebug`) が、全 JSON が読めること・画像の実在・`:shortcode:` に絵文字があること・ID の一意を検査する。
* ボタンの操作 (ブースト等)・プロフィールへの遷移は無効 (データ層を通らない)。メディアのタップはビューアが開く。

### ロジックの置き場所とテスト
* 一覧の組み立て・書き換えは純粋関数にし、ユニットテストを付ける。壊れやすい所は特に厚めに:
  `NotificationGroupingTest` (通知のグルーピング・合流)、`TimelineItemTest` (gap・新着・上限)、
  `TimelineSyncTest` (ストリーム・補完の段取り)、`PostFeedTest` (ページング)。
* `android.util.Log` は JVM テストでは動かない (`isReturnDefaultValues` 未設定)。テスト対象のクラスではログを
  直接呼ばず、`TimelineSync(onError = ...)` のように外から受け取る。
* 実データの JSON の形が怪しいときは、公開 API (`/api/v1/timelines/public` 等) から取ってきて、一時的なテストで
  DTO が読めるか確かめるとよい (ドキュメントと実装が違うことがある。下記)。

## 引き継ぎ: 注意点・落とし穴

### API / サーバの癖
* ドキュメントと型が違うことがある: Mastodon 4.8.0-alpha は `NotificationGroup.most_recent_notification_id` を数値で
  返す → `StringOrNumberSerializer` で受けている。他の ID フィールドでも起きうる。
* Mastodon の ID は文字列だが大小比較は数値として行う (`MastodonIdComparator`: 桁数 → 辞書順)。文字列のまま
  比較しない (`"100" < "99"` になる)。
* 次ページの `max_id` は「表示上の末尾」ではなく「取得した生ページの最古 ID」から決める (`FeedState.nextMaxId`)。
  並び替え (ユーザー単位のグルーピング) や v2 の `page_min_id` で、末尾が最古とは限らないため。
* v2 通知の `notifications_count` はグループ全体の総数。ページ境界で同じグループが分かれても足さない。
* 引用のフォールバック「RE: <リンク>」は Mastodon が `<p class="quote-inline">`、Misskey 由来が
  `<br><br><span class="quote-inline">` で来る (`simplifyMastodonHtml`)。
* 動画の `meta.original` には `aspect` が無い (width / height から計算)。未設定のヘッダ画像は `.../missing.png`。
* `Status.application` (User Agent) は他サーバのポストでは返らない。
* サーバによっては公開タイムラインも要ログイン (mastodon.social は 422)。ストリーミングは 4.2 以降すべて要ログイン。
* 認証済みの Local / Global はミュート・ブロック・フィルタが効く (公開 API の結果と差が出ても正常)。
* グローバルは「サーバに届いた順」で並び、表示時刻 (投稿日時) は前後する。

### Compose / UI
* LazyColumn のキーは一意に保つ。同じポストが別の行にも出うる場所 (ピン留めと投稿一覧など) は 1 アイテムに
  まとめるかキーに接頭辞を付ける。重複キーはクラッシュする。
* テキスト内リンクのタッチ判定は最低 48dp に広がるため、本文先頭のリンクの直上 (投稿者行・時刻) のタップで
  リンクが開くことがある (既知の挙動)。
* `StickToTopEffect` はコンポジション中に LazyListState を読んで「直前に先頭にいたか」を判定している。
  一覧の先頭キーの決め方を変えるときは挙動を確認する。
* **MainActivity は manifest で `android:windowSoftInputMode="adjustResize"` にしておく** (入力欄のある画面がすべてここに載る)。未指定だと OS が `adjustPan` を選ぶことがあり、ウィンドウごと押し上げられた上に Compose の
  `WindowInsets.ime` の分も持ち上がって、クイック投稿バーがキーボードの高さ 1 つ分上 (画面の上端付近) に飛ぶ。
* **凹みのある形 (`MaterialShapes.Cookie9Sided` 等) に `Modifier.shadow` を付けない**。影の計算が RenderThread で非常に重く、
  ヘッダのアイコンに 12dp の影を付けたら 1 フレームに約 10 秒かかり、起動直後に固まって ANR になった。影は円などの凸形で付ける
  (見た目の形と影の形は別でよい)。
* **Navigation 3 のエントリに変わる値を外から渡さない**。`NavDisplay` は積まれたエントリを中身のラムダごと使い回すので、
  `entryProvider { entry<...> { Page(settings) } }` のように外で読んだ値は開いたときのまま変わらない (設定画面のスイッチが
  その場で切り替わらなくなった)。変わる値はエントリの中で Flow から読む (`WithSettings`)。
* カスタム絵文字の縦横比はメモリ上のキャッシュのみ。初回は正方形で表示され、読み込み後に幅が変わる。

### ビルド
* Gradle のインクリメンタルコンパイルが古い結果を返して、正しいコードでも型エラーになることがあった。
  不可解なエラーは `./gradlew :app:compileDebugKotlin --rerun` で確かめる。
* release ビルドは R8 (minify) 無効。`material-icons-extended` が大きいので、有効にするか必要なアイコンだけにする。

### 実機での確認 (adb)
* 実機はワイヤレス adb で、シリアルが `adb-...-w20nYF (2)._adb-tls-connect._tcp` のように変わることがある。
  `adb devices` の先頭の device を使う。切断・ロック・スリープは頻繁に起きる → 実機が寝ていて (`dumpsys power` の
  `mWakefulness=Asleep`) エミュレータが起きているなら、エミュレータで動作確認する。どちらも使えなければユーザーに解除を頼む。
  エミュレータにはログイン情報が無いことがあるので、ログインが要る確認はその旨を伝える。
* 座標決め打ちのタップは許可されているが、アプリの外 (ブラウザ等) に出たら即中断する。リンクを避けるため、
  ポストを開くときはアクション行の空き部分をタップする。
* 先頭へ戻るために下方向スワイプを繰り返すと pull-to-refresh が発動し一覧が読み直される (gap も消える)。
  ボタン等を探すときは fling しないよう長めの duration (1500ms 程度) でゆっくりスクロールする。
* **`./gradlew connectedDebugAndroidTest` は終了時にアプリをアンインストールする** (ログイン情報も消える)。端末上で一時的な
  計測をするなら `installDebug` / `installDebugAndroidTest` + `adb shell am instrument` を使う。
* 性能の測定は debug ビルドでは行わない (Compose が最適化されず数倍遅い)。**benchmark ビルド** (`assembleBenchmark`。
  release と同じ設定 + debug 鍵の署名 + ギャラリー入り。applicationId が同じなので debug と入れ替えてもデータは残る) を入れ、
  `scripts/perf/measure.sh <label> <秒> [swipe]` で測る (端末が複数なら `ADB="adb -t 3"`)。終わったら debug を入れ直す。
  benchmark は debuggable ではないので `run-as` は使えない。フレーム数・ジャンクは `dumpsys gfxinfo <pkg>` (`reset` してから)、
  CPU は `/proc/<pid>/task/*/stat`。**RenderThread は 2 本ある**ので合計する (スクリプトは対応済み)。
  **gfxinfo の「Total frames rendered」はプロセス内の全ウィンドウの合計**。ボトムシート (絵文字ピッカー等) は別ウィンドウなので、
  後ろの画面の描き直しも足される (`framestats` は `ViewRootImpl@...` ごとに分かれている)。後ろのタイムラインの中身は時間で
  変わるので、実データで版どうしを比べるときは同じ状態のうちに交互に測る。条件を揃えたいならギャラリーを使う。
* 特定の条件の投稿・ユーザー (アニメ絵文字・アイコン等) をタイムラインで探し回らない。まずギャラリーにシナリオを足す。
* 起動直後の `uiautomator dump` はまだ描画前のことがある。目的のノードが出るまで数回リトライする。
* gap の動作確認は流量のあるグローバルで行う: 設定 (Timelines → Auto-update) でグローバルをオンにして、別画面を
  開いて (= 切断) 8 分ほど待ってから戻る。確認後はオフに戻す。
* 設定を既定に戻すには (debug): `adb shell am force-stop <pkg>` → `adb shell run-as <pkg> rm files/datastore/settings.preferences_pb`。
* ストリームの接続状況は logcat の `StreamingClient` (接続・切断・受信イベントの種類と文字数のみ。トークンや本文は出さない)。

## 未対応・今後の課題

* **認証**: OAuth 未実装。アクセストークンを手入力し、`SessionStore` (SharedPreferences) に**平文**で保存している。
  OAuth (`/api/v1/apps` + `/oauth/authorize`) と暗号化保存が必要。
* **操作**: 投稿はテキスト・CW・投票・公開範囲・メディア添付・リプライ (リプライは実機で未確認)。投票・お気に入り・ブースト・ブックマークは送信できる (実機では未確認)。
  操作失敗時の UI 通知、ブーストの公開範囲の選択、自分のフォロワー限定ポストのブーストは未対応。
* **メディアビューア**: PiP、代替テキストの表示は未実装
  ([docs/media-viewer.md](docs/media-viewer.md) の「未実装」)。audio はまだ実機で確認していない。
* **設定**: アカウントごとの設定 (今はアプリ全体で 1 つ)、サーバ側の既定の公開範囲 (`update_credentials`) との同期は無い。
  v2 通知は Mastodon 4.3+ 前提で、互換サーバ (Pleroma / GoToSocial 等) やバージョン判定はまだ無い。
* **自動更新**: 通知の gap は無い (1 ページ埋まったら読み直し)、背面での受信 (Web Push) 無し、
  通知の削除イベント未対応。一覧の上限 (400) で捨てる際、下端を読んでいる最中でも古い側が消えうる。
* **プロフィール**: 返信のタブ、プロフィール編集、一覧の行ごとのフォローボタン、フォロー失敗時の UI 通知。
* **通知**: ユーザー単位のまとめに時間の窓が無い (同じユーザーなら古いリアクションも同じ行に入る)。
* **画像のアニメーション**: GIF / APNG / Motion WebP の再生・設定のオン / オフ・メモリ不足時の挙動は確認済み。負荷は
  Snapdragon 8 Elite (OnePlus 13) でしか測っていない (release 相当、何もしない状態、CPU は 1 コア比): ピッカー (APNG 3 つ) は
  上限なし 82〜88fps・16%、上限 30fps で 41fps (ピッカー 30 + 後ろの画面の GIF 約 10)・11%。アイコンが動くタイムラインの
  1 画面 1.4fps・0.9%、スクロール中のジャンク 0。GIF / WebP のフレームレート上限は実装しない方針 (Coil = ImageDecoder に任せる。
  上限を掛けるには再生の置き換えが要る: GIF は android-gif-drawable、WebP は Fresco の animated-webp 等が候補だった)。
  切り替えは設定画面 (Display → Animation) で確かめる。
* **ローカライズ**: 文字列は英語のみ。
* **テスト**: Composable の UI テスト・スクリーンショットテストは無い。
