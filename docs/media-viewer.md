# メディア再生 / メディアビューア

AGENTS.md から参照される詳細。添付メディア (https://docs.joinmastodon.org/entities/MediaAttachment/) の
インライン再生とメディアビューアの設計・注意点をまとめる。再生は Jetpack Media3 (1.11.x) を使う。

## 種別ごとの扱い

| type | 一覧でのタップ | ビューア |
| --- | --- | --- |
| image | ビューアを開く | 元画像を Fit (画面に収まるよう拡大縮小)。読み込み中は preview / BlurHash。ピンチで拡大縮小、ダブルタップで 130% / 等倍 |
| video | ビューアを開く。中央の再生ボタンだけはその場でミュート再生 → 再生中のタップで続きからビューア | 自動再生 (最初はミュート) |
| gifv | 同上 (繰り返し再生、音声トラック無し) | 自動再生・繰り返し |
| audio | 同上 (投稿者アイコンが回る) | 自動再生・**音あり**。投稿者アイコンを中央で回転 |
| unknown | 元ファイル (`originalUrl`) をブラウザで開く | 「ブラウザで開く」ボタンだけのページ (左右スワイプで来たとき) |

* ポストの添付以外 (プロフィールのアイコン・ヘッダ画像) は `MediaViewerArgs.singleImage` + `Navigator.openMediaViewer` で画像 1 枚として開く。
  `meta` が無いので、閉じるときの戻り先の縦横比はビューアで読み込んだ画像のもの (`MediaViewerViewModel.onImageLoaded`) を使う
  (読み込み前に閉じたらその場で縮む)。添付でも `meta` が無ければ同じく読み込み結果を使う。

* **一覧では自動再生しない**。帯域の細いサーバが多いため、インライン再生は中央の再生ボタン (48dp のタッチ範囲) を押したときだけ。
  ぼかし中 (閲覧注意) は再生ボタンを出さず、タップは表示への切り替え。
* URL: 表示・再生は `MediaAttachment.viewUrls` (= `url`, `remote_url` の順) を先頭から試し、読み込みに失敗したら次へ
  (画像は Coil の onError、再生は `onPlayerError`)。保存・ブラウザで開くは `originalUrl` (= `remote_url ?: url`、リモートの原本優先)。

## 構成

```
ui/component/player/            再生部品 (一覧とビューアで共通)
├── PlaybackController.kt   ExoPlayer を 1 つ持ち、「持ち主」1 つだけに再生させる
├── PlayerFactory.kt        createMediaPlayer (OkHttp・±10 秒・音声属性)
├── MediaOverlayColors.kt   メディア上・黒背景上の操作部分の色 (白 / 半透明の黒)。色のハードコードはここだけ
├── MediaPlayer.kt          再生部分の UI (Player を受け取って描くだけ) + MediaPoster + AudioArtwork
├── PlayerControls.kt       InlineControls (残り時間 + ミュート) / FullControls (ダブルタップ + 下端の BottomBar / SeekBar)
├── PlayerState.kt          rememberIsPlaying / rememberHasAudio / formatPlaybackTime / resolveDurationMs
└── InlinePlayback.kt       LocalInlinePlayback / rememberInlinePlayback
ui/component/media/MediaClick.kt   タップの情報 (ポスト + メディア + インライン再生の位置)
ui/mediaviewer/                 ビューア画面
├── MediaViewerEntry.kt     画面 (Route.MediaViewer) の組み立て (ViewModel・保存) と openMedia (unknown はブラウザ)
├── MediaViewerArgs.kt      画面の引数 (@Serializable、Route.MediaViewer が持つ。ViewerMedia はドメインの写し)
├── MediaViewerViewModel.kt PlaybackController を保持 (回転しても再生継続)、ページ切り替えで差し替え。Player の作り方は関数で受け取る
├── MediaViewerScreen.kt    HorizontalPager + 上部バー (閉じる / 位置 / メニュー)。操作部分の表示状態・システムバー
│                           (LocalActivity の window。閉じたら表示・アイコンの色を戻す)・画面点灯 (Modifier.keepScreenOn)・ON_STOP で一時停止 (LifecycleEventEffect)
├── MediaPages.kt           ImagePage / PlayerPage / UnknownPage
├── Zoomable.kt             画像のピンチ拡大縮小 (ZoomState / Modifier.zoomable) + 純粋関数 (applyGesture / clampZoomOffset)
└── DismissibleViewer.kt    出入り (開く・上下スワイプ・戻る・閉じる) + 純粋関数 (rubberBand / shouldDismiss / exitTransformTo / fitSize)
```

### PlaybackController
* Player は最初の `play()` で作る。`play(owner, media, startPositionMs, muted)` で持ち主を切り替え、`stop(owner)` は
  持ち主が自分のときだけ止める。表示側は `isActive(owner)` なら `player` を、そうでなければ `null` を `MediaPlayer` に渡す
  (1 つの Player は 1 つの Surface にしか描けない)。
* 持ち主は一覧では `MediaAttachmentItem` の `remember { Any() }` (同じメディアがブーストと元ポストで 2 か所に出ても区別)、
  ビューアではページの `MediaAttachment` 自身。
* ミュート: `Player.mute()` / `unmute()` (直前の音量を覚える) を使い、同じ状態での重複呼び出しは避ける。
  **ミュート中は音声フォーカスを取らない** (他のアプリの音楽を止めない)。音を出すときだけフォーカスとイヤホン抜けの一時停止を有効にする。
* シーク幅は `ExoPlayer.Builder.setSeekBack/ForwardIncrementMs(10_000)` (既定は 5 秒 / 15 秒)。通信はアプリ共通の OkHttpClient。

### インライン再生
* `QuasarAppContent` がアプリで 1 つの `rememberInlinePlayback()` を `LocalInlinePlayback` で配る。`PostList` → `PostCard` → `MediaAttachments` (引用・ピン留め含む) に引数を通さずに済む。
* 止めるタイミング: 項目が LazyColumn から外れた・別の画面を開いて画面ごとコンポジションから外れた (`DisposableEffect`)、
  閲覧注意のぼかしに戻した、アプリの `ON_STOP` (Player を解放してデコーダを手放す)。
* **ビューアを開くときは明示的に止める**。ビューアは下の画面に重ねて出す (`OverlaySceneStrategy`) ので、下の画面はコンポジションに残り `STARTED` のまま。
  再生位置とミュート状態を `PlaybackSnapshot` に取ってから止め、ビューアはその位置から続ける。戻ったときに一覧側は再開しない。

### ビューア
* 出入りのアニメーションはすべて Compose (`DismissibleViewer` / `DismissibleViewerState`) で描く。ビューアは
  `OverlaySceneStrategy` で下の画面に重ねるので、`NavDisplay` の遷移アニメーションは付かない。
  * 開く: 閉じるときの逆回しで、一覧の元の位置 (閉じるときの戻り先と同じ) から 300ms で広がる。中身は最初の 1/4 で現れて
    下のサムネイルから引き継ぎ、背景の黒は全体で濃くなる。元の位置が分からない (位置不明・縦横比不明。プロフィールのアイコン等は
    開く時点では読み込み前なので縦横比が無い) ときは 350ms でフェードイン (OS 標準のフェードイン 500ms の 0.7 倍)。
    最初のレイアウトで画面の大きさが決まってから始める。回転等では繰り返さない。開く途中で閉じたら、その時点の見た目から縮んで戻る。
  * 閉じる: 上下スワイプ・「戻る」・閉じるボタンのどれでも `DismissibleViewerState.dismiss()` で、一覧の元の位置へ縮んで戻る
    (下記)。終わったら `back()` でバックスタックから外す。
  * 予測型「戻る」: `NavigationBackHandler` + `rememberNavigationEventState` の進み具合 (0〜1) に合わせて、上下スワイプと同じく
    縮み・背景が薄くなる。確定で閉じ、取り消しで元に戻る。
* 上下スワイプで閉じる:
  * ドラッグ中はゴムのように引っかかる (`rubberBand`: 動き始めは指の 0.55 倍、遠くなるほど動かず、画面高の 30% を超えない)。
    進むにつれて最大 15% 縮み、背景の黒が薄くなる (下の画面に重ねているので元の画面が透ける)。
  * 見た目で画面高の 8% 以上動かす (指で 1/4 画面ほど) か、動かした向きに速く弾いたら閉じる。戻す向きに弾いたら閉じない。
    それ以外はばねで戻る。
  * 閉じるときは、一覧での元の位置 (`MediaViewerArgs.sourceBounds`、`MediaAttachments` が各メディアの位置を
    `onGloballyPositioned` で測って `MediaTap` に付ける) へ 300ms で縮みながら移動し、最後の 1/4 で消えて下のサムネイルに
    引き継ぐ。Fit 表示の矩形とサムネイル (Crop、縦横比は 1/3〜3 に丸め) の縦横比が違うことがあるので、拡大率は幅・高さの比の
    相乗平均。戻り先が無い (位置不明・メディアの縦横比不明・回転等でウィンドウの大きさが変わった) 場合はその場で縮んで消える。
  * 操作中 (開く途中・スワイプ・「戻る」の途中・閉じる途中) は上部バー・再生操作を隠す (`DismissibleViewerState.isActive`)。
* 画像のピンチ拡大縮小 (`Modifier.zoomable`):
  * 拡大率は 1〜5 倍。指の中心の下にある点が指に付いてくるように拡大し、拡大した画像の端が画面の内側に入らない範囲でだけ
    動かせる (画像が画面より小さい向きは中央に固定)。範囲は実際の画像の縦横比 (Coil の読み込み結果、無ければ `meta`) から求める。
  * ジェスチャの取り合い: 2 本指、または拡大中の 1 本指の動きだけを受け取って消費する。拡大していない時の 1 本指は消費しないので、
    ページ切り替え (HorizontalPager) と上下スワイプで閉じる操作にそのまま渡る。2 本指で消費されると、親のそれらの操作は
    タッチスロップ前に取り消されるので、ピンチの途中でページが動いたり閉じたりしない。
  * 拡大中は `HorizontalPager(userScrollEnabled = false)` と `DismissibleViewer(swipeEnabled = false)` にする。
    拡大したまま端まで動かして次のページへ送ることはできない (縮小してから切り替える)。
  * 1 回タップ (操作部分の出し入れ) は、指が動いて消費された時点で取り消されるので、ピンチがタップにはならない。
  * ダブルタップ: 等倍ならタップした点を中心に 130% (`DoubleTapZoomScale`) へ、拡大中 (ピンチで 130% を超えていても) なら
    等倍へ 250ms で戻す。途中もタップした点が動かないよう、拡大率から毎フレーム移動量を求める (`applyGesture` を使い回す)。
    1 回タップと同じ `detectTapGestures` で区別するので、1 回タップ (操作部分の出し入れ) はダブルタップ待ちの分だけ遅れて反応する。
    アニメーション中に指が触れたら止めて指の操作を優先する。
  * 拡大状態はページごと (ページから外れると戻る)。「戻る」・閉じるボタンは拡大中でもそのまま閉じる。
* 下端の再生操作は Google フォトの動画再生を参考に控えめにしている: 帯状の背景は敷かず下からの薄いグラデーションだけ、
  1 行目に 再生/一時停止 (左)・「現在 / 全体」(中央)・ミュート (右)、その下に細いシークバー (M3 `Slider` のトラックを 4dp、
  つまみを 4x20dp のバーにしたもの。ドラッグ中は時刻もその位置を出し、離したときにシーク)。中央の大きな再生ボタンは無い。
  つまみの位置は `SliderState` が持ち、触っていない間だけ再生位置を `snapshotFlow` で写す (コンポジション中に状態を書かない)。
  Media3 の `ProgressSlider` はトラック・つまみを差し替えられないので使っていない。
* 操作部分 (上部バー・再生操作・システムバー) はタップで出し入れし、再生中は 3 秒で自動的に隠す。
  システムバーは隠しても端からのスワイプで一時的に出る。操作部分の余白は `systemBarsIgnoringVisibility` で取る (出し入れで位置がずれない)。
* ダブルタップは左右 1/3 で ±10 秒 (連打で「+20s」と積み上がる)。中央のダブルタップは何もしない。
* ページ切り替え時のミュート: video / gifv はページをまたいで直前のミュート状態を引き継ぎ、audio は常に音ありで始める。
* ミュートボタンは音声トラックがあるときだけ出る (Mastodon の gifv には無い)。
* 再生中は画面を消さない (`View.keepScreenOn`)。開発機はアニメーション速度 0.5 倍の設定なので、アニメーションの見た目は設定値の半分。

## 描画は TextureView
`ContentFrame` の既定は SurfaceView だが、一覧・ビューアとも `SURFACE_TYPE_TEXTURE_VIEW` にしている。
SurfaceView は Compose の `graphicsLayer` の alpha (閉じるときのフェード) やスクロール中の位置に追従しないことがあるため。
実機で、TextureView ならフェード中に元の画面と映像が正しく重なることを確認した。PiP 対応時に SurfaceView へ戻すなら、
フェードは背景だけにする等の調整が要る。

## PiP への準備 (未実装)
* 再生を止めるのは `ON_PAUSE` ではなく `ON_STOP` (`MediaViewerScreen` の `LifecycleEventEffect` → `viewModel.pause()`)。PiP 中は onPause のまま
  再生を続けるため。
* ビューアは MainActivity の中の画面なので、PiP は Activity 全体で入り、PiP 中はビューアのプレイヤーだけを出す作りにする
  (PiP 中は他の画面・システムバーを描かない)。`supportsPictureInPicture` と、PiP の出入りで作り直さないための
  `configChanges` (`screenSize|smallestScreenSize|screenLayout|orientation`) は MainActivity にまだ付けていない。
  回転で Activity が作り直されても、Player は ViewModel (画面ごと) が持つので再生は続く。
* 操作部分は `FullControls(visible = ...)` の 1 フラグで隠せる。PiP 中は `onPictureInPictureModeChanged` から `false` にする。
* 再生部品は `Player` インターフェースにだけ依存する (将来 MediaSession と組み合わせやすい)。

## 保存
システムの DownloadManager に任せる (`data/media/MediaDownloader.kt` の `SystemMediaDownloader`)。**権限は宣言しない**。
* 取得元は `originalUrl` (リモートの原本優先)。保存先・ファイル名・MIME は純粋関数 `mediaSaveTarget` (`domain/model/MediaSave.kt`、
  `MediaSaveTest`) が決める:
  * image / video / gifv → `Pictures/Quasar/`、audio → `Music/Quasar/`、unknown → `Download/Quasar/`
    (MediaProvider は最上位のディレクトリごとに置ける種類を制限していて、Pictures には画像・動画しか置けない)。
  * ファイル名は `<取得元のホスト>_<メディア ID>.<拡張子>`。拡張子は URL のパスから、無ければ type から (jpg / mp4 / mp3)。
    `:` 等は `_` に置き換える (プロフィール画像の合成 ID `avatar:<id>` 等)。
* `setDestinationInExternalPublicDir` は targetSdk Q 以上なら既知の公開ディレクトリに権限なしで書ける。サブディレクトリも
  DownloadProvider が作る。保存されたファイルはメディアスキャンされ、MediaStore の持ち主はこのアプリになる (エミュレータで確認)。
* 同じファイル名が既にあれば DownloadProvider が `-1` 等を付けて別名にする (上書き・失敗にはならない)。
* 進み具合・完了・失敗は DownloadProvider の通知 (`VISIBILITY_VISIBLE_NOTIFY_COMPLETED`)。通知を出すのはシステム側なので
  `POST_NOTIFICATIONS` は要らない。アプリは結果を受け取らない (`ACTION_DOWNLOAD_COMPLETE` の receiver は無い)。
  ビューアは受け付けたら「Saving to Pictures/Quasar」、受け付けられなければ「Couldn't save」をスナックバーで出す。
* DownloadManager は http / https しか受け付けない (ギャラリーの `file:///android_asset` は「Couldn't save」になる)。
* 未対応: remote_url で失敗したときに url で取り直す (受け取るなら完了の receiver が要る)、ダウンロード中の通知のタップ
  (`ACTION_NOTIFICATION_CLICKED`)。

## 未実装・未確認
* PiP、代替テキストの表示。
* 画像: 拡大中に端まで動かしたら次のページへ送る、拡大率を元画像の解像度に合わせる (今は一律 5 倍まで)。
* 一覧での自動再生 (入れるなら `DisplaySettings` の設定項目にし、既定はオフ)。
* ビューアから戻ったときにインライン再生を続きから再開すること。
* 通知のコンパクト表示の 56dp サムネイルはインライン再生しない (行のタップは対象ポスト)。
* 実機で未確認: audio の再生 (回転アイコン)、複数メディアの左右スワイプ、画面回転中の再生継続。

## テスト
`PlayerLogicTest` (時刻表示・長さの決め方・ダブルタップの領域・再生可能な種別)、`MediaViewerLogicTest`
(閉じる判定・ゴムの引っかかり・元の位置への変形・引数の JSON 往復)、`ZoomTransformTest` (指の下の点の固定・拡大率の範囲・移動範囲)、`MappersTest` (url / remote_url の分離)。
