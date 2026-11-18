# マルチアカウント

複数のアカウントでログインしておき、ドロワーから切り替える。計画の段階 (「進め方」の 2 まで済み。切り替えはまだ無い)。

## 要求と決めたこと

* 切り替えはドロワー (`TimelineDrawerSheet`) から。切り替えたら画面を作り直してよい (バックスタックは `[Timeline]` に戻る)。
* アカウントをまたいだ操作 (別のアカウントでお気に入り等) は今回は作らない。ただし後から足せる形にしておく (下記「将来」)。
* 設定は**アカウントごとのもの**と**端末共通のもの**に分ける。新しく足したアカウントのアカウントごとの設定は**既定値**から
  始める (コピーしない)。その分、再設定の手間が出ないよう、アカウントごとにするのは本当にアカウント・サーバに依存するものに絞る。
* ログアウトしたら、そのアカウントの設定 (アカウントごとの分)・キャッシュは消す。
* 書きかけの投稿 (下書き) はアカウントごとに持ち、切り替えて戻っても残る (プロセスが終わると消えるのは今と同じ)。
* API の結果のキャッシュ (Room) はアカウントごとに分ける。見えるポスト・関係・自分の操作の状態はアカウントで違い
  (ブロック・フォロワー限定・ダイレクト等)、ID はサーバの中でしか一意でないため。メディアのキャッシュ (Coil・再生) は共有する
  (URL がキーで、アカウントによって中身が変わらない)。

## 方針: アカウント 1 つ分の依存をまとめた `AccountContext`

* 今のコードは「今のアカウント」を暗黙に読む所がほぼ無い: Repository のメソッドと ViewModel は `Session` を引数で受け、
  `MastodonApi` は呼び出しごとにサーバ・トークンを受ける。入口は `QuasarNavHost` の `WithSession(current)` だけ。
  問題はアプリ全体で 1 つだけ持っていてアカウントを区別しないもの:
  | もの | 今 | 問題 |
  | --- | --- | --- |
  | `EntityCache` (Room) | 1 ファイル。セッションが変わったら `clear()` | キーが `(kind, id)` だけ |
  | `SettingsStore` | `settings` の DataStore 1 つ | アカウントごとにならない |
  | `SessionStore` | SecureStorage の `session` 1 つ | 1 アカウントしか持てない |
  | `relationshipChanges` / `postChanges` | アプリ全体で 1 本 | 将来、B での操作が A の一覧に当たる |
  | `DraftStore` / `PostEditStore` | `open(session)` で別のアカウントの下書きを捨てる | 切り替えで下書きが消える |
  | `StreamingClient` | アプリ全体で 1 つ。キャッシュに書く | 書く先のキャッシュがアカウントと結びつかない |
  | `PostTranslationStore` | `statusId` がキー | サーバをまたぐと衝突しうる (本文も比べているので実害は小さい) |
* **「今のアカウント」をグローバルな可変の値にして、Repository・ViewModel がその都度読む形にはしない**。代わりにアカウント
  1 つ分の依存 (`AccountContext`) を作り、ViewModel は作るときに受け取って寿命の間ずっと同じものを使う。
  * 切り替えの瞬間に送信中だったリクエストの結果が、別のアカウントのキャッシュ・一覧に書かれない (作ったときに決まっている)。
  * 将来のアカウントをまたいだ操作が `accounts.context(B).statuses.perform(...)` で書ける (一時的に切り替える必要が無い)。
  * 共有から投稿するアカウントを選ぶ等、入口ごとに別のアカウントを使える。
  * テストでは偽物の `AccountContext` を渡せばよい。
* 「今の」アカウントは UI の最上位にだけある (`AccountManager.active`)。`QuasarNavHost` がそれを各画面に渡す。今 `Session` を
  渡している配線を `AccountContext` に替える形になる。

```
AppContainer (アプリ全体)
├── OkHttp / MastodonApi / Json
├── InstanceRepository / EmojiRepository   サーバ単位 (トークン無し) なので共有
├── ImageLoader・インライン再生・翻訳 (エンジン・モデル・PostTranslationStore)
├── DeviceSettingsStore                    端末共通の設定 (今の `settings` のファイル)
├── AccountStore                           アカウントの一覧 + 使っているアカウント (SecureStorage)
├── AccountManager                         AccountKey → AccountContext (必要になったら作る) / active / switchTo / logout
└── OAuthLogin                             ログインできたら AccountStore.add → switchTo

AccountContext (アカウントごと)
├── key: AccountKey, session: Session (serverUrl + accessToken)
├── cache: EntityCache                     専用の Room の DB
├── settings: AccountSettingsStore         専用の DataStore
├── Timeline / Account / Status / Notification / Search / Media の Repository
│                                          session と cache をコンストラクタで受ける (メソッドの session 引数は無くす)
├── streaming: StreamingClient             書く先はこのアカウントの cache
├── draftStore / panelDraftStore / postEditStore
├── accountActions / ownPostActions        (今の SessionAccountActions / SessionOwnPostActions)
└── close()                                ログアウト時: DB を閉じ、下書きのスコープを止める
```

* Repository の `session` 引数は無くす: キャッシュがアカウントごとになるので Repository もアカウントごとのインスタンスになる。
  引数でも受けると「A のキャッシュを持つ Repository に B の session を渡す」が書けてしまう。差分は大きいが機械的。
* `AccountContext` は一度作ったらログアウトまで持つ (アカウントの数は少なく、中身は軽い)。下書きを切り替えをまたいで残すため。

## アカウントの保存 (`AccountStore`)

* SecureStorage に、アカウントの一覧 (`StoredAccount` のリスト) と使っているアカウントの `AccountKey` を置く。
  `StoredAccount` = `key` / `serverUrl` / `accessToken` / `client` (ログアウト時の無効化用) / `accountId` /
  ドロワーに出す情報 (acct・表示名・アイコンの URL・絵文字。`fetchMe`・`myAccount` の結果で更新する。切り替えの一覧を通信なしで描ける)。
* `AccountKey` は端末の中で振る UUID。ファイル名 (キャッシュ・設定) にも使う。
* 同じアカウント (`serverUrl` + `accountId` が同じ) でもう一度ログインしたら、新しく足さずにトークン・クライアントを差し替え、
  古いトークンは無効化する。
* 機密の扱いは今と同じ (トークン等は SecureStorage 以外に置かない。`Route`・`rememberSaveable` には `AccountKey` まで)。

## 設定の分け方

型は `AppSettings` を `DeviceSettings` (端末共通) と `AccountSettings` (アカウントごと) に分ける。保存のキー
(`SettingsPreferences.kt` の `SettingFields`) も 2 つの一覧に分ける。キーの名前は変えない。

分ける基準: **サーバ・アカウント (の使い方) によって変えたくなるものだけをアカウントごと**にする。人・端末の好み
(見た目・操作の癖・言語) は端末共通。新しいアカウントは既定値から始まるので、端末共通にしておけば再設定が要らない。

| 項目 | 置き場所 | 理由 |
| --- | --- | --- |
| 表示 (`DisplaySettings`: 長文の折りたたみ・ぼかし・アニメーション・fps 上限・UI / 文字の倍率・アイコンの形・カラムの並べ方) | 端末 | 見た目の好み・端末の大きさ。ログイン画面 (未ログイン) でも要る |
| 予測型「戻る」 (`PredictiveBackSettings`) | 端末 | 操作の癖 |
| 翻訳のオン / オフ (`TranslationSettings.enabled`) | 端末 | モデルは端末に 1 つ。A でオフ + 「モデルも削除」で B の翻訳が止まる、を避ける |
| 翻訳の訳す先 (`targetLanguage`) | 端末 | 読む人の言語 |
| 翻訳の言語ごとの扱い (`defaultMode` / `languageModes`) | 端末 (要検討) | 読める言語は人で決まる。サーバの言語圏で変えたい人もいるかもしれない |
| ポストのメニューに出さない項目 (`hiddenPostMenuItems`) | 端末 | 操作の好み |
| 操作前の確認 (`confirmations`) | 端末 (要検討) | 誤操作の防止は人の癖。仕事用のアカウントだけ投稿を確認したい等ならアカウントごと |
| 大きな画像の圧縮 (`compressOversizedImages`) | アカウント | 投稿の添付の扱いはアカウントの用途で変えたい。上限 (`image_size_limit`) もサーバごと |
| 画像のメタデータの削除 (`stripImageMetadata`) | アカウント | 同上。足したアカウントは既定 (オフ) から始まるので、追加の後に設定し直す必要がある (追加の直後に知らせるかは未定) |
| 通知の取得 API (`notificationApi`) | アカウント | サーバの版で使えるかが違う (3.5 は v2 が無い) |
| カラムごとの自動更新 (`StreamingSettings`) | アカウント | サーバの流量 (大きなサーバのグローバル等) で決まる |
| 新しい投稿の公開範囲 (`defaultVisibility`) | アカウント | アカウントの用途 |

* この表は仮のもの。項目ごとの置き場所は後で整理する。
* 今後アカウントごとになりそうなもの: カラムの並び・種類 (リスト等)、フィルタ、Web Push。
* 設定画面: トップで「アカウントの設定」(使っているアカウントのもの。見出しに @acct@host) と「アプリの設定」(端末共通) に
  分ける。今のページは両方が混ざる (Timelines: 自動更新・通知 API はアカウント、ポストのメニューは端末) ので、どちらかに
  振り分けて組み直す (Posting は公開範囲・画像の扱いともアカウント)。ページの構成は後で整理する。
* 読み込みを待つ所: `QuasarAppContent` は端末共通の設定だけを待つ (表示・確認・ポストのメニューはすべて端末共通なので、
  未ログインでも今と同じに動く)。アカウントごとの設定は `AccountContext.settings` から (`TimelineViewModel` の自動更新・
  通知 API、`ComposerSession` の公開範囲・`AppContainer` の `composerSession` (`AccountUiContext.composerSession` の中身) の
  `prepareUpload` が読む画像の扱い)。
* 移行: 今の `settings` のファイルをそのまま端末共通の設定にする (キーが同じなので読める)。アカウントごとの項目は移行する
  アカウントのファイルへ写し、端末共通のファイルからは消す。

## キャッシュ

* アカウントごとに別の DB ファイル: `cacheDir/api-cache/<AccountKey>/api-cache.db`。行にアカウントの列を足す方式は DAO・
  インデックス・trim がすべて変わり、条件の付け忘れで漏れる。ファイルを分ければ、ログアウトはファイルを消すだけ。
  * `setCacheBehaviorGroup` はアカウントのディレクトリごとに付ける (本体・`-wal`・`-shm` がばらけて消されない)。
  * 起動時の `trim()` は DB を開くとき (`AccountContext` を作るとき)。
  * `sessionStore.session.drop(1).collect { cache.clear() }` は要らなくなる。
  * 以前の `api-cache/api-cache.db` は消す (キャッシュなので移さない)。
* 共有のまま: Coil のディスク・メモリ (URL がキー)、インライン再生、`EmojiAspectRatios`、`ApngFrameCache`、
  `InstanceRepository` / `EmojiRepository` (サーバごと)。
* `PostTranslationStore` はアプリ全体で 1 つのまま (エンジン・モデルは端末に 1 つ)。切り替えたら訳と言語の推定の結果を消す。

## 切り替え (画面の作り直し)

* `AccountManager.switchTo(key)` で `active` が変わる → `MainActivity` の中身を `key(activeKey) { ... }` で包み直し、
  **アカウントごとの `ViewModelStoreOwner`** を `LocalViewModelStoreOwner` で渡す。`recreate()` / `finish()` は要らない。
* Nav3 1.2.0 / lifecycle 2.11.0 で動くことを確かめた (下記「確かめた結果」)。形は次のとおり:

```kotlin
// MainActivity
private val accountStores: AccountStoresViewModel by viewModels() // AccountKey → ViewModelStore。onCleared で全部 clear

setContent { QuasarAppContent(container) {
    val active by accountManager.activeKey.collectAsStateWithLifecycle()
    // key の外。他のアカウントの保存した状態を消すため (下記「穴」)
    val stateHolder = rememberSaveableStateHolder()
    LaunchedEffect(active) { (accountStore.keys - active).forEach(stateHolder::removeState) } // ログアウトしたキーもその時に消す
    key(active) {
        val owner = remember { AccountViewModelStoreOwner(accountStores.store(active), this@MainActivity) } // 既定の factory・extras は Activity に委譲
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(Unit) {
            // 切り替えのときだけ消す。Activity の作り直し (回転等) では DESTROYED になっているので消さない (ライブラリと同じ判定)
            onDispose { if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) accountStores.clear(active) }
        }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner /*, AccountContext */) {
            stateHolder.SaveableStateProvider(active) { QuasarNavHost(...) }
        }
    }
}}
```

* 注意:
  * **`AccountKey` は `hashCode` がプロセスをまたいで変わらない型にする** (String か String の data class)。保存の合成キーに
    `key()` のキーの `hashCode` が入るので、enum・identity hash ではプロセスの再生成で復元されない。
  * アカウントごとの owner は `HasDefaultViewModelProviderFactory` で Activity の `defaultViewModelProviderFactory` /
    `defaultViewModelCreationExtras` を委譲する。Activity を ViewModel に持たせないよう、包みは composition の中で作る。
  * 一番上のエントリ (Nav3 の movableContent) だけは後始末が遅れ、新しいアカウントの画面の effect が動き出した後に前の画面の
    `LaunchedEffect` のキャンセル・`onCleared` が来る (エントリの Lifecycle の ON_PAUSE/STOP/DESTROY は先)。前のアカウントの通信
    (ストリーミング等) が一瞬重なりうるが、ViewModel が作ったときのアカウントを持っていれば害は無い。
* `ShareActivity` は使っているアカウントで投稿する。外から開くリンク (`OpenLink`) も同じ。

### 確かめた結果

debug ビルドだけの実験の画面 (ブランチ `spike/nav3-account-switch` の `app/src/debug/.../debug/navspike/`。マージしない) で、
エミュレーターの logcat とライブラリ (逆コンパイル) で確かめた。

* **前のアカウントの ViewModel はバックスタックの深さに関わらずすべて `onCleared` される**。消しているのは
  `rememberViewModelStoreProvider` (`RememberViewModelStoreProviderKt`) の DisposableEffect の
  `provider.clearAllKeys()` (lifecycle が CREATED 以上のとき)。NavDisplay ごと消えるときは Nav3 の `onPop` は呼ばれない
  (バックスタックに残っているので)。
  * なので owner の `clear()` が無くても ViewModel は消えるが、`ViewModelStoreProvider` が親のストアに置くエントリの表
    (`StateHolder`、空) がアカウントごとに残る。アカウントごとの owner を `clear()` すれば Activity のストアに何も残らない。
  * 対照として今の作り (バックスタックを `[Timeline]` に戻すだけ) を試すと、前のアカウントの Timeline の ViewModel と
    `rememberSaveable` が残り、前のアカウントの ViewModel のまま新しいアカウントの画面が組み立てられた。
* **保存した状態も捨てられる**。切り替えた先のアカウントは `[Timeline]` から始まる。元のアカウントに戻しても `[Timeline]` から
  (作り直しの扱い)。バックスタック・エントリの `SaveableStateHolder` は key の内側の `rememberSaveable` で、破棄で登録が外れる。
* **Activity の作り直し (設定の変更) では ViewModel も状態も残る**。owner は Activity の ViewModel に置けば残る。
  **プロセスの再生成**ではバックスタックと上・下のエントリの `rememberSaveable` が戻る (ViewModel は作り直し)。
* **前のアカウントの画面が新しいアカウントの値で組み立てられることは無かった**。詳細のパネルを開いたまま切り替えても全部消え、
  切り替えた後の「戻る」も普通に動いた。
* **穴: 復元したときの active が保存したときと違う** (MainActivity が止まっている間に active が変わった。別の所でのログアウト等)
  と、新しい active は正しく `[Timeline]` から始まるが、前のプロセスで保存した前のアカウントの状態が残り、同じプロセスで
  そのアカウントに切り替えると古いバックスタックがよみがえる (使われなかった復元値を親の SaveableStateRegistry が持ち越すと
  思われる。Compose のソースでは確かめていない)。key の外の `rememberSaveableStateHolder()` + `SaveableStateProvider(active)` で
  包み、他のアカウントの分を `removeState` すれば起きなくなった (作り直し・再生成での復元はそのまま)。

## ログイン・追加・ログアウト

* 追加: ドロワーの「アカウントを追加」で `Route.Login` を積む (戻るボタン付き。今の「ログインしていたら Login を外す」作り直しは、
  `activeKey` が変わったときの作り直しに置き換える)。`OAuthLogin.exchange` でトークンを得たら `verify_credentials` で
  `accountId`・acct を確かめてから `AccountStore.add` → `switchTo`。途中の状態 (`PendingAuthorization`) は今と同じく 1 つ。
* ログアウト: 対象は使っているアカウント (設定画面のトップ。補足にアカウント)。確認 → トークンの無効化 (`StoredAccount.client`)
  → `AccountContext.close()` → キャッシュの DB とアカウントごとの設定のファイルを消す → 残りのアカウントがあればそちらへ
  切り替え、無ければ Login。
* 移行 (初回起動): 今の `session` を 1 つ目のアカウントにする (`accountId` は無いので最初の `fetchMe` で埋める)。

## 切り替えの UI

* ドロワーの上のアカウントの欄 (X のアプリの形): 今のアカウントのアイコン・名前・acct の右に他のアカウントの小さなアイコン
  (タップで切り替え)、展開すると全アカウントの一覧 +「アカウントを追加」。一覧は `AccountStore` の情報で描く (通信なし)。
* 細かい形は実装時に詰める。

## 将来: アカウントをまたいだ操作

* `AccountManager.context(key)` で、使っていないアカウントの `AccountContext` も取れる。
* ポストの ID はサーバの中でしか通じないので、別のアカウントで操作するにはそのサーバで引き直す (`SearchRepository.resolve`)
  必要がある。`Post` には今 `url` しか無いので、`uri` (ActivityPub の id) も持たせておくと確実に引ける。
* 変更の通知 (`relationshipChanges` / `postChanges`) はアカウントごとの Repository に付くので、B での操作が A の一覧に当たらない。

## 変更の範囲

| 層 | ファイル | 内容 |
| --- | --- | --- |
| 新規 | `data/account/` (`AccountStore` / `AccountKey` / `StoredAccount` / `AccountManager` / `AccountContext`) | |
| data | `data/session/SessionStore.kt` | `AccountStore` に置き換え (移行込み) |
| | `data/auth/OAuthLogin.kt` | 追加のログイン・`verify_credentials`・アカウントごとのログアウト |
| | `data/settings/` | 端末共通 / アカウントごとの 2 つのストア、`SettingFields` の分割、移行 |
| | `domain/model/settings/` | `AppSettings` → `DeviceSettings` + `AccountSettings` |
| | `data/cache/CacheDatabase.kt` | 置き場所を引数に・ファイルの削除 |
| | `data/repository/*` (Timeline / Account / Status / Notification / Search / Media) | `session` をコンストラクタへ。メソッドの引数から外す |
| | `data/streaming/StreamingClient.kt` | アカウントごと |
| di | `di/AppContainer.kt` | 共有のものとアカウントごとのものに分ける |
| ui | `ui/navigation/QuasarNavHost.kt` / `Route.kt` | `WithSession` → `AccountContext` を渡す、追加のログイン、作り直し、設定のページの組み直し |
| | `ui/navigation/SessionAccountActions.kt` / `SessionOwnPostActions.kt` | `AccountContext` の中へ |
| | `MainActivity.kt` / `ui/AppContent.kt` | `key(activeKey)` + アカウントごとの owner、端末共通の設定を読む |
| | ViewModel 13 個 (Timeline / Profile / Post / Hashtag / FollowList / PersonalPosts / FollowRequests / Search / AccountReactions / PostReactions / OpenLink / Share)、`ui/feed/PollVoting.kt` | `session` + Repository → `AccountContext` (中身はほぼ引数を消すだけ) |
| | `ui/composer/` (`ComposerSession` / `DraftStore` / `PostEditStore` / `QuickPostBar` / `QuickComposerHost` / `ComposerScreen` / `MediaPickerHost`) | `AccountContext` から取る。`DraftStore` の「別のアカウントなら捨てる」は無くす |
| | `ui/timeline/TimelineDrawer.kt` / `TimelineScreen.kt` | 切り替えの UI |
| | `ui/settings/SettingsScreen.kt` / `TranslationSettingsPage.kt` | 2 つのストア、トップを「アカウントの設定」/「アプリの設定」に分けてページを組み直す、ログアウトの対象 |
| | `ui/share/` | 使っているアカウントで投稿 |
| | `ui/translation/PostTranslationStore.kt` | 端末共通の設定を読む、切り替えで結果を消す |
| | `ui/login/LoginScreen.kt` | 追加のときの戻るボタン |
| debug | `DebugGalleryActivity.kt` / `QuickPostBarGallery.kt` | ギャラリー用の `AccountContext` (または設定) |
| test | 新規 `AccountStoreTest` (移行・重複・削除)、`SettingsPreferencesTest` (分割・移行) | 既存のテストは引数の修正 |
| 文字列 | `values` / `values-ja` | アカウントの追加・切り替え・設定の見出し |
| docs | `AGENTS.md` | パッケージ構成・キャッシュ・設定・ログイン・未対応 |

規模: `session` の参照が data / ui で約 380 箇所。大半は引数を消すだけ。新しく考えるのは `AccountStore` / `AccountManager`、
画面の作り直し、設定の分割、ドロワーの UI。

## 進め方 (各段階でビルドが通る状態にする)

1. **作り直しの確認** (済み): `key()` + アカウントごとの `ViewModelStoreOwner` で前の ViewModel が消え、保存した状態が正しく
   復元されることを小さく試した (上記「切り替え」の「確かめた結果」)。
2. **リファクタ (動きは変えない)** (済み): `AccountContext` を入れ、Repository に session を持たせ、ViewModel・NavHost の配線を替える。
   アカウントは 1 つのまま。計画との違いは下記「段階 2 で決めたこと」。
3. **設定の分割**: `DeviceSettings` / `AccountSettings`、2 つのストア、移行、設定画面のトップの分割。
4. **アカウントごとの保存**: `AccountStore` (移行込み)、アカウントごとのキャッシュ DB。
5. **切り替え・追加・ログアウト**: `AccountManager`、作り直し、追加のログイン、ログアウト。
6. **UI**: ドロワーの切り替え、文字列。
7. **仕上げ**: テスト、エミュレーター + testbed (サーバの違う 2 アカウント、3.5 と最新の安定版) で確かめる、`AGENTS.md`。

## 段階 2 で決めたこと (計画との違い)

* **`AccountContext` を 2 つに分けた**: data 層の `data/account/AccountContext` (session・cache・Repository 6 つ・
  `StreamingClient`) と、UI 側の `ui/account/AccountUiContext` (`account` = 前者・`draftStore` / `panelDraftStore` /
  `postEditStore`・`accountActions` / `ownPostActions`・`composerSession(...)`・`close()`)。`DraftStore` 等は `ui/composer` の
  クラスなので、data 層に置くと `ui → domain ← data` の向きが崩れるため。上の図の `AccountContext` の中身は 2 つを合わせたもの。
  * ViewModel は data 層の `AccountContext` を受ける (中で `account.statusRepository` 等を取り出す)。投稿欄 (`ComposerSession`) も
    `AccountContext` を受ける。`QuasarNavHost` の画面・`ShareActivity`・ギャラリーは `AccountUiContext` を扱う。
  * `SessionAccountActions` / `SessionOwnPostActions` は名前のまま `ui/navigation/` から `ui/account/` に移した。
  * data 層の `AccountContext` にはまだ `close()` が無い (閉じるものが無い。DB を分ける段階 4 で足す)。
* **「今の」アカウントは `AppContainer.currentAccount`** (`StateFlow<AccountUiContext?>`): `SessionStore.session` から作り、セッションが
  変わったら作り直して前のものを `close()` する (Main で行う。作り直しのたびに Repository も新しくなる)。段階 5 の
  `AccountManager.active` の代わりで、そこで置き換える。キャッシュの `clear()` (セッションが変わったとき) と起動時の `trim()` は今のまま。
  ギャラリー (quick-post-bar) は `AppContainer.createAccount(Session(.invalid))` で別に作り、閉じるときに `close()` する。
* `AccountRepository` の `me` (`fetchMe` のメモリのキャッシュ) はセッションをキーにしなくなった (Repository が 1 つのセッションのもの)。
* `DraftStore.open()` は引数無し (無ければ作る。今までどおり `QuasarNavHost` が開く)。「別のアカウントなら捨てる」は無くなった。
  `PostEditStore` の `closeUnlessOwnedBy` も無くなり、ログアウトでは `AccountUiContext.close()` が下書き・編集を止める
  (今までは次にログインするまで下書きのアップロードが続いていた。新しいトークンのセッションでは下書きを捨てていたので、見た目は同じ)。
* `relationshipChanges` / `postChanges` は Repository に付いたままなので、アカウントごとになった。

## 未決

* 設定の項目ごとの置き場所 (表は仮。「要検討」は翻訳の言語ごとの扱い・操作前の確認)。
* 設定画面の「アカウントの設定」/「アプリの設定」それぞれのページの構成。
* 画像の圧縮・メタデータの削除が既定 (オフ) から始まることを、アカウントの追加の後に知らせるか。
* 切り替えの UI の細部。
* `Post.uri` を今回入れるか。
