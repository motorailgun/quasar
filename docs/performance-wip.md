# 描画性能の改善 (作業中のメモ)

低スペック端末で速くスクロールしたときに備えて、ポスト一覧の無駄な再コンポーズ・重い部品を減らしている。
別の環境で続きをするための記録。終わったら要点を AGENTS.md に移して、このファイルは消す。

## 測り方

* **benchmark ビルド** (`./gradlew :app:assembleBenchmark`) で測る。debug は Compose が最適化されず数倍遅い。
* 場面はギャラリーの `post-variety` (`adb shell am start --activity-clear-task -n es.aria_on_the_planet.quasar.debug/es.aria_on_the_planet.quasar.debug.DebugGalleryActivity --es scenario post-variety`)。
  シナリオを指定しても一覧が開くことがあり、そのときは「Post variety」を手でタップした。
* 起動後に composition tracing を有効にする:
  `adb shell am broadcast -a androidx.tracing.perfetto.action.ENABLE_TRACING -n es.aria_on_the_planet.quasar.debug/androidx.tracing.perfetto.TracingReceiver`
  (exitCode 2 が返ることがあるが、既に有効になっているだけのようだった)。
* Perfetto の設定 (`/data/local/tmp/trace.cfg` に push):
  ```
  buffers { size_kb: 131072 fill_policy: RING_BUFFER }
  data_sources { config { name: "track_event" track_event_config { enabled_categories: "*" } } }
  data_sources { config { name: "linux.ftrace" ftrace_config { atrace_apps: "es.aria_on_the_planet.quasar.debug" atrace_categories: "view" atrace_categories: "gfx" ftrace_events: "sched/sched_switch" } } }
  duration_ms: 16000
  ```
  記録しながら、上へ 2 回・下へ 2 回の速いスワイプ (`input swipe 540 1900 540 500 150`、間に 0.6 秒) を 6 回繰り返す。
* 集計は Python の `perfetto` パッケージ (`TraceProcessor`)。Composable の区間名は `es.aria_on_the_planet...PostCard (PostCard.kt:NN)` の形。
  区間を祖先で分類した:
  * 祖先に `compose:lazy:prefetch:*` → 先読み (フレームの外)
  * 祖先に `measure` / `traversal` → レイアウト中の subcompose (そのフレームで同期的に組み立て)
  * どちらでもなく祖先に `Compose:recompose` → 再コンポーズ (無駄の候補)
* Compose コンパイラのレポート: `./gradlew :app:compileBenchmarkKotlin -PcomposeReports --rerun` → `app/build/compose_compiler/`。
  `Post` は unstable だが strong skipping で `===` で飛ばせるので `@Immutable` は付けない (中に List がある)。
* **エミュレーターの値はぶれが大きい** (ホストの負荷・Gradle デーモン次第で 15〜20%)。比べるときは `./gradlew --stop` してから
  A / B の APK を交互に入れて測る。RenderThread (描画側) の差はエミュレーターでは読めなかったので、描画側の変更は実機で測る。

## 済み (1〜4)

修正前 → 後 (エミュレーター、benchmark、同じ操作)。

| | 前 | 後 |
|---|---|---|
| `PostCard` 1 件の組み立て (平均) | 3.70ms | 2.99ms |
| `PostActionRow` | 2.02ms | 0.98ms |
| `AnimatedCount` (1 つ) | 363µs | 46µs |
| `PostActionButton` (1 つ) | 471µs | 202µs |
| `CollapsibleContent` の再コンポーズ | 44 回 | 0 回 |
| `MediaAttachments` の組み立て直後の再コンポーズ | 8 件中 8 件 | 8 件中 4 件 |

触っていない部分 (本文・投稿者行) も後の方が 15〜20% 遅く出ていた (ぶれ)。PostCard の 19% 減は控えめな値。

1. **`AnimatedCount`** (`PostCard.kt`): 件数が一度も変わっていない間は素の `content(count)`。変わったら最初の件数から始まる
   `MutableTransitionState` を作り、`rememberTransition(...).AnimatedContent` に切り替える (最初の変化からアニメーションする)。
2. **`PostActionButton`** (`PostCard.kt`): `animateColorAsState` ×1・`animateFloatAsState` ×2・`collectIsPressedAsState`・
   `LaunchedEffect(active)` をやめ、`ActionButtonAnimation` (`Animatable` を持つ小さなクラス) にまとめた。
   * 色・状態の変化は `SideEffect { animation.update(...) }` で受け、変わったときだけ launch する。
   * 弾み・粒はタップでオンになったとき (`tapped`) だけ。ストリーム等から状態が変わっても弾まない。確認ダイアログを挟んでも動く。
   * 押下の縮小は押せるボタン (`onClick != null`) だけ `LaunchedEffect` で interaction を集める。
3. **`CollapsibleContent`**: `onSizeChanged` で書いた高さをコンポジションで読む backwards write だった。
   `val overflows by remember(maxPx) { derivedStateOf { contentHeight > maxPx } }` にして、はみ出すかが変わらなければ再コンポーズしない。
4. **`MediaAttachments`**:
   * `onGloballyPositioned` (スクロールの毎フレーム呼ばれる) → `onPlaced` で `LayoutCoordinates` を持ち、タップ時に `positionInWindow`。
   * 端のフェードは `animateFloatAsState` をやめ、残りのスクロール量 / フェード幅から描画時に決める (見え方が少し変わった:
     時間で消える → スクロール量に比例)。
   * スクロールの有効化は `derivedStateOf { scrollState.maxValue in 1 until Int.MAX_VALUE }` (レイアウト前の maxValue は Int.MAX_VALUE)。
     収まる並びは再コンポーズしない。はみ出す並び (4 枚等) は有効化のため 1 回残る (仕方ない)。
   * `Compose:onPositionedCallbacks` の回数は 248 → 247 で、ほぼ他の部品からだった (未調査)。

確認: `testDebugUnitTest` 通過、変更ファイルに警告なし。エミュレーター (debug) でお気に入りのオン / オフ (縮小・色・弾み・粒・件数の
スライド)、横スクロールした並びの 2 枚目からビューア (2/4 で開き、下スワイプで元の位置へ縮む)、端のフェードを確認。実機は下記。

### 実機での検証 (2026-10-27)

端末は A024 (SM8735、1260x2800、120Hz)。benchmark ビルドで変更前 (004f9f0) と変更後 (9e8f790) を交互に 3 回ずつ測った。
スワイプは画面の大きさに合わせて `input swipe 630 2200 630 600 150` (上の手順と同じ回数・間隔)。

組み立て (3 回の合計、1 回あたりの平均。先読み + レイアウト中 + 再コンポーズ):

| | 前 | 後 |
|---|---|---|
| `PostCard` 1 件 | 6.34ms | 4.78ms (−25%) |
| `PostActionRow` | 4.24ms | 2.12ms (−50%) |
| `AnimatedCount` (1 つ) | 0.89ms | 0.12ms |
| `PostActionButton` (1 つ) | 1.13ms | 0.49ms |
| `CollapsibleContent` の再コンポーズ | 105 回 | 0 回 |
| `MediaAttachments` の再コンポーズ | 22 回 | 12 回 |

* `CollapsibleContent` は初回の組み立てが 1 回あたり約 0.75ms → 1.0ms に増えて見え、再コンポーズが無くなった分と合わせた
  合計は 103ms → 108ms でほぼ同じ。触っていない `EmojiText` / `AuthorLine` も後の方が 15% ほど遅く出ているので、ぶれの
  可能性が高いが、`CollapsibleContent` の時間そのものは減っていない (再コンポーズの回数が減っただけ)。
* フレーム単位 (`dumpsys gfxinfo`、トレース無し) は差が見えなかった: ジャンク 4.2 / 5.4 / 4.2% → 4.7 / 4.0 / 4.0%、
  p99 29 / 32 / 29ms → 27 / 26 / 28ms、CPU (1 コア比) 82〜85% → 80〜84%。トレース中の main の `doFrame` も平均 4.2〜4.5ms で同じ。
  減った組み立て (16 秒で約 0.2 秒) の多くは先読み (フレームの外) で、この端末では余裕があるため。効くのは低スペック端末。
* 動作: お気に入りのオン (色・塗り・粒・件数) / オフ (粒・弾み無しで色が戻る)、横スクロールした 4 枚の並びの 2 枚目から
  ビューア (2/4 で開き、下スワイプでスクロール後の位置へ縮んで戻る)、両端のフェード、いずれも実機で問題なし
  (ギャラリーの `post-detail`)。押している間の縮小は録画では見分けられなかった。

## 済み (5)

5. **`FadingEdges` の Offscreen をフェードを描くときだけにした** (`offscreenWhile`)。以前は `bottomFadingEdge` /
   `horizontalFadingEdges` を付けた所 (全ポストの本文 = `CollapsibleContent`、添付の並び、タブ、投稿欄の添付) が、
   フェードしていなくても常にオフスクリーンのバッファを確保して描いていた。強さはレイヤの設定の中で読むので、変わっても
   組み立て・レイアウトはやり直さない。描画側の判定 (`> 0f` のときだけ DstIn) と揃えておかないと、DstIn が下の画面まで削る。
   * 実機 (A024、benchmark、post-variety を速くスワイプ、トレースは atrace の gfx / view のみ。変更前 = 9e8f790 と交互に 4 回ずつ):

     | | 前 | 後 |
     |---|---|---|
     | `drawLayer [graphicsLayer]` (オフスクリーンのレイヤの描き直し) | 415〜424 回・約 145ms | 12 回・約 1ms |
     | `flush layers` | 640〜675 回・1053〜1144ms | 439〜469 回・882〜953ms |
     | 1 フレームの描画 (`Drawing`) 平均 / p90 / p99 | 4.64ms / 6.76 / 8.67 | 4.58ms / 6.46 / 8.04 |
     | RenderThread の CPU (gfxinfo 側、1 コア比) | 34.1〜35.0% | 32.4〜34.4% |

     残った 12 回は実際にフェードしている 4 枚の添付の並び。残りの `flush layers` は主にスクロール端の伸び (`AndroidEdgeEffectOverscrollEffect`)。
     GPU の時間 (gfxinfo) はどちらも 2ms で、ジャンク・fps に差は出なかった (この端末では余裕がある)。
   * 見た目: 添付の並びの左右のフェード、長文の下端のフェードと Show more / Show less での出入りを実機で確認 (黒く抜ける・ちらつきは無し)。
     長文はギャラリーに無いので、`--ei long_post_max_lines 3` (`GalleryExtras.LongPostMaxLines`) を足して post-variety の
     1 件目を折りたたませた。引用 (`QuotedPost`) の下端のフェードは、はみ出す引用がギャラリーに無く未確認 (同じ Modifier)。

## 残り (候補)

6. **`EmojiText` の `fromHtml` の結果をキャッシュ**: 本文の HTML → AnnotatedString を組み立てのたびにやっている
   (`remember` はあるが一覧のアイテムが作り直されると毎回)。ポスト ID 等をキーにしたプロセス内キャッシュの候補。
7. 小物: `PostTime` 等の `DateTimeFormatter` を毎回作らない。`TimelineScreen` が状態全体を `ColumnPage` に渡している
   (変わるたびに全ページが再コンポーズの候補)。
8. **Baseline Profile** (起動・タイムラインのスクロール)。まだ無い。

その他メモ: 先読み (`compose:lazy:prefetch:compose`) は 1 件あたり数 ms で、フレーム内の同期組み立てよりはこちらが主。
