# Navigation 3 への移行計画 (Activity を 1 つにする)

状態: **完了** (pt.1〜pt.4)。「決めること」はすべて推奨のとおり。以下は計画時の記述 (経緯として残す)。

## 目的

今は画面ごとに Activity を分けている (Main / Profile / FollowList / Hashtag / Post / Composer / MediaViewer / Settings の 8 つ)。
これを MainActivity 1 つにまとめ、画面の行き来を Navigation 3 (`NavDisplay`) で行う。

解決したいこと:

* **ウィンドウ境界の問題**: Activity の出入りのたびに OS の遷移・ウィンドウの背景・最初のフレームの前が見える
  (ダークテーマで光る等)。画面の切り替えが全部 Compose の中になれば、ウィンドウの背景が見えるのは起動時だけになる。
* **遷移アニメーションを Compose で揃える**: 今は Activity 間が OS 任せで、AGENTS.md の「アニメーションは Compose」と
  ずれている。設定画面のスライド (`SettingsActivity` の `settingsPushTransition` 等) を全画面の既定にする。予測型「戻る」も同じ動き。
* **メディアビューア**: 透過 Activity + 位置を Intent で渡す今の作りを、同じウィンドウ内の重ね表示にする。将来は共有要素の遷移
  (`SharedTransitionLayout`) で、一覧の画像からそのまま広がる・縮んで戻る形にできる。
* **画面間の引数を型付きに**: Intent の extras (`MediaViewerArgs` の JSON 等) を `@Serializable` の `NavKey` にする。
* **Activity ごとに重複しているもの**を 1 つに: `QuasarAppContent`、インライン再生の Player (`rememberInlinePlayback`)、
  表示用の現在時刻、確認ダイアログのホスト。
* 将来のタブレット対応 (一覧 + 詳細の 2 ペイン) の土台 (Navigation 3 の Scene)。

やらないこと (この移行では): 画面の中身・見た目の変更、ストリーミングの方針変更 (下記「決めること」)、PiP の実装。

## 前提: Navigation 3 での振る舞い (今の Activity との対応)

| 今 (Activity) | Navigation 3 |
| --- | --- |
| `Context.openXxx()` で `startActivity` | `backStack.add(Route.Xxx(...))` |
| `finish()` で戻る | `backStack.removeLastOrNull()` (`NavDisplay(onBack = ...)`) |
| Intent extras | `NavKey` のプロパティ (`@Serializable`、`rememberNavBackStack` が保存・復元する) |
| Activity ごとの ViewModel | エントリごとの ViewModel (`rememberViewModelStoreNavEntryDecorator`、`lifecycle-viewmodel-navigation3`) |
| Activity の Lifecycle | **エントリごとの `LocalLifecycleOwner`** (Navigation 3 の既定) |
| 別の Activity を開く = 下の画面は ON_STOP | 上にエントリを積む = **下のエントリはコンポジションから外れる** (状態は `rememberSaveable` で保存) |
| 透過 Activity (ビューア) = 下の画面は STARTED のまま | **OverlayScene** = 下のエントリも表示されたまま (STARTED)。ダイアログ・ボトムシートと同じ仕組み |

下の画面がコンポジションから外れるので、`LaunchedEffect` / `repeatOnLifecycle` で動いているもの (ストリーミング・相対時刻の
更新・インライン再生) は**今と同じく、別の画面を開くと止まる**。挙動を変えずに移せる見込み。

## 全体の形

```
MainActivity (唯一の Activity。windowSoftInputMode="adjustResize")
└── QuasarAppContent (テーマ・設定・確認ダイアログ・インライン再生・ウィンドウの背景。1 つだけ)
    └── QuasarNavHost
        ├── backStack = rememberNavBackStack(Route.Timeline)   ※未ログインなら Route.Login (下記)
        ├── LocalNavigator (画面からの遷移の口。Context.openXxx の置き換え)
        └── NavDisplay(
              entryDecorators = [SaveableState, ViewModelStore],
              sceneStrategy = MediaViewerSceneStrategy (ビューアだけ重ね表示),
              transitionSpec / popTransitionSpec / predictivePopTransitionSpec = スライド (設定画面のものを共通化))
```

### ルート

```kotlin
@Serializable sealed interface Route : NavKey {
    @Serializable data object Timeline : Route
    @Serializable data object Login : Route
    @Serializable data class Profile(val accountId: String) : Route
    @Serializable data class FollowList(val accountId: String, val tab: FollowListTab) : Route
    @Serializable data class Hashtag(val name: String) : Route
    @Serializable data class Post(val statusId: String) : Route
    @Serializable data object Composer : Route
    @Serializable data class MediaViewer(val args: MediaViewerArgs) : Route   // 既に @Serializable
    // 設定画面のページ (今の SettingsRoute を平らにする。入れ子の NavDisplay はやめる)
    @Serializable data object Settings : Route
    @Serializable data object SettingsDisplay : Route
    ...
}
```

### 遷移の口 (`Navigator`)

画面 (`ProfileScreen` 等) は既にコールバックで遷移を受けているので、**画面の中身は変えない**。変えるのは今 Activity が
組み立てているコールバックの出どころだけ。

```kotlin
/** 画面からの遷移。Context.openProfile 等の置き換え。 */
interface Navigator {
    fun openProfile(user: User); fun openPost(post: Post); fun openHashtag(name: String)
    fun openFollowList(accountId: String, tab: FollowListTab); fun openComposer()
    fun openMedia(click: MediaClick); fun openMediaViewer(args: MediaViewerArgs); fun openSettings()
    fun back()
}
val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No Navigator") }
```

* 今の各 Activity の `onCreate` にある配線 (「同じアカウントからは遷移しない」「返信は投稿画面で」等) は、エントリの中身
  (`entry<Route.Profile> { ... }`) にほぼそのまま移す。
* `Context.openXxx()` 拡張は消す (呼び出し元は Activity と `openMedia` の中だけ)。unknown のメディアをブラウザで開く処理は
  `Navigator.openMedia` に残す。

## 段階 (それぞれで動く状態にしてコミット)

### 段階 0: 準備

* 依存: `androidx.lifecycle:lifecycle-viewmodel-navigation3` 2.11.0 (lifecycle と同じ版)。
* 今コミットしていないウィンドウの背景の修正 (`SyncWindowBackground` + `values-night`) は**先にコミットする**。
  Activity が 1 つになっても起動時と、ビューアの重ね表示の下で役に立つ。`values-v34` / ライフサイクルコールバックはやらない。
* 遷移アニメーション (`settingsPushTransition` / `settingsPopTransition`) を `ui/navigation/Transitions.kt` に移して共通化。
* `Route` / `Navigator` / `QuasarNavHost` の骨組み (`ui/navigation/`)。

### 段階 1: 普通の画面を移す (Profile / FollowList / Hashtag / Post / Settings / Composer)

* MainActivity の中身を `QuasarNavHost` にし、Timeline をルートに。各画面をエントリにして、対応する Activity と manifest の
  宣言を消す。メディアビューアはこの段階では**今の Activity のまま** (`Navigator.openMedia` が `startActivity` する)。
* **ログイン**: 今は MainActivity が `session == null` で LoginScreen と Timeline を出し分けている。セッションの有無で
  バックスタックを作り直す (ログイン → `[Timeline]`、ログアウト → `[Login]`) 形にする (Navigation 3 の「条件付きの遷移」)。
  非ログインの経路は今後なくす方針なので、ここでは最低限 (Login 画面を 1 つのルートにするだけ)。
* **ViewModel**: 各エントリで今と同じ `viewModel(factory = ...)`。エントリごとに別の ViewModel になる (同じプロフィールを
  2 回積んでも別)。`TimelineViewModel` のキー (セッション) はそのまま。
* **投稿画面**: `ComposerActivity` の `stateVisible` (開いたらキーボードを出す) は、`ComposerScreen` を単独で開いたときに
  フォーカスを要求して出す。
* **設定画面**: 入れ子の `NavDisplay` をやめ、ページを全体のバックスタックに平らに積む。
* **「戻る」**: 画面の中の `NavigationBackHandler` (投稿の破棄確認・クイック投稿バーのフル画面を閉じる 等) が
  `NavDisplay` より優先されることを確認する (navigationevent の入れ子の優先順位)。

### 段階 2: メディアビューアを重ね表示に

* `MediaViewerSceneStrategy` (自前の `OverlayScene`、ボトムシートの例と同じ形): ビューアのエントリだけ、下のエントリを表示した
  まま上に重ねる。下の画面は STARTED のまま = 今の透過 Activity と同じ扱いなので、「開くときにインライン再生を明示的に止める」
  処理 (`MediaAttachments` / `ProfileHeader`) はそのまま要る。
* 出入りのアニメーションは今どおり `DismissibleViewer` が描く (Navigation 3 の遷移は付けない: エントリのメタデータで無し)。
  閉じ終わったら `back()`。
* **システムバー**: 今は Activity の `enableEdgeToEdge(dark)` で白アイコンにしている。ビューアの表示中だけ
  `WindowInsetsControllerCompat.isAppearanceLight*Bars = false` にし、閉じたら戻す (`DisposableEffect`)。
* `MediaViewerActivity` と `Theme.Quasar.MediaViewer` (透過テーマ) を消す。`SyncWindowBackground` の透過の判定も不要になる。
* 共有要素の遷移 (`SharedTransitionLayout`) への置き換えは**この移行ではやらない** (今の `sourceBounds` の仕組みで動くため)。
  別のタスクにする。

### 段階 3: 後片付け

* Activity ごとだった前提の記述・コードを整理: 「ビューアは透過 Activity なので ON_STOP が来ない」等のコメント、
  `QuasarAppContent(inlinePlayback = false)` の使い分け (ビューアが同じコンポジションになるので、インライン再生の Player を
  ビューアの間どうするか確認)。
* AGENTS.md (パッケージ構成・画面・開発メモ) と `docs/media-viewer.md` を更新。
* デバッグ用ギャラリー (`DebugGalleryActivity`、debug のみ) は Activity のまま残し、中に小さな `NavDisplay` を置いて
  ビューアを重ねる (`Navigator` の実装をギャラリー用に用意)。adb から直接開ける今の使い方を保つため。

## 決めること (レビューで判断してほしい)

1. **ストリーミング**: 今は「別の画面を開く = タイムラインが背面 = 切断」。移行後も、タイムラインのエントリがコンポジションから
   外れるので**同じ挙動になる** (何もしなければ今と同じ)。
   → 推奨: **移行では今の挙動を保つ**。「ポスト画面を見ている間も繋いでおく」等は、移行後に別に決める
   (Activity が 1 つなら、接続をアプリの前面 (ProcessLifecycle) に結び付けるのも簡単になる)。
2. **メディアビューアを移すか**: PiP は Activity 単位の機能なので、移すと「PiP 中は Activity 全体がプレイヤーだけを出す」
   作りになる (YouTube 等と同じ)。Activity のまま残せば PiP は素直だが、透過 Activity の回り道と、ウィンドウ境界の問題は残る。
   → 推奨: **移す (段階 2)**。段階 1 と分けてあるので、段階 1 の後で止める判断もできる。
3. **同じ画面を何重にも積むか**: プロフィール → ポスト → 同じプロフィール… と無制限に積める (今の Activity と同じ)。
   → 推奨: **今と同じく制限しない**。メモリは、下のエントリはコンポジションから外れ、ViewModel (一覧のデータ) だけが残る。
     気になれば後で上限を設ける。
4. **画面遷移のアニメーション**: 全画面を設定画面と同じ横スライドにする。
   → 推奨: **横スライドで統一** (メディアビューアだけ例外)。

## 確認すること (各段階で)

* ユニットテスト (`testDebugUnitTest`) と debug ビルド。
* 実機 / エミュレータで:
  * 各画面への行き来、システムの「戻る」と予測型「戻る」(途中でやめる操作も)、ダークテーマで光らないこと。
  * 投稿の破棄確認・クイック投稿バーのフル画面が「戻る」を先に受けること。
  * タイムライン → ポストを開くとストリーミングが切断され (logcat `StreamingClient`)、戻ると再接続・補完されること。
  * ポスト画面の自動の返信 (開くと返信先が付き、離れると外れる)。
  * 画面回転・プロセスの再生成 (背面に回して `adb shell am kill <pkg>` → 戻る) でバックスタックと引数が復元されること。
  * (段階 2) ビューアを下へスワイプすると後ろの一覧が見え、元の位置へ縮んで戻ること。インライン再生の位置の引き継ぎ。
    システムバーのアイコンの色が出入りで正しく切り替わること。
  * キーボード: クイック投稿バーがキーボードの上に乗ること (`adjustResize` は MainActivity に既にある)、投稿画面を開いたら
    キーボードが出ること。

## 規模の見込み

* Activity は薄い入れ物 (各 60〜110 行) で、画面本体はコールバックを受ける Composable なので、**中心は配線の付け替え**。
  画面の中身はほぼ触らない。
* 新規: `ui/navigation/` (Route / Navigator / QuasarNavHost / Transitions / MediaViewerSceneStrategy) で数百行。
* 削除: 7 つの Activity (Main 以外) と manifest の宣言、`Context.openXxx` 拡張、透過テーマ。
