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
│   └── repository/                 TimelineRepository, AccountRepository, StatusRepository
├── domain/model/                   UI が扱うモデル (User, Profile, Post, Timeline, CustomEmoji, PreviewCard, MediaAttachment, Poll, Quote)
└── ui/
    ├── theme/                      MaterialExpressiveTheme
    ├── component/
    │   ├── text/EmojiText.kt       カスタム絵文字対応テキスト (Text のラッパー)
    │   ├── post/PostCard.kt        ポスト 1 件
    │   ├── post/PostList.kt        ポストの集合 (LazyColumn)
    │   ├── post/PostDetail.kt      詳細画面の主役ポスト (大きめ本文 + 詳細日時)
    │   ├── post/PostTime.kt        詳細日時のフォーマット
    │   ├── post/QuotedPost.kt      引用ポストの縮小表示 / 表示不可時のプレースホルダ
    │   ├── poll/PollView.kt        投票 (選択肢 / 結果) + PollMath.kt (割合・残り時間)
    │   ├── scaffold/DetailScaffold.kt 詳細画面共通の骨組み (戻る付きトップバー + スクロールでタイトル切替 + PTR)
    │   ├── card/LinkPreviewCard.kt リンクプレビュー
    │   ├── media/MediaAttachments.kt 添付メディアの並び + 1 件 (MediaAttachmentItem)
    │   ├── image/                  BlurHash デコーダ + プレースホルダ Painter
    │   ├── list/LoadMoreEffect.kt  末尾付近で追加読み込みを呼ぶ (PostList / NotificationList 共通)
    │   ├── layout/CollapsibleContent.kt 高さ超過時に折りたたみ + フェード + 展開ボタン
    │   └── modifier/               汎用 Modifier (FadingEdges: 左右 / 下端)
    ├── feed/                       PagedFeed<T> (max_id ページングの状態管理) / PostFeed / PollVoting
    ├── notification/               通知一覧 (NotificationList / NotificationItem)
    ├── login/                      サーバ URL + トークン入力 (暫定。OAuth は後で)
    ├── timeline/                   タイムライン画面 + ViewModel
    ├── profile/                    プロフィール (ProfileActivity / Screen / Header / ViewModel)
    └── post/                       ポスト詳細 (PostActivity / Screen / ViewModel / ReplyList)
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

// User は一覧表示に必要な最小限。プロフィール画面用の詳細は Profile に分ける
// User.fullScreenName: ローカルユーザーにも所属ドメインを付けた username@domain

data class Profile(
    val user: User,
    val headerUrl: String?,        // header。未設定時の .../missing.png は null にする
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
    val viewer: PostViewerState,   // 自分がこのポストに行った操作
    val boostedBy: User?,          // ブーストの場合、ブーストしたユーザー
    val statusId: String,          // 本文を持つ Status の ID (ブーストなら中身)。詳細画面・スレッドはこれを使う
    val inReplyToId: String?,      // in_reply_to_id (中身の値)
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

| 用途 | エンドポイント |
| --- | --- |
| プロフィール | `GET /api/v1/accounts/:id` |
| プロフィールの投稿一覧 | `GET /api/v1/accounts/:id/statuses` (初回 `limit=15`、以降 40) |
| ピン留めポスト | `GET /api/v1/accounts/:id/statuses?pinned=true&limit=20` |
| 自分がフォローしている人のうちのフォロワー | `GET /api/v1/accounts/familiar_followers?id[]=:id` (要認証) |
| 通知 (グループ) | `GET /api/v2/notifications` (要認証、Mastodon 4.3+、既定) |
| 通知 (個別) | `GET /api/v1/notifications` (要認証、max_id ページング。`NotificationApi.Ungrouped` 時) |
| ポスト | `GET /api/v1/statuses/:id` |
| 投票する | `POST /api/v1/polls/:id/votes` (form `choices[]`, 要認証) |
| スレッド | `GET /api/v1/statuses/:id/context` (未認証では祖先 40・子孫 60・深さ 20 まで) |

`:id` は接続中サーバ内の ID (`User.inServerId`)。リモートユーザーでも接続中サーバに問い合わせる。

### PagedFeed / PostFeed (`ui/feed/`)
`PagedFeed<R, T>` は max_id ページングの状態 (`FeedState<T>`: items / isRefreshing / isLoadingMore / endReached / error /
loaded / nextMaxId) と `refresh()` / `loadMore()` / `updateItems()` をまとめた汎用クラス。`R` は API の 1 件、`T` は表示の
1 件で、`append(current, page)` (純粋関数) で一覧を組み立て、`nextMaxIdOf(page)` で取得した生ページから次の `max_id` を
決める。`appendDistinct` は重複を捨てる標準の append。`PostFeed` はそのポスト版 (`PostFeedState = FeedState<Post>`、
`.posts` は `.items` の別名)。通知は `PagedFeed<NotificationGroup, NotificationEntry>`。

## 再利用 UI コンポーネント

### EmojiText (`ui/component/text/EmojiText.kt`)
アカウント名・ポスト本文・プロフィール文はカスタム絵文字 `:shortcode:` を含みうるため、**`Text` を直接使わず必ず `EmojiText` を使う**。

* `EmojiText(text: String, emojis: List<CustomEmoji>, isHtml: Boolean, ...)`
  * `isHtml = true` のとき Mastodon の HTML を `AnnotatedString.fromHtml` で変換 (リンク付き)。
  * `:shortcode:` を `emojis` と照合し、`InlineTextContent` + Coil の `AsyncImage` で画像に置換。
  * 絵文字の高さは文字スタイルの行の高さ (`lineHeight`、未指定なら 1.2em) に合わせ、幅は画像の縦横比から決める
    (横長の絵文字は横長に表示)。API はサイズを返さないため、画像読み込み時に `EmojiAspectRatios` (URL ごとの
    アプリ全体キャッシュ) へ記録し、記録されると再レイアウトする。未読み込みの間は正方形。比率は 1/4〜8 に丸める。
* 変換処理 (`replaceEmojiShortcodes`, `simplifyMastodonHtml`) は純粋関数として分離し、ユニットテスト可能にする。

### PostCard (`ui/component/post/PostCard.kt`)
ポスト 1 件の表示。タイムライン・プロフィール・詳細画面などで再利用する。

* `PostCard(post: Post, modifier, mediaHeight: Dp = PostCardDefaults.MediaHeight, onUserClick, onPostClick, onMediaClick)`
* リプライ表示: `inReplyToId` があるポストは、ブースト表示と同じ形 (アイコン + tertiary 色) で「Reply to @acct」を出す。
  acct は `in_reply_to_account_id` を、自分へのリプライなら投稿者、それ以外は `mentions` から引いて求める
  (見つからなければ「Reply」)。Post 画面のスレッドでは縦線で関係が分かるので `showReplyIndicator = false`。
* 構成: (ブースト表示) / (リプライ表示) / アイコン + 表示名 (EmojiText) + screenName / 本文 (EmojiText) / 投票 (PollView) /
  添付メディア (MediaAttachments) / リンクプレビュー (LinkPreviewCard) / 引用 (QuotedPost) / アクション行
* `mediaHeight` はそのまま `MediaAttachments` に渡す (`PostList` も同名パラメータで中継する)。
* アクション行: リプライ数・ブースト数・リアクション数・ブックマーク。`viewer` のフラグが立っているものは
  塗りアイコン + 色付き (ブースト=extendedColors.reblog (緑), リアクション=extendedColors.favourite (黄),
  ブックマーク=secondary) で表示する。
  現時点では表示のみで、操作 (favourite/reblog/bookmark API 呼び出し) は未実装。
* `Post` 以外の状態を持たない (stateless)。

### PostList (`ui/component/post/PostList.kt`)
ポストの集合。タイムライン以外 (プロフィールの投稿一覧など) でも使う。

* `PostList(posts, onLoadMore, isLoadingMore, modifier, contentPadding, header, ...)`
* `LazyColumn` + `key = post.id`。末尾付近で `onLoadMore` を呼ぶ。
* データ取得には関与しない (呼び出し側の ViewModel が担当)。
* 末尾付近にいる間は項目数が増えるたびに `onLoadMore` を再度呼ぶ (1 ページが画面に収まる場合も続きを読む)。

### Content Warning / NSFW
* CW (`ContentWarning.kt`): `spoiler_text` があるポストは、本文の位置に警告文 (EmojiText) と、その下に primary 色の
  「Show more >」を出す。タップで本文・投票・リンクプレビュー・引用を展開 (`>` は上向きに回り「Show less」)。
  展開状態は `rememberContentWarningExpanded(post)` (statusId ごとの rememberSaveable)。PostCard / PostDetail / QuotedPost 共通。
* NSFW: `sensitive` のポストのメディアは、初期状態でぼかし (BlurHash があればそれを表示し本体は読み込まない。無ければ
  `Modifier.blur`) + 右上に目アイコン。アイコン (またはぼかし中のメディア自体) のタップで切り替え。状態はメディアごと。
  `sensitive` でないポストでは目アイコンを出さない。CW とは独立 (CW で隠れるのは本文側のみで、メディアは sensitive に従う)。

### PollView (`ui/component/poll/PollView.kt`)
* `PollView(poll, modifier, onVote: (suspend (List<Int>) -> Boolean)?)`。`PostCard` / `PostDetail` が本文の直後に置く。
* 結果表示: 終了済み・投票済み・`onVote == null` (未ログイン、引用内) のとき。割合バー (最多得票は primaryContainer +
  太字、0% でも細いバーを出す)・右端に割合 (10% 未満は小数 1 桁)・自分の選択にチェック。複数選択の分母は voters_count。
* 投票: 枠線のピル型ボタンを中央寄せで並べる。単一選択はタップで即投票、複数選択はトグルで選んで「Vote」。
  投票中はボタン無効、失敗時はエラー文言。
* フッタ: 「N votes · 6 days left」/「Final results」(複数選択は「N people」)。
* 投票処理は ViewModel の `suspend fun vote(post, choices)` (`ui/feed/PollVoting.kt` の `submitVote`)。
  viewModelScope 上で実行し、成功したら `Post.withPoll()` で各一覧 (引用先含む) の該当投票を差し替える。
  画面側は `onVote = if (viewModel.canVote) viewModel::vote else null` で渡す。

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
* メディアビューア未実装のため、タイムラインではタップでメディア URL をブラウザで開いている。
* 未対応: `sensitive` (閲覧注意) によるぼかし表示。

## 画面

* **Profile** (`ProfileActivity`): `Context.openProfile(user)` で起動する (extra はアカウント ID のみ)。
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
  * 未実装: フォローボタン、投稿/返信/メディアのタブ切り替え、familiar followers の一覧画面。
  * 注: API の Profile エンティティ (`/api/v1/profile`) はログイン中ユーザー自身の編集用 (raw な値) で、
    他人のプロフィール表示には Account を使う。ドメインの `Profile` は表示用で、API の Profile とは別物。
* **Post** (`PostActivity`): `Context.openPost(post)` で起動する (extra は `post.statusId`)。
  * 構成: 祖先 (古い順、PostCard をアイコン位置の縦線で連結) / 本体 (`PostDetail`) / リプライ一覧。
    初回表示時は本体ポストまでスクロールする。
  * `PostDetail`: PostCard の全要素 + 大きめの本文・メディア高さ 240dp + 詳細日時 + 区切り線付きアクション行。
    詳細日時は `yyyy MM/dd HH:mm:ss` (システム設定が 12 時間なら `yyyy MM/dd h:mm:ss a`)。
    日時の隣に投稿アプリ (`Status.application`) を「日時 · アプリ名」で出し、`website` があればリンクにする。
    application は他サーバからのポストでは返らないため、表示されるのは主にローカルユーザーのポスト。
  * リプライ (`ReplyList.kt`): ツリーにはせず、API (`descendants`) の順のまま一列に並べる。インデント・折りたたみは無し。
    直前の行へのリプライが続く場合はアイコン位置の縦線で繋ぎ (X と同様)、繋がらない行の間は区切り線。
  * トップバーは「Post」、本体が画面外に出ると投稿者名 + `@acct` に切り替わる。
* **DetailScaffold** (`ui/component/scaffold/`): Profile と Post で共通の骨組み。戻る付き TopAppBar、
  pull-to-refresh、`revealTitleAfterIndex` 番目のアイテムが画面外に出たら `title` → `revealedTitle` に切り替え。
  補助として `DetailTitle` (名前 + 補足の 2 段)、`DetailLoading`、`DetailMessage` を持つ。
* 遷移: `PostCard` のアイコン・表示名 (+ screenName)・「〜 boosted」ラベルのタップで `onUserClick`。
  タイムラインとプロフィールの両方から `openProfile` する (プロフィール中の本人の投稿からは遷移しない)。
  ポスト本体のタップ (`onPostClick`) はタイムライン・プロフィール (ピン留め含む)・ポスト詳細から `openPost` する。
* 既知の挙動: Compose はテキスト内リンクのタッチ判定を最小 48dp に広げるため、本文先頭のリンクの直上
  (投稿者行・時刻) をタップするとリンクが開くことがある。

* **Login (暫定)**: サーバ URL とアクセストークン (任意) を入力。トークンなしでは Home を無効化。
  OAuth (`/api/v1/apps` + `/oauth/authorize`) は後続タスク。
* **カラム** (`ColumnType`): Home / Notifications / Local / Global。Home と通知はログイン時のみ。
  4 つでは等幅にラベルが収まらないため、選択中のボタンだけラベルを出して幅を広げる (他はアイコンのみ)。
* **通知カラム** (`ui/notification/`): `NotificationGroup` 単位の一覧。
  * 取得 API は `NotificationRepository.api` で切り替える。既定は `Grouped` (`GET /api/v2/notifications`、Mastodon 4.3+、
    サーバが favourite / reblog / follow を「同じ種類・同じ対象・近い時間帯」でまとめる)。`Ungrouped` は v1 で、
    各通知を 1 件のグループ (`Notification.asGroup()`) にする。将来は設定画面やサーバのバージョン確認で切り替える。
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
* **Timeline**: 上部に Home / Local / Global の切り替え (Expressive の `ButtonGroup` 系トグル)、
  中身は `PostList`。各タイムラインの状態は `TimelineViewModel` が `TimelineType` ごとに保持。
  pull-to-refresh と追加読み込みに対応。読み込み中は `LoadingIndicator` を使う。

## テーマ / 色

* `QuasarTheme` は dynamic color + `MaterialExpressiveTheme`。
* アプリ独自の意味を持つ色は Material 3 の Custom colors として `MaterialTheme.extendedColors` (`ui/theme/ExtendedColors.kt`)
  で参照する。元の色 (お気に入り=黄 #FFC107、ブースト=緑 #4CAF50、フォロー=青 #2196F3) を dynamic color の primary に harmonize し、
  ライト/ダークに応じた役割色 (color / onColor / container / onContainer) を MDC の `MaterialColors` で作る。
  色を直接ハードコードせず、新しい意味の色が要るときはここに追加する。

## 開発メモ

* ビルド: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug`
* テスト: `./gradlew :app:testDebugUnitTest`
* Expressive API は `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` が必要。
* 文字列リソースは `res/values/strings.xml` に置く。
