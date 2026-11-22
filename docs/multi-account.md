# マルチアカウント

複数のアカウントでログインしておき、ドロワーから切り替える。計画の段階 (「進め方」の 6 まで済み。残りは仕上げ)。

## 要求と決めたこと

* 切り替えはドロワー (`TimelineDrawerSheet`) から。切り替えたら画面を作り直してよい (バックスタックは `[Timeline]` に戻る)。
* アカウントをまたいだ操作 (別のアカウントでお気に入り等) は今回は作らない。ただし後から足せる形にしておく (下記「将来」)。
* 設定は**アカウントごとのもの**と**端末共通のもの**に分ける。新しく足したアカウントのアカウントごとの設定は**既定値**から
  始める (コピーしない)。その分、再設定の手間が出ないよう、アカウントごとにするのは本当にアカウント・サーバに依存するものに絞る。
* ログアウトしたら、そのアカウントの設定 (アカウントごとの分)・キャッシュは消す。
* 書きかけの投稿 (下書き) はアカウントごとに持ち、切り替えて戻っても残る (プロセスが終わると消えるのは今と同じ。ログインし直しでも
  消える。下書きの永続化は TODO)。
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
| 翻訳の言語ごとの扱い (`defaultMode` / `languageModes`) | 端末 | 読める言語は人で決まる。サーバの言語圏で変えたい人もいるかもしれない |
| ポストのメニューに出さない項目 (`hiddenPostMenuItems`) | 端末 | 操作の好み |
| 操作前の確認 (`confirmations`) | 端末 | 誤操作の防止は人の癖。仕事用のアカウントだけ投稿を確認したい等ならアカウントごと |
| 大きな画像の圧縮 (`compressOversizedImages`) | アカウント | 投稿の添付の扱いはアカウントの用途で変えたい。上限 (`image_size_limit`) もサーバごと |
| 画像のメタデータの削除 (`stripImageMetadata`) | アカウント | 同上。足したアカウントは既定 (オフ) から始まるので、追加の後に設定し直す必要がある (追加の直後に知らせはしない) |
| 通知の取得 API (`notificationApi`) | アカウント | サーバの版で使えるかが違う (3.5 は v2 が無い) |
| カラムごとの自動更新 (`StreamingSettings`) | アカウント | サーバの流量 (大きなサーバのグローバル等) で決まる |
| 新しい投稿の公開範囲 (`defaultVisibility`) | アカウント | アカウントの用途 |

* この表で決定 (段階 6 の後に確認。翻訳の言語ごとの扱い・操作前の確認も端末共通のまま)。
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
* 細かい形は実装時に詰める (下記「段階 6 で決めたこと」)。

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
3. **設定の分割** (済み): `DeviceSettings` / `AccountSettings`、2 つのストア、移行、設定画面のトップの分割。
4. **アカウントごとの保存** (済み): `AccountStore` (移行込み)、アカウントごとのキャッシュ DB。計画との違いは下記「段階 3・4 で決めたこと」。
5. **切り替え・追加・ログアウト** (済み): `AccountManager`、作り直し、追加のログイン、ログアウト。計画との違いは下記「段階 5 で決めたこと」。
6. **UI** (済み): ドロワーの切り替え、文字列。下記「段階 6 で決めたこと」。
7. **仕上げ** (済み): テスト、エミュレーター + testbed (サーバの違う 2 アカウント、3.5 と最新の安定版) で確かめる、`AGENTS.md`。
   下記「段階 7 で確かめたこと」。

## 段階 2 で決めたこと (計画との違い)

* **`AccountContext` を 2 つに分けた**: data 層の `data/account/AccountContext` (session・cache・Repository 6 つ・
  `StreamingClient`) と、UI 側の `ui/account/AccountUiContext` (`account` = 前者・`draftStore` / `panelDraftStore` /
  `postEditStore` (`OwnPostActions` も兼ねる)・`accountActions`・`composerSession(...)`・`close()`)。`DraftStore` 等は `ui/composer` の
  クラスなので、data 層に置くと `ui → domain ← data` の向きが崩れるため。上の図の `AccountContext` の中身は 2 つを合わせたもの。
  * ViewModel は data 層の `AccountContext` を受ける (中で `account.statusRepository` 等を取り出す)。投稿欄 (`ComposerSession`) も
    `AccountContext` を受ける。`QuasarNavHost` の画面・`ShareActivity`・ギャラリーは `AccountUiContext` を扱う。
  * `SessionAccountActions` / `SessionOwnPostActions` は名前のまま `ui/navigation/` から `ui/account/` に移した (後に
    `SessionOwnPostActions` は無くし、`PostEditStore` が `OwnPostActions` を実装する)。
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

## 段階 3・4 で決めたこと (計画との違い)

まだ 1 アカウントずつ使う (ログインで足して使う・ログアウトで使っているものを除く)。`AccountManager`・切り替え・追加のログイン・
`key(active)` は段階 5・6。

### アカウントの保存 (`data/account/`)

* **`AccountKey` は String の value class** (`@JvmInline`、`hashCode` は中身の String のもの)。値は `UUID.randomUUID()`。
* **`AccountStore`** (`SessionStore` を置き換え): SecureStorage の 1 項目 `accounts` に `StoredAccounts` (`accounts: List<StoredAccount>` +
  `activeKey`) の JSON。一覧と使っているアカウントを一緒に書き換えるため 1 項目にした。`StoredAccount` = `key` / `serverUrl` /
  `accessToken` / `client` / `accountId?` / `profile: StoredProfile?` (acct・表示名・アイコンの URL (static も)・絵文字・bot)。`toString` は
  トークン・クライアントを出さない (`Session` も)。
  * 公開するのは `state` (全体。使っているアカウントは `state.active`)。`addLogin` / `remove` / `updateProfile`。変更は `StoredAccounts` の
    純粋関数 (`withLogin` / `without` / `withProfile`)。保存先は `KeyValueStore` (interface。実体は `SecureStorage`、テストは Map) なので
    JVM でテストできる (`AccountStoreTest`)。
  * `SecureStorage` の書き込みは `commit` (同期) にした。移行・ログインの直後にプロセスが終わって一覧を失うと、キーが振り直されて
    アカウントごとのファイルと結びつかなくなるため (値は小さく、書くのはログイン・ログアウト・表示用の情報が変わったときだけ)。
  * 表示用の情報と `accountId` は、`AppContainer` がアカウントの `myAccount()` (`fetchMe` の結果・プロフィールの編集) を見て埋める
    (変わっていなければ書かない)。設定画面のトップの見出し・ログアウトの補足 (`@acct@host`) がこれを使う。
* **同じアカウントの判定** (段階 5 で ID の無い場合の扱いを変えた。下記「同じアカウントを 2 つ持たない」): サーバ (末尾の `/`・大文字小文字は無視) と `accountId` が同じもの。どちらかの `accountId` が無ければ
  別のものとして足す (移したアカウントは最初の `fetchMe` まで ID が無いので、その間に同じアカウントでログインすると 2 つになる。
  今はログアウト中にしかログインできないので起きない)。同じなら足さずにトークン・クライアント (・表示用の情報) を差し替え、キーは
  そのまま。古いトークンは無効化する。
* **ログインで `verify_credentials` を呼ぶ** (`OAuthLogin.exchange`)。ID・表示用の情報を確かめてから `addLogin`。失敗してもログインは
  する (段階 5 で、失敗したらログインを失敗にするよう変えた) (ID 無しで足し、`fetchMe` で埋まる)。ログには失敗の種類だけ。
* **ログアウト**: `OAuthLogin.logout` は `AccountStore.remove(active)` と無効化だけ。ファイルの削除は `AppContainer`: `active` が変わったら
  前の `AccountUiContext` を `close()` し (DB を閉じる)、一覧から消えたキーなら `deleteAccountData` (設定のコルーチンを止め、
  `cacheDir/api-cache/<key>/` と `settings_<key>.preferences_pb` (+ 書き込みの途中のファイル `.preferences_pb.tmp`) を消す)。途中でプロセスが終わった分は、**起動時に一覧に無いキーの
  ファイルを消す**ことで片付く (分ける前のキャッシュの削除も同じ所)。
* **移行**: `accounts` が無ければ `session` (以前の SecureStorage の 1 つのセッション)、それも無ければ平文の SharedPreferences
  `session` を 1 つ目のアカウントにして使うアカウントにする。新しい形を書いてから古いものを消す (途中で終わっても次の起動で
  新しい形を読むか、やり直す)。トークンの無い保存はログアウト状態。
* `AppContainer.currentAccount` はそのまま (`AccountStore.active` から作る。段階 5 で `AccountManager.active` に置き換える)。作り直しは
  **前のものを閉じてから**作る (トークンの差し替えでは同じキーの DB を開くため)。

### 設定 (`data/settings/`)

* `SettingsStore<S>` (型の別名 `DeviceSettingsStore` / `AccountSettingsStore`) + 項目の表 `SettingsSchema<S>` (`DeviceSettingsSchema` /
  `AccountSettingsSchema`)。`SettingFields` を 2 つの表に分け、キーの名前は変えていない。`AppSettings` は無くなった。
* **アカウントの設定のストアは `AppContainer` がキーごとに持つ** (`AccountContext` は受け取って `settings` で出すだけ)。DataStore は
  1 つのファイルにプロセスで 1 つしか作れず、トークンの差し替えで `AccountContext` を作り直すと同じファイルのものを作ってしまうため。
  ログアウト (`deleteAccountData`) でコルーチンを止めて外す。
* **移行は DataStore の `DataMigration`** (`LegacyAccountSettingsMigration`): アカウントの設定を最初に読むときに、端末共通のファイル
  (= 分ける前のファイル) に残っているアカウントごとの項目を写し (こちらに既にある項目は上書きしない)、写せたら端末共通のファイル
  から消す。途中で終わっても次に開いたときにやり直す。純粋関数 (`hasAccountSettings` / `withLegacyAccountSettings` /
  `withoutAccountSettings`) は `SettingsPreferencesTest`。
  * 移行を付けるのは保存しているアカウント (ログインしたアカウント) の設定だけ。ギャラリーのアカウントには付けない。
  * 移し終えると何もしないので、後から足したアカウントは既定値から始まる。**ログアウト中に更新した場合は、次にログインした
    アカウントに移る** (その人の設定なので捨てなかった)。
* 端末共通のファイルに残ったアカウントごとのキー (移す前) は、端末共通の設定としては読まないので害は無い。

### 設定画面

* トップ: 「アカウントの設定 (@acct@host)」= Timelines (自動更新・通知 API) / Posting (公開範囲・大きな画像の圧縮・メタデータの削除)、
  「アプリの設定」= Display / Post menu / Confirmations / 実験的機能。その下にライセンス・ログアウト (補足に `@acct@host`)。
  ログアウト中は「アカウントの設定」とログアウトを出さない (今は設定画面にはログイン中しか来ない)。
* 両方が混ざっていたページは最小限に振り分けた: Timelines にあった「ポストのメニュー」への入口はトップ (アプリの設定) へ。
  Posting にあった「投稿前に確認」は Confirmations の先頭 (Posting の見出しで「Post」) へ。
* ページの中身は今までどおりエントリの中で読む (`WithDeviceSettings` / `WithAccountSettings`。後者は `currentAccount` の設定)。
* ギャラリーの timeline-settings は設定のトップ + Timelines (ギャラリーのアカウントの設定) + Post menu (本物の端末共通の設定)。
  ギャラリーのアカウントは `rememberGalleryAccount` (`AccountKey("gallery")`・`.invalid`。quick-post-bar と共通)。一覧に無いので、
  ファイルは次の起動で消える。

### キャッシュ

* `CacheDatabase.create(context, key)` → `cacheDir/api-cache/<key>/api-cache.db`、`setCacheBehaviorGroup` はアカウントのディレクトリに
  付け、親 (`api-cache`) からは外す。`AccountContext` が DB を持ち、作ったときに `trim()`、`close()` で閉じる (`scope` も止める)。
  `AccountUiContext.close()` が呼ぶ。
* `EntityCache.clear()` / `CacheDao.clear()` は使わなくなったので消した。

### エミュレーター + testbed で確かめたこと

* 前のコミットのビルド (user2@stable でログイン、長文の折りたたみ 4 行・アイコンの形・投稿前の確認・通知 API = Individual・
  公開範囲 = 未収載・グローバルの自動更新・メタデータの削除を変えた状態) の上に `adb install -r`: ログインしたまま、端末共通の
  ファイルには表示・確認・ポストのメニューのキーだけが残り、アカウントのファイル (`settings_<key>`) に 5 つのキーが移った。
  分ける前の `api-cache/api-cache.db` は消え、`api-cache/<key>/` ができた。ストリーミングは public を含み、通知は v1 で取った。
* 設定画面で両方を変えて再起動しても残る。ログアウトでアカウントの設定・キャッシュのディレクトリが消え (端末共通は残る。
  `/oauth/revoke` 200)、user1 でログインし直すと既定値から始まった。プロセスの再生成・コールドスタートも問題なし。

## 段階 5 で決めたこと (計画との違い)

### `AccountManager` (`ui/account/AccountManager.kt`)

* 置き場所は `ui/account/` (計画の図では `AppContainer` の下の data 寄りのもの)。持つのが `AccountUiContext` (UI 側) なので、
  data 層に置くと向きが崩れる。`AppContainer.accountManager` が 1 つ持つ。`AppContainer.currentAccount` は無くなった。
* `AccountKey → AccountUiContext` を持ち、**使うアカウントになったときに作る** (計画の「必要になったら作る」。今は使っている
  アカウントにしか要らない)。切り替えても閉じない (下書きが残る)。閉じるのは一覧から消えたとき・トークンが変わったときだけ
  (トークンが変わったとき = ログインし直しでも下書きは消える)。
  使っていないアカウントの依存は画面が無いので通信しない (ストリーミングは画面が collect している間だけ)。
* 出すのは `active: StateFlow<ActiveAccount?>` (`context` + `generation`)・`switchTo(key)`・`logout(key)`・`context(key)` (キーの
  依存。開いていなければ作り、使っているアカウントは変えない。共有の画面が使う)。`context(key)` で受けた画面は
  `AccountUiContext.closed` (ログアウト・ログインし直しで閉じられた) を見る。
* 状態は `AccountStore.state` を Main で collect して合わせる (切り替え・ログイン・ログアウトはどれも `AccountStore` を
  書き換えるだけ)。合わせ方: 一覧から消えた・セッションが変わったキーの依存を閉じる → 一覧から消えたキーのファイルを消す
  (このプロセスで開いていないものも) → `activeKey` の依存を (無ければ作って) `active` にする。段階 3・4 の不変条件 (閉じてから開く・
  閉じてから消す・起動時に一覧に無いキーを消す) はそのまま。
* **`generation`**: そのキーの依存をこのプロセスで作り直した回数 (最初は 0)。同じアカウントでログインし直すとキーが同じまま
  依存 (セッション) が新しくなるので、画面の作り直しのキー (`key(アカウントのキー, generation)`) とアカウントごとの ViewModelStore の
  キーに入れる。保存した状態のキー (`SaveableStateProvider`) はアカウントのキーだけ。
  * 差し替えた後にプロセスが終わると、保存した状態の合成キーに 1 以上の generation が入っていて新しいプロセス (0) と合わないので、
    復元されず `[Timeline]` から始まる (まれなので受け入れた)。
* 使っているアカウントは `AccountStore` に保存する (`setActive` → `StoredAccounts.withActive`)。コールドスタートは最後に使った
  アカウントから。

### 画面の作り直し (`ui/account/AccountScope.kt`)

* 計画のコードのとおり (`key()` + アカウントごとの owner + 外側の `rememberSaveableStateHolder()` + 使っていないキーの
  `removeState` + `onDispose` で lifecycle が CREATED 以上なら owner を clear)。`AccountScope(active, accountKeys, stores) { account -> ... }`
  に包み、MainActivity は `QuasarNavHost(container, account, ...)` を中に置く。
  * 保存のキーは `AccountKey` の値 (String)。value class を `Any` で渡すと箱 (`AccountKey`) になり、Bundle に入らない。
  * ログアウト中のキーは `""`。`removeState` は「保存しているキー + このプロセスで使ったキー + `""`」から使っているキーを除いたもの
    (ログアウトしたキーの分も消える)。**使っているキーには `removeState` しない** (`SaveableStateHolderImpl` は登録中のものを外して
    しまい、以後保存されない。逆アセンブルで確かめた)。
  * 同じキーのまま作り直す (generation が変わる) とき、新しい `SaveableStateProvider` は作り直しの組み立ての時点の保存 (使っている
    間は空) から始まり、前のものが破棄で保存した分は新しいものの登録 (`savedStates -= key`) で消える。forget が remember より先に
    届くので `require(key !in registries)` にも当たらない (エミュレーターで同じアカウントを足し直して落ちないことを確かめた)。
* `QuasarNavHost` はアカウントを引数で受ける (作り直されるまで変わらない)。「ログインしたら Login を外す / ログアウトしたら
  `[Login]` に戻す」作り直しは無くなり、ログアウト中に復元したバックスタックが `[Login]` でなければ直すだけ。
  `TimelineEntry` の ViewModel のキー (トークンのハッシュ) も要らなくなった。
* `ShareActivity` は開いたときに使っていたアカウントで投稿し、開いている間に MainActivity で切り替えても追わない (キーを
  `rememberSaveable` で持ち、依存は `AccountManager.context(key)`。ViewModel のキーは `share#<アカウントのキー>`)。追うと入力が消え、
  前のアカウントの下書きがアップロードを続けていた。そのアカウントの依存が閉じられたら (`closed`。ログアウト・ログインし直し)
  Toast を出して閉じる。未ログインで開いたときだけ、ログインされるのを待って使っているアカウントにする。将来この画面でアカウントを
  選べるようにするときは、選んだキーに替える (下書きの中身の引き継ぎは別に要る)。外から開くリンク (`OpenLink`) は
  `QuasarNavHost` の中なので、使っているアカウントで引く。

### 追加・ログアウト

* 追加: ドロワーの「アカウントを追加」で `Route.Login` を積む。`LoginScreen(onBack = ...)` はログイン中だけ戻るボタン
  (タイトル「アカウントを追加」) を出し、押したら進行中のログインもやめる (トークンに換えている途中なら止めない。できたら切り替わる)。
  システムの「戻る」では止めない (後からブラウザで認可を終えたら、そのアカウントが足されて切り替わる)。
  ログインの流れ・`PendingAuthorization` は今のまま 1 つ。`addLogin` が使うアカウントにするので、`AccountManager` が切り替える。
* ログアウト: 設定画面のトップから `AccountManager.logout(key)` (その画面のアカウント) → `OAuthLogin.logout(key)` (`AccountStore.remove` + 無効化)。残りの
  先頭に切り替わる (`StoredAccounts.without`)。依存を閉じてファイルを消すのは `AccountManager`。

### 同じアカウントを 2 つ持たない・自動では除かない

* **不変条件: サーバ + `accountId` が同じアカウントを 2 つ保存しない**。一時的にも並ばない (並んだ後にまとめる形は取らない)。
* **保存しているアカウントを自動では除かない** (除くとそのアカウントの設定・キャッシュも消える)。トークンが使えなくなったら
  「ログインし直す」に案内し、除くのはユーザーのログアウトだけ。
* ID の無いアカウントがありうるのは、**以前の保存 (1 つのセッション) から移したアカウントだけ**で、最初の `fetchMe` まで。
  * ログインで足すアカウントは必ず ID を持つ: トークンを得た後の `verify_credentials` に失敗したらログイン自体を失敗にする
    (`LoginStatus.Failed`。通信なら `Network`、サーバが断ったら `Rejected`)。得たトークンは無効化を試み、`addLogin` は呼ばない
    (`addLogin` / `withLogin` の `accountId` は non-null)。
  * 依存を開いたら必ず `fetchMe` を呼ぶ (`AppContainer.openStoredAccount`。表示用の情報も新しくなる)。移したアカウントは使った
    時点で ID が埋まる。
* 足すときは `addLogin` の前に、**同じサーバの ID の無いアカウント** (`StoredAccounts.withoutIdOn`、`AccountStoreTest`) の ID を
  それぞれのトークンで `verify_credentials` を呼んで埋める (`OAuthLogin.resolveMissingIds` → `AccountStore.updateProfile`)。これで
  `withLogin` の「同じアカウントなら差し替え」が効く。埋められなければ足すのを失敗にし (得たトークンは無効化を試みる)、
  理由ごとに文言を分ける (`storedTokenError`、`OAuthTest`。Mastodon の `verify_credentials` / `Api::BaseController#require_user!`):
  * 401 (トークンが無効) / 422 (トークンにユーザーが無い) → `ExistingAccountNeedsLogin`: そのアカウントに切り替えて「ログインし直す」か、
    ログアウトしてもらう。
  * 403 (アカウントが無効・凍結・メール未確認・承認待ち) → `ExistingAccountUnavailable`: 今はサーバで使えない。案内は同じ。
  * 通信・5xx 等 → `Network` / `Rejected` (後でもう一度)。
  * ログには失敗の種類 (HTTP の状態コード・例外のクラス名) だけを出す。
* 以前の計画のメモ (段階 3・4) にある「ID が無ければ別のものとして足す」は無くなった。

### ログインし直す

* 設定のトップのアカウントの部分 (ログアウトの上) に「ログインし直す」(`Route.Relogin`)。使っているアカウントのサーバで
  `LoginScreen(relogin = ReloginTarget)` を開く (サーバは変えられない。戻るボタン付き)。トークンが無効になったとき等に使う。
* 途中の状態 (`PendingAuthorization`) に対象のキー (`reloginKey`) を持たせる (1 つだけなのは今と同じ)。トークンを得たら
  `verify_credentials` (失敗ならログインを失敗にする) → `AccountStore.relogin` (純粋関数 `StoredAccounts.withRelogin`、`AccountStoreTest`):
  * サーバが違う・保存している ID と違う → `DifferentAccount` (「別のアカウントでログインしました」)。保存しているものは触らず、
    得たトークンは無効化を試みる。
  * 保存している ID が無い (移したアカウント) → 受け入れて ID を埋める。ただしその ID が他に保存しているアカウントのものなら
    `AlreadyAdded` (差し替えると同じアカウントが 2 つになる)。
  * 対象がもう無い (認可の間にログアウトした) → 新しく足すのと同じに扱う。
  * 差し替えたら同じキーのまま (設定・キャッシュが残る) 使うアカウントにし、古いトークンは無効化を試みる。依存は
    作り直され (`generation` が変わる)、画面はタイムラインから。**下書き・編集中の投稿・アップロード中の添付は消える**
    (`ComposerSession` は古いトークンの依存を持つので、そのままは引き継げない)。下書きの永続化は TODO (永続化すれば
    ログインし直しの後に新しい依存で開き直せる)。
  * システムの「戻る」で離れても認可待ちは残る (追加と同じ)。その間に別のアカウントへ切り替えていても、後から認可を終えたら
    対象のアカウントに切り替わる (認可を終えたアカウントを出すのが自然で、追加とも揃う)。
* アカウントの追加・ログインし直しでは、認可の URL に `force_login=true` を付ける (`authorizeUrl(forceLogin = true)`、`OAuthTest`。
  ドキュメントでは Mastodon 2.6.0 から「同じサーバの複数のアカウントで認可するのに要る」)。保存しているアカウントが無いとき
  (最初のログイン) は付けない。
  * **ただし実際にはログインの画面は出ない** (3.5.19・最新安定版の testbed で確かめた)。サーバの実装
    (`Oauth::AuthorizationsController`) では、同じクライアントの有効なトークンがあるときに認可画面を飛ばして自動で承認するのを
    止めるだけで、ブラウザのログインはそのまま。Quasar はログインのたびにクライアントを登録するので、もともと自動の承認は
    起きず、今は効いていない (害も無い)。認可画面は「ログイン中: @user」(4.x は上部、3.5 も表示あり) とログアウトのボタンを
    出すので、別のユーザーで認可するにはそこでブラウザ側のログアウトをしてもらう。

### 翻訳

* `PostTranslationStore.clearResults()`: アカウントのキーが変わったら (`AppContainer` が `active` を見て呼ぶ) 訳・言語の推定の結果と
  順番待ちを捨てる。推定の途中のものは世代 (`detectionGeneration`) で結果を捨てる。モデルはそのまま。

### ドロワー (仮)

* 確かめるための最小限 (他のアカウントの行 +「アカウントを追加」) を置いた。段階 6 で置き換えた (下記「段階 6 で決めたこと」)。

### エミュレーター + testbed で確かめたこと

user1@stable (最新安定版) でログインしている前の段階のビルドの上に入れ、user2@legacy (3.5.19) を足して確かめた (debug、`small_phone`)。

* 追加: ドロワーの「アカウントを追加」→ 戻るボタン付きのログイン → ブラウザで認可 → legacy のタイムラインに切り替わった
  (投稿欄のアイコン・ドロワーの見出しも legacy、他のアカウントの行に stable)。戻るボタンで今のアカウントに戻れる。
* 切り替え (両方向): タイムライン・通知はそのアカウントのもの。ストリーミングは前のものが閉じ、新しいアカウントのサーバに
  繋ぎ直した (logcat の `StreamingClient`)。クイック投稿バーの書きかけは、それぞれ切り替えて戻っても残った。
* アカウントごとの設定: legacy で通知の Grouping を Individual にすると legacy の通知が出て、stable は Grouped のまま (別のファイル)。
  設定画面の見出し・ログアウトの補足は使っているアカウント。
* 回転 (legacy で検索を開いたまま): 画面・下書きとも残った。プロセスの再生成 (legacy でお気に入りを開いて裏に回し `am kill`):
  legacy のお気に入りの画面に戻り、「戻る」で legacy のタイムライン。コールドスタート (`force-stop`) は最後に使った legacy から。
* 共有 (`ShareActivity`) は使っているアカウント (stable) の投稿欄、「Quasar で開く」は使っているアカウントで引いた。
* 同じアカウント (stable) を「アカウントを追加」で足し直す: 足されずにキーはそのまま (キャッシュのディレクトリも同じ)、古い
  トークンは `/oauth/revoke` 200、画面はタイムラインから作り直され、落ちなかった (同じキーで `SaveableStateProvider` を作り直す道)。
* ログアウト: legacy (使っている) → stable に切り替わり、legacy のキャッシュのディレクトリ・`settings_<key>` が消え、`/oauth/revoke` 200。
  最後の stable → ログインの画面 (戻るボタン無し)、ファイルも消えた。コールドスタートもログインの画面。
* ログインし直す (後から足した。同じく stable + legacy): legacy で通知の Grouping を Individual にしてからログインし直すと、
  キー・キャッシュのディレクトリ・`settings_<key>`・設定の値はそのままで、古いトークンは無効になった (サーバの DB で Quasar の
  トークンは 1 つだけ有効)。ブラウザで別のユーザー (user3) にログインして認可すると「別のアカウントでログインしました」が出て、
  user3 の新しいトークンは無効化され、保存している user2 はそのまま使えた (通知が読めた)。サーバ側 (Rails) で user2 のトークンを
  無効にしてからログインし直しても同じキーのまま戻った。
* ここで確かめていなかったもの (ID の無いアカウントの ID を先に埋める道・Activity が止まっている間の切り替え・詳細のパネルを
  開いたままの切り替え) は段階 7 で確かめた (下記)。

## 段階 6 で決めたこと (ドロワーの切り替え)

* 部品は `ui/timeline/DrawerAccounts.kt`: `DrawerAccountHeader` (上の部分) + `DrawerAccountList` (広げた一覧)。`TimelineDrawerSheet` が
  並べる。描くのは `DrawerAccounts` (`current` / `others` / `onSwitch` / `onAdd`) で、`StoredAccounts.toDrawerAccounts(currentKey, ...)` が
  `AccountStore` の保存している情報 (`StoredProfile`) から作る (通信しない)。`TimelineEntry` (`QuasarNavHost`) が組み立てる。
* 形 (X のアプリ):
  ```
  (icon 48)                              (他 32)
  表示名                                     [v]
  @acct@host
  ── 広げると ──
  (icon 40) 表示名 / @acct@host            (✓)   ← 使っているアカウント (押せない)
  (icon 40) 他のアカウント ...                    ← タップで切り替え
  +         アカウントを追加
  ```
  * 右の小さなアイコン (大きなアイコンと下端を揃える) は他のアカウントの先頭 1 つだけ (`MaxSideIcons`。それより多ければ広げた一覧で選ぶ)。タップで切り替え
    (読み上げは「@acct@host に切り替え」)。押せる範囲は 48dp の円 (アイコンは 32dp) で、名前の行・一覧の行とは重ならない。
  * 上の余白は段階 5 までと同じ (大きなアイコンがステータスバーに近づかないように)。
  * [v] (`KeyboardArrowRight` を回したもの) で一覧を開閉する (`AnimatedVisibility`、motionScheme の spatial / effects)。開閉の状態は
    保存しない (ドロワーを閉じて開き直すと閉じている。切り替えでは画面ごと作り直される)。
  * 名前の部分・大きなアイコンのタップは今までどおり自分のプロフィール。名前・アイコンは投稿欄が取得した新しいもの
    (`account`)、まだ無ければ保存している情報。
  * 表示用の情報の無いアカウント (以前の保存から移した直後で `fetchMe` 前) は、アイコンを `avatarShape()` のプレースホルダ (人のアイコン)、
    名前をサーバのホストにする。その間は自分のプロフィールを開けない (ID が無い)。
  * 名前は `UserName` (カスタム絵文字も出る。bot の印は保存していないので出ない)、アイコンは `UserIcon` (`avatarShape`)。
* **ドロワーの幅は変えない**: アカウントの部分 (上の部分と一覧) は `IgnoreIntrinsicWidth` で幅を決めるのに数えず、長い名前・
  `@acct@host` は省略する (仮の UI では一覧の行で幅が広がっていた)。
* 切り替えると画面ごと作り直されるので、ドロワーは閉じる処理を待たずに消える。切り替えの見た目の遷移は付けていない
  (`AccountScope` の `key()` をまたいでクロスフェードするには前後のアカウントの画面を同時に組み立てる必要があり、簡単ではない)。
* ギャラリー `account-switcher` (`AccountSwitcherGallery`): 固定データ `account-switcher.json` (`[{"server", "account": Account | null}]`、
  `GalleryFixtures.storedAccounts`) で本物の `TimelineDrawerSheet` を出す。チップでアカウントの数 (1 / 2 / 全部) を変え、切り替えは
  手元だけ。長い名前・名前の絵文字 (アニメーション)・表示用の情報の無いアカウント・同じサーバの 2 つ目を含む。
* 確かめたこと (エミュレーター、日本語): ギャラリーのライト / ダーク・閉じた / 広げた・表示用の情報の無いアカウントを使っているとき。
  testbed (stable + legacy) で、広げた一覧の「アカウントを追加」から legacy を足し、右上の小さなアイコンで両方向に切り替えた
  (ストリーミングが繋ぎ直された)。

## 段階 7 で確かめたこと

エミュレーター (`small_phone`) + testbed (stable = 最新の安定版、legacy = 3.5.19)。ユニットテストは全部通る。

* **以前の保存から移したアカウント (ID の無いもの) と同じサーバへの追加**: 分ける前のビルド (`master` の liteDebug) で user4@stable に
  ログイン → stable を止めてから今のビルドに上げて起動 (移行の後の `fetchMe` が失敗し、ID・表示用の情報が無いまま。ドロワーは
  プレースホルダとホスト名) → stable を戻し、そのまま「アカウントを追加」で同じ user4 で認可。一覧は user4 の 1 つだけで (ID が
  埋まって同じアカウントと分かり、トークンの差し替えになった)、サーバでは移した古いトークンが無効になり、有効なのは新しい 1 つ。
* **2 アカウント (legacy の user3 + stable の user4)**: 追加・ドロワーの小さなアイコンでの両方向の切り替え・ストリーミングの繋ぎ直し。
  使っている legacy のログアウトで stable に切り替わり、legacy のキャッシュのディレクトリが消え、サーバで user3 の Quasar の
  トークンが無効になった。
* **詳細のパネルを開いたままの切り替え** (`wm size 1920x1200` で 2 カラム): パネルを 2 段 (プロフィール → フォロー一覧) 積み、
  パネルの投稿欄に下書きを入れたまま、ドロワーから切り替えた。落ちず、パネルは消えて切り替え先は `[Timeline]` から。元の
  アカウントに戻してもパネルはよみがえらず、パネルの下書き (`panelDraftStore`) は残っていた。
  * 気づいたこと: パネルが開いているとき、ドロワー (と暗い膜) はパネルの下に描かれる (アカウントに関係なく、タブレット表示の
    作り)。切り替えのアイコンは左上なので押せる。
* **Activity が止まっている間に使うアカウントが変わる** (アプリには画面の外から切り替える道が無いので、一時的なデバッグ用の
  BroadcastReceiver で `AccountStore.setActive` を呼んだ。コミットしていない): user3 で `[Timeline, Post, Profile]` まで進めて
  ホームに出てから user4 に切り替え、
  * プロセスを残したまま戻る: user4 の `[Timeline]`。user3 に戻すと `[Timeline]` (「戻る」でアプリを抜ける)。
  * プロセスを終わらせて (`am kill`) から戻る (上記「穴」の状況): user4 の `[Timeline]`。同じプロセスで user3 に戻しても古い
    バックスタックはよみがえらない。
  * 対照: 切り替えずにプロセスを終わらせると `[Timeline, Post, Profile]` が復元される。

## 未決だったもの (決めたこと)

* 設定の項目ごとの置き場所: 上の表のまま (翻訳の言語ごとの扱い・操作前の確認も端末共通)。
* 設定画面の「アカウントの設定」/「アプリの設定」それぞれのページの構成: 今のまま。
* 画像の圧縮・メタデータの削除が既定 (オフ) から始まることは、アカウントの追加の後に知らせない。
* `Post.uri` は今回は入れない。アカウントをまたいだ操作 (上記「将来」) を作るときに足す (DTO は `uri` を受けているので、
  `Post` と mapper だけ)。
* 通知の取得 API (v2 / v1) は自動で判定しない。3.5 等のサーバのアカウントは、足した後に設定で Individual にする。
* ドロワーの `@acct@host` が [v] の分だけ省略されるのはそのまま。
* トークンが使えないとき (対応済み): 読み込みの失敗が 401 / 403 なら、エラーの文言の下 (一覧があるタイムラインはスナックバー) に
  「ログインし直す」を出す (AGENTS.md の「Login」)。自動では除かず、ログインし直しへ誘う方針のとおり。testbed でトークンを
  無効にして、タイムライン・プロフィールのタブにボタンが出て、押すとログインし直しの画面が開くことを確かめた (スナックバーと
  403 はユニットテストのみ)。ストリーミングの 401 は再接続を続けるまま。
