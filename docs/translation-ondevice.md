# オンデバイス翻訳 (PLaMo 3 Translate + llama.cpp)

ポストを端末の中で訳す。フレーバー `full` に入り、v1.4 から GitHub Release で配っている (設定では「実験的機能」の下)。
`lite` には入らない。モデルは設定から Hugging Face のものをダウンロードする。以前の ML Kit + サーバー側翻訳の設計は
`origin/translation` ブランチの `docs/translation.md`。

## 構成

```
PostCard / PostDetail → PostBody (translatable)
   rememberPostTranslation → PostTranslationBar (ui/component/post/PostTranslation.kt)。訳したら本文・CW を差し替える
      ▲ snapshot の状態 (ポストごと)
PostTranslationStore (ui/translation/、アプリ全体で 1 つ、LocalPostTranslations)
   translationOfferFor (何語から何語へか・自動で訳すか) → 順番待ち → 1 件ずつ PostTranslator.translate
PostTranslator (data/translation/)
   toTranslatableHtml → split → plamoTranslationPrompt → TranslationEngine → restoreTranslatedHtml (崩れたら平文で訳し直す)
TranslationEngine: createTranslationEngine は src/full (LlamaTranslationEngine → :llama の LlamaModel) / src/lite (null)
TranslationModelFiles (data/translation/): モデルのダウンロード・削除
```

* `:llama` (`llama/`): llama.cpp (git submodule `third_party/llama.cpp`、タグ v1.0.0 = b12359) を NDK でビルドし、薄い JNI
  (`quasar_llama.cpp`) と Kotlin の `LlamaModel` で包む。llama.cpp の API はこのモジュールの中だけで使う。app からは
  `fullImplementation`。
* 変換・判断は純粋関数で、テストがある (`PlamoPromptTest` / `TranslatableHtmlTest` / `TranslationOfferTest` /
  `LanguageDetectionTest`)。**挙動を変えたら、テストとこのドキュメントの実験の節も直す**。

## モデル

[PLaMo 3 Translate](https://huggingface.co/collections/pfnet/plamo-3-translate) (PFN、PLaMo Community License。
非商用は登録なしで無償、商用は年商 10 億円以下なら登録して無償)。0.8B / 2B / 8B / 31B があり、端末では 0.8B。53 言語。

* 0.8B の構造: hidden 1536・16 層・語彙 107,520・KV ヘッド 1 (MQA)・スライディングウィンドウ 2048 (8 層に 1 層が全体)。
  埋め込みと lm_head は別々で、合わせて全体の約 37%。
* **使うのは GGUF の Q4_0 (548MB)**。HF の W4AFP8 等は vLLM (GPU サーバ) 向けで、埋め込み・lm_head が BF16 のまま (W4AFP8 で
  950MB) な上、活性化の FP8 を端末のランタイムが扱えない。GGUF は埋め込み・出力も量子化される (Q4_K_M 573MB / Q4_0 548MB /
  Q3_K_M 486MB)。Q4_0 は Arm CPU・Adreno・Hexagon に最適化したカーネルがある。
* KV キャッシュは KV ヘッドが 1 つなので 1 トークンあたり全層で約 8KB (fp16)、コンテキスト 4096 で約 32MB。

## 実行のフレームワーク

llama.cpp を選んだ。

| 候補 | PLaMo 3 | 備考 |
|---|---|---|
| **llama.cpp** | 公式の GGUF がそのまま動く (`plamo3` の構造とトークナイザに対応済み) | CPU (NEON / dotprod / i8mm、実行時に選ぶ)・OpenCL・Vulkan・**Hexagon (NPU)** |
| LiteRT-LM | 構造の移植とトークナイザの変換が要る (1〜3 週間の見込み) | トークナイザが独自 (Unigram + Aho-Corasick) で一致の検証が重い。NPU は SoC ごとの事前コンパイル |
| ExecuTorch | `modeling_plamo.py` から書き出し | XNNPACK の 8da4w、QNN で NPU |
| MLC LLM / ONNX Runtime GenAI | 移植が要る | |

LiteRT-LM と llama.cpp を同じ条件で比べたベンチマークは見つからなかった (2026-11)。Google の数字では生成 (decode) の速さは
CPU と GPU でほぼ同じで、ポストの翻訳は生成が主なので差は出にくいと見ている。

### `:llama` のビルド

* NDK 29.0.14206865・CMake 4.1.2 (SDK のパッケージ)。最初のビルドは 2 ABI 分で数分。STL は `c++_shared`。KleidiAI
  (ビルド時に取りに行く) と OpenMP (`libomp.so` が要る。ggml 自身のスレッドプールで足りる) は使わない。
* `BUILD_SHARED_LIBS` + `GGML_BACKEND_DL` + `GGML_CPU_ALL_VARIANTS`: CPU の機能ごとの版 (arm64 は 7 つ、x86_64 は 14) を作り、
  実行時に選ぶ。選ぶには .so がファイルとして要るので、full は `useLegacyPackaging`。
* APK に入れる版は絞っている (`app/build.gradle.kts` の `droppedCpuVariants`。ggml は nativeLibraryDir の `libggml-cpu-*.so` を
  走査して選ぶので、無い版は候補にならないだけ): arm64 は armv8.0 (NEON)・armv8.2_2 (dotprod + FP16)・armv8.6 (i8mm)、
  x86_64 は sse42・haswell (AVX2)。SVE2・SME の CPU は armv8.6 で動く。エミュレーターは sse42 を使う (2026-11 時点)。
  **llama.cpp を上げて版が増えたら、ここと release のワークフローの CPU の版の一覧を見直す**。
* release / benchmark は AGP が .so を strip する (app にも `ndkVersion` が要る。無いと警告だけで strip せずに入れる)。
  release の APK は arm64 版 13.2MB・x86_64 版 10.1MB (2026-11-07、CPU の版を絞った後。debug は strip されず数十 MB 大きい)。

### CI・配布

ビルドの本体は `.github/workflows/build.yml` (再利用ワークフロー)。手動の確認 (`check.yml`: テスト + lite・full (NPU 込み)
の debug ビルド) とリリース (`release.yml`、`v*` のタグ) が呼ぶ。リリースは lite と、NPU 入りの full を ABI ごとに 2 つ
(`-Pquasar.fullAbi=arm64-v8a` / `x86_64`。プロパティが無ければ full は全 ABI 入り) 作る。出す前に、full の APK がそれぞれの
ABI だけを持つこと・CPU の版の一覧・arm64 版に NPU のカーネルと SDK の表記が入っていること・`.so` に `.debug_info` が
残っていないことを確かめる。Hexagon SDK は下記の tarball を sha256 を固定して取る。

### Hexagon (NPU)

llama.cpp の Hexagon バックエンド (Qualcomm が開発に参加、experimental) は Hexagon SDK を直接使い、NPU 上のカーネル
(`libggml-htp-v73〜v85.so`) を FastRPC で呼ぶ。v73 = Snapdragon 8 Gen 2 以降 (8 Elite は v79)。**OnePlus 13 でアプリの中から
動くことを確認した**。

既定のビルドには入らない。`local.properties` に `quasar.hexagonSdk=<SDK のパス>` を書いた (または `-Pquasar.hexagonSdk=…`)
ときだけ、arm64 に足す。SDK は snapdragon-toolchain の再パッケージ (llama.cpp の Hexagon バックエンドを書いている Qualcomm の
開発者が CI 向けに文書等を削ったもの。llama.cpp 本家の CI・release もこれを使う。SDK 単体の Linux 版の配布は無い) から取る。
hexagon-clang・qaic は Gradle (CMake) から呼ぶのでホストに置くしかないが、**置き場所は `StudioProjects` の下** (下記
「外から取ってきたバイナリ」)。イメージは digest で固定する:

```
image=ghcr.io/snapdragon-toolchain/arm64-android@sha256:e39e5e9c0957550a92c0de756b7556d81196c7c7f7328ed80de252bfb4878adf   # v0.8
docker pull "$image"
sdk_root="$(cd .. && pwd)/hexagon-sdk"   # = ~/StudioProjects/hexagon-sdk
mkdir -p "$sdk_root"
# docker cp は中の権限で失敗するので tar で取り出す (3.3GB。Hexagon SDK 6.7.0.1 / Hexagon Tools 21.0.04)
docker run --rm "$image" tar -C /opt/hexagon -cf - 6.7.0.0 | tar -C "$sdk_root" -xf - --no-same-owner
chmod -R u+rwX "$sdk_root/6.7.0.0"
echo "quasar.hexagonSdk=$sdk_root/6.7.0.0" >> local.properties
```

SDK の場所を変えたら `llama/.cxx` を消す (CMake のキャッシュが前のパスを覚えている)。CI は Docker のイメージではなく同じ
再パッケージの tarball (`snapdragon-toolchain/hexagon-sdk` の `hexagon-sdk-v6.7.0.0-amd64-lnx.tar.xz`。中身はイメージの
`/opt/hexagon/6.7.0.0` とファイル単位で同じ) を使う。

#### 外から取ってきたバイナリ

インターネットから取ってきたバイナリ (ツール・SDK・検査用のコマンド等) は**まず Docker の中で動かす** (ホストで直に実行
しない)。ビルドから呼ぶ等で Docker に閉じ込められないものだけはホストに置くが、そのときも `StudioProjects` の下に置き、
ホームの他の場所や PATH には入れない。

#### ライセンス (2026-11-07 に確認。未解決の点あり)

* APK に入る SDK 由来のもの: `libggml-hexagon.so` (CPU 側) に SDK のヘッダ (`remote.h`・`HAP_farf.h`・`dspqueue.h` 等は
  「Confidential and Proprietary」、`AEEStdDef.h`・`AEEStdErr.h`・`rpcmem.h` は BSD-3-Clause) と qaic が作った FastRPC の
  stub。`libggml-htp-v*.so` (NPU 側) に qaic の skel と Hexagon Tools の `initS.o`・`finiS.o`。SDK のライブラリはリンクしていない。
* BSD-3-Clause のヘッダの表記は、Hexagon 入りのビルドだけ `:llama` が `src/hexagon/assets/licenses/hexagon-sdk.txt` を入れ、
  設定の「ライセンス」に出る (`FlavorLicenses.kt`)。
* **それ以外は SDK の利用許諾に従うが、手元の SDK (文書を削った再パッケージ) には許諾の文書が無く、ビルドした物の再配布の
  条件は確かめられていない**。ggml-org は同じイメージで作った `libggml-htp-v*.so` 入りのバイナリを MIT の LICENSE だけで
  配っている (前例であって根拠ではない)。公式の SDK を落として許諾を読むか、Qualcomm に確かめる。
* `__FILE__` 等でビルドした場所の絶対パスが strip 後の .so に残る (CI のビルドならランナーのパス)。

#### ビルドの仕組み

* `-DGGML_HEXAGON=ON -DHEXAGON_SDK_ROOT=… -DPREBUILT_LIB_DIR=android_aarch64` (最後のは llama.cpp の公式の preset と同じ。
  無いと SDK の `hexagon_fun.cmake` が失敗する)。x86_64 では `CMakeLists.txt` で切る。
* NPU 側の `libggml-htp-v*.so` は llama.cpp の CMake の ExternalProject で作られ、AGP がビルドするライブラリの依存に入らないので、
  `quasar_llama` に `add_dependencies` で作らせ、`CopyHtpSkels` (`llama/build.gradle.kts`) が jniLibs の生成ディレクトリに写す。

#### 実行時

* `libggml-hexagon.so` が `libcdsprpc.so` (FastRPC。端末の /vendor) を dlopen する。そのために `:llama` の manifest に
  `<uses-native-library android:name="libcdsprpc.so" android:required="false" />`。
* NPU 側のライブラリの場所: llama.cpp v1.0.0 からは `libggml-hexagon.so` のある場所 (= nativeLibraryDir) を自分で探して絶対
  パスで開く (#31781)。JNI の `nativeInit` が `ADSP_LIBRARY_PATH` に `nativeLibraryDir;/vendor/dsp/cdsp;…` を入れているのは
  その予備 (b12129 まではこれで探していた)。**v1.0.0 での NPU の動作は実機で未確認**。どちらも .so が展開されている必要がある
  (`useLegacyPackaging`)。
* 署名の無い NPU のプロセス (unsigned PD) は llama.cpp が頼むので、アプリ側は何もしない。
* `LlamaParams.cpuOnly = false` (既定) なら使えるデバイス全部、`true` なら CPU だけ (`n_gpu_layers = 0`。ギャラリーは
  `--es device cpu`)。
* 読み込みのログ (logcat の `llama.cpp`): `ggml-hex: Hexagon Arch version v79`、`HTP0 hwinfo: threads 6, hvx 6, hmx 1, vtcm 8 MB`、
  `offloaded 17/17 layers to GPU`、`HTP0 power mode: burst` (既定。`GGML_HEXAGON_POWER_MODE` で変えられる)。
* 動いている間も SELinux の拒否 (`avc: denied { open } … /dev/fastrpc-cdsp`) が logcat に出るが、NPU で計算されている。
  FastRPC が順に試す経路のうち使えないものと思われる (原因は未確認)。
* **Hexagon の版ごとの扱い** (JNI の `nativeInit`。バックエンドを読む前に `remote_handle_control` (`DSPRPC_GET_DSP_INFO` /
  `ARCH_VER`) で版を調べる。llama.cpp の `htpdrv_get_arch` と同じ問い合わせ):
  * **v73 (8 Gen 2・8s Gen 3・8s Gen 4 等) は HMX を使わない** (`GGML_HEXAGON_NHMX=0`、HVX だけ)。Nothing (8s Gen 4) で HMX を
    使うと訳文が壊れた (「】外傷外傷喪失喪失…」が上限まで続く)。**原因: v73 には HMX が FP16 に対応していないもの (下位の SKU)
    があり、llama.cpp は FP16 の HMX を使う**。報告済み: [#29473](https://github.com/ggml-org/llama.cpp/issues/29473)・
    [#31645](https://github.com/ggml-org/llama.cpp/issues/31645)。直す PR [#29740](https://github.com/ggml-org/llama.cpp/pull/29740)
    (FP16 の HMX があるかを調べ、無ければ整数の HMX を使う。`GGML_HEXAGON_HMX_FP16=0|1`) は **v1.0.0 にも入っていない**。
    FP16 の HMX があるか (8 Gen 2 等) はアプリから分からないので v73 は全部 HVX だけ。**#29740 が入ったら v73 の扱いを外して
    llama.cpp の判定に任せ、v73 の実機で確かめる**。環境変数が明示されていればそちらに従う。
    この扱いで Nothing のギャラリーの 4 件が正しく訳せることを確認した。
  * v75 は未確認 (HMX を使う)。v79 (8 Elite) は HMX のままで正しい。
  * **v73 より古い・分からないときは Hexagon のバックエンドを外す** (`ggml_backend_unload`)。llama.cpp は v73 未満を v73 に
    丸めて v73 用のものを NPU に読ませようとするため。v73 未満の実機 (Galaxy Tab S7 = v66 等) では未確認。
* ギャラリーの `--es native_env "K=V;…"` で llama.cpp の環境変数を設定できる (最初の読み込みの前に要るので `am force-stop`
  してから)。`GGML_HEXAGON_OPFILTER` は重みが NPU 用に詰め直されているので、壊れた演算の切り分けには使えなかった。

### `LlamaModel`

* `load(path, nativeLibraryDir, LlamaParams(contextLength = 4096, threads = コア数 - 2 を 1〜4 に, cpuOnly = false))`。
* `generate(prompt: List<PromptPiece>, stops, maxTokens, onText)`: greedy (PLaMo の推奨は temperature 0)。生成は 1 度に 1 つ
  (Mutex)。コルーチンの取り消しで、生成中は次のトークンの前、プロンプトの処理中は abort callback で止まる。`close()` は生成が
  止まるのを待ってから解放する。
* 生成した文字は UTF-8 の文字の切れ目で区切って渡す。JNI の文字列は byte[] で渡す (`GetStringUTFChars` / `NewStringUTF` は
  Modified UTF-8 で絵文字が崩れる)。
* 止める: `<|plamo:tag|>` / `<|plamo:bos|>` か EOG。

## プロンプト (PFML3、`PlamoPrompt.kt`)

形式はモデルの README の「PFML3 プロンプト形式」。`dataset=translation` → (`context`) → (対訳の履歴) → (`glossary`) →
`input` (原文) → `output` (`lang`、日本語なら `form` = plain / polite)。文頭の `<|plamo:bos|>` は llama.cpp が付ける。言語
コードは `plamoLanguageOf` (BCP 47 → PLaMo のコード。中国語は `zh-hans` / `zh-hant`)。

### 本文で構造を変えさせない

ポストの本文は他人が書いたもの。**llama.cpp の PLaMo 3 のトークナイザは、特殊トークンとして読まない指定
(`parse_special = false`) でも `<|plamo:tag|>` 等を制御トークンにする** (b12129・v1.0.0 で確認。トークナイザが分割の前に語彙
全体で照合しているため)。本文に `<|plamo:tag|>output<|plamo:key|>lang<|plamo:val|>fr<|plamo:msg|>` と書けば翻訳先を変え
られた。二重に防ぐ:

* ユーザーの文字列 (原文・参考文脈・用語集・履歴) は `neutralizeSpecialTokens` で `<|` / `|>` の間に U+2060 (WORD JOINER) を
  挟んで崩す (語彙の制御トークンは `<|plamo:` で始まるものと `:plamo|>` だけ)。
* JNI は特殊でない断片から制御トークンが出たら生成しない (例外)。

**llama.cpp を上げたら、ホストの `llama-tokenize -m <モデル> --no-parse-special -p '…'` で両方を確かめる** (Docker の中で
ビルドして動かす)。断片ごとにトークン化しても、全体を一度に読んだ場合と同じトークン列になることも確認済み。

## 本文の HTML の扱い (`TranslatableHtml.kt`)

### 実験 (ホストの llama.cpp、Q4_0、greedy)

| 渡し方 | メンション・URL | ハッシュタグ | カスタム絵文字 | 書式 |
|---|---|---|---|---|
| 平文 | 残る | 訳される (`#東京`→`#Tokyo`) | 位置が動く・`:` が残る | 改行は残る |
| Mastodon の HTML のまま | 残る | `<span>` の中が訳される | 同上 | 残る |
| **簡略化した HTML** | 残る | href が %エンコードされていると訳されることがある (下記) | **`<img alt=":x:">` にすると残り、位置も自然** | 残る |

訳の質はおおむね良い。一言、すでに翻訳先の言語の文 (そのまま返る) も問題なし。HTML の分だけトークンは増える
(平文 40 前後 → 86〜110)。

### 変換

訳す前 (`toTranslatableHtml(html, emojiShortcodes)`):

* 残すタグ (`p br ul ol li blockquote pre code strong b em i u s del sub sup`) は属性を外して残し、それ以外は外して中身だけ残す。
* リンクは `<a href="…">中の文字をつなげたもの</a>` (短縮表示の URL も全体) にし、元の `<a>…</a>` を覚えておく。
* ポストのカスタム絵文字の `:shortcode:` だけを `<img alt=":shortcode:">` にする (`12:30:00` 等は文字のまま)。
* 引用のフォールバック「RE: <リンク>」(`quote-inline`) は除く。文字参照 (`&amp;` 等) はそのまま渡す。HTML の字句解析は
  自前の小さなもの (Mastodon がサニタイズした本文と、それを元にしたモデルの出力だけが相手)。

訳した後 (`restoreTranslatedHtml(source, output)`)。次のどれかなら崩れたとして `null`:

* 残すタグ・`<a href>`・`<img alt=":既知の絵文字:">` 以外のタグがある / 開き閉じが合わない / リンクの中にタグがある。
* リンク (href) や絵文字が元と同じ数だけ無い。
* 元は段落等の中にしか文字が無かったのに、外に文字・リンク・絵文字がある (`<p>この<img alt=":x:"></p>のデバッグを…` の
  ように段落を早く閉じたもの)。

戻し方:

* リンクは href で元のリンクに対応付け (同じ href は出てきた順)、**元の `<a>` に戻す**。Mastodon の href は %エンコードされて
  いて、そのときモデルは `#東京` を `#Tokyo` に訳すことがある。東京のタグを指したまま別の名前に見えるのは困るので、
  メンション・ハッシュタグ・URL は訳されていても元に戻す。文字が普通の言葉のリンク (`this page` 等) だけは訳した文字を使う。
* `<img alt=":x:">` は `:x:` に戻す。
* 文字 (リンクの中も) はエスケープする (`escapeText`): タグに見えない `<` `>` と、正しい文字参照になっていない `&` (`AT&T`・
  `&copy` 等)。表示側 (`fromHtml`) が続きを飲み込んだり文字参照と読んだりしないように。

崩れた分 (`null`) と上限で切れた分は、その部分だけ平文で訳し直す (リンクはただの文字になる。`PostTranslation.plain`)。

### 既知の問題

* 文末の絵文字が目的語に読まれることがある: 「for helping me debug this :x:」→「この :x: のデバッグを手伝ってくれた…」。HTML では
  上の検査で弾かれ平文で訳し直すが、平文でも良くない。案: 段落末尾の絵文字はモデルに渡さず、訳した後に段落の末尾へ付け直す。

## アプリでの動き

* **表示**: X の形。本文の上に「PLaMoで〇〇語から翻訳 · 原文を表示」/「翻訳を表示」(`PostTranslationBar`。PLaMo の名前は
  規約の第 5 条でモデルの出力だと示すためなので、省略させず折り返す)。順番待ち・訳している間は「翻訳中…」を薄くし、その上を
  濃い帯が流れる (`Modifier.shimmer`)。失敗は「翻訳できませんでした · 再試行」。訳を出すのは通常の PostCard と PostDetail
  だけ (返信先・︙のメニューの上・引用・通知の抜粋には出さない)。本文と CW を訳に差し替え、長文の折りたたみ・CW の開閉・
  リンクのタップはそのまま。投票の選択肢・メディアの代替テキストは訳さない。
* **いつ訳すか**: `PostBody` が組み立てられたら `onShown`、外れたら `onHidden`。自動で訳す言語なら順番待ちに入れ、訳し始める
  前に外れたら外す。1 度に 1 件で、最後に表示されたものから。「翻訳を表示」で押したものは先頭へ。ストリーミングで届いた
  ポストも同じ。訳し始めたものは画面を離れても最後まで訳す。
* **状態**: アプリ全体で 1 つなので、どの画面でも同じポストは同じ訳。ポストごとの状態は snapshot の状態で、1 件訳し終えても
  他の項目は描き直されない。キーは statusId で、原文が編集されたら作り直す。訳・推定した言語はそれぞれ 1000 件を超えたら
  古いものから捨てる。
* **組み立ての中では軽く・書かない**: `entryFor` はポストを組み立てるたびに呼ばれるので、端末の言語は構成が変わったとき
  (`onConfigurationChanged`) だけ読み直し、自動で訳すかの切り替えは設定が変わったときに `updateOffers` が当てる。
* **訳す先・自動** (`translationOfferFor`): `Post.language` をモデルの言語にする。訳す先は設定 (既定は端末の言語、対応して
  いなければ英語)。同じ言語・言語が分からない・対応していない・本文も CW も空なら何も出さない。訳す先を変えたら訳を全部捨て、
  言語ごとの扱いだけを変えたときは訳を残す。
* **言語ごとの扱い** (`TranslationMode`: 自動で翻訳 / ボタンを表示 / 表示しない): 「その他の言語」(`defaultMode`、既定は
  ボタンを表示) と、それと違う扱いにする言語の一覧 (`languageModes`) で決める。一覧を変えるまでは `null` で、端末の言語
  (`deviceLanguageTags`: アプリの言語 + 端末の設定の言語) を「表示しない」にした一覧として扱う。訳す先の言語は一覧に関わらず
  出さない。設定画面 (`TranslationLanguageSettings`) は追加した言語だけを並べ、検索付きのシートから足す。モデル無しで
  ギャラリーの `translation-languages` で触れる。
* **言語の無いポスト**: Misskey は AP の Note に言語を付けないので、Mastodon では `language` が `null` になる (testbed で確認)。
  その場合は言葉でないもの (メンション・ハッシュタグ・URL・絵文字・コード) を除いた本文 + CW を、端末の TextClassifier
  (`detectLanguage`、確信度 0.5 以上) に聞く (`detectPostLanguage`)。推定は `PostTranslationStore` が裏で行い、終わったら表示する。
  * エミュレーター (Google の TextClassifier) では英・日・仏・中・韓とも当たり、短くても確信度は高い (「それな」ja 1.0、
    「lol」en 0.75)。「草」は zh、「ok」は何も返らない。中国語は `zh` としか返らず、繁体字も簡体字扱いになる。
  * API は OS の標準だが、推定はシステムの TextClassifier サービスが行うので結果は端末次第 (AOSP main のソースで確認)。
    アプリの `getTextClassifier()` は `SystemTextClassifier` で、system_server が「メーカーのサービス
    (`config_defaultTextClassifierPackage`) があればそれ、無ければ既定 (`config_servicesExtensionPackage` = `android.ext.services`)」
    に回す。既定は Mainline の ExtServices の `DefaultTextClassifierService` で、`detectLanguage` は libtextclassifier の
    `LangIdModel` (`lang_id.model`) の結果を返す (ネイティブ側でモデルが持つノイズの閾値未満と `und` を除く。前処理で数字・
記号を除いた残りが短すぎると `und` = 空)。中身は文字 n-gram (ハッシュ) と文字種 (ひらがな等) の割合を特徴にした小さな
順伝播のニューラルネット (埋め込み → ReLU の隠れ層 → softmax。`native/lang_id/`)。モデルは `/data/misc/textclassifier/lang_id.model`
    (config updater) か APK に同梱のもの (ダウンローダは既定でオフ)。ExtServices は Mainline で更新されるので、OS の版が
    同じでもモデルの版で結果がずれうる。サービスが無い・失敗したときは `NO_OP` で空の結果 (= null、翻訳の表示を出さない)。
    どれが使われているかは `adb shell dumpsys textclassification` の `systemTextClassifierPackage` (メーカーの。`null` なら無し)
    と `defaultTextClassifierPackage`。ユーザーの実機 1 台は `systemTextClassifierPackage=null` で ExtServices だった。
    端末に依らずにしたいなら ML Kit の Language ID (モデル同梱) に替える。
  * API: [TextClassifier.detectLanguage](https://developer.android.com/reference/android/view/textclassifier/TextClassifier#detectLanguage(android.view.textclassifier.TextLanguage.Request)) /
    [TextLanguage](https://developer.android.com/reference/android/view/textclassifier/TextLanguage)。実装は AOSP の
    `packages/modules/ExtServices` と `external/libtextclassifier`。
* **長い本文**: 1 回の訳は出力 1024 トークン・コンテキスト 4096 まで。HTML で 800 文字を超える本文は `TranslatableHtml.split`
  で段落等の切れ目、それでも長い段落はその中の `<br>` で分け、1 つずつ訳してつなぐ (リンク・絵文字は分けた先へ順に配り、
  検査もそれぞれで行う)。文の途中では分けない。平文で訳し直しても上限で切れたら「PLaMoで〇〇語から一部を翻訳」。エミュレーターで
  930 文字・4 段落のノートを最後まで訳せた (約 20 秒)。
* **モデルの読み込みとメモリ**: 最初に訳すときに読み込み (logcat の `TranslationEngine`)、2 分使われなければ解放する。読み込んだ
  モデルは約 600MB (CPU は重みを並べ替えて持ち、NPU は共有メモリに写すので mmap のままにはならない)。裏で持っているとアプリ
  ごと終わらされやすいので:
  * 裏に回ったら (`ProcessLifecycleOwner` の onStop) すぐ解放し、前面に戻るまで訳さない。
  * `onTrimMemory` (RUNNING_LOW 以上、UI_HIDDEN を除く)・`onLowMemory` でも解放し、前面なら 30 秒は自動で訳さない。
  * 訳しかけは取り消して順番待ちに戻す (訳す先が変わって作り直したもの等は戻さない)。読み込みは JNI の中で止められないので、
    読み込みの途中で取り消しても読み終えてから解放する。モデルを消している間は次を訳さない。
  * CPU か NPU かは `LlamaParams` の既定 (NPU が使えれば NPU)。
* **設定** (設定 →「実験的機能」→「翻訳」、`TranslationSettingsPage`): 「端末内で翻訳」のスイッチ (既定オフ)、モデルの状態、
  翻訳先の言語 (端末の言語 / 53 言語)、言語ごとの扱い。保存は `DeviceSettings.translation` (端末共通)。言語名は ICU の方言名
  (`languageDisplayName`。「簡体中国語」)。
* **フレーバーの入れ替え**: applicationId が同じなので full と lite は互いに上書きして入れられる。lite は起動時に full の残した
  モデル・ダウンロードを消す。翻訳の設定の値は残るが lite では読まない (full に戻すとオンのままモデルが無い状態)。
* **確かめたこと** (エミュレーター + testbed、アプリの言語は日本語): 「翻訳を表示」→「翻訳中…」→ 訳 (メンションはリンクのまま)、
  自動にした言語がローカルの一覧・ストリーミングで自動で訳されること、「原文を表示」で戻ること。実機の実際のタイムラインは未確認。

### モデルのダウンロード (`TranslationModelFiles`)

* 取得元は `pfnet/plamo-3-translate-0.8b-GGUF` の Q4_0。**リビジョンを固定**し、大きさと sha256 を `TranslationModelSource` に
  持つ。差し替えるときはここを全部変える。認証は要らない。
* 流れ: 設定で「端末内で翻訳」をオン → 確認 (大きさ・Hugging Face から・モバイルデータの通信量) → OS の DownloadManager が
  `models/<名前>.download` へ落とす → sha256 を確かめて本来の名前にする。
  * アプリが終わっても続き、切れたら再開し、通知に進み具合が出る。ローミング中は落とさない。HF の 302 は DownloadManager がたどる。
  * 進み具合はダウンロード中だけ 1 秒ごとに問い合わせる。ID は保存せず、DownloadManager の一覧から URL で探す。
  * 空きがモデル + 200MB 無ければ始めない。失敗 (空き・通信・壊れている) は設定に出し、タップでやり直す。
* オフにするときは「ダウンロードしたモデルも削除する」(既定でチェック) を選べる。ダウンロード中のモデルの項目を押すと中止できる。
* 置き場所は `Android/data/<pkg>/files/models/` (`getExternalFilesDir`。DownloadManager が書けるのはここか共有の場所だけ)。
  548MB はバックアップの上限を超えるので `backup_rules.xml` / `data_extraction_rules.xml` で除外している。共有の「ダウンロード」等は
  再インストール後に読むのに SAF が要り、ユーザーが動かす・消すこともあるので使っていない。
* adb で置いても大きさが合えば使う (ハッシュは確かめない)。**ディレクトリはアプリに作らせる** (adb shell で作ると中を読めない)。
* PLaMo Community License は「Built with PLaMo」の表示と規約の写しを求める (第 4 条) ので、設定のモデルの項目と設定の
  「ライセンス」(本文は `src/full/assets/licenses/`) に出している。

## ギャラリー (`translation` シナリオ、`src/fullDebug` の `TranslationGallery.kt`)

固定データ `assets/gallery/translation.json` は Mastodon 4.x の形の HTML (メンション・%エンコードされた href のハッシュタグ・
短縮表示の URL・カスタム絵文字・リスト・コード・引用・RE:・一言)。言語は全部無しなので、推定の結果も出る。画面ではモデルの
読み込み・HTML / 平文・ポスト・翻訳先・文体を選べ、組み立てたプロンプト・訳文 (戻せなければ赤字)・速さが出る。

```
# 一度開いて models/ をアプリに作らせてから (または設定の「翻訳」でダウンロード)
adb push plamo-3-translate-0.8b-Q4_0.gguf /sdcard/Android/data/es.aria_on_the_planet.quasar.debug/files/models/
# 開いたらすぐ読み込んで訳す。logcat の QuasarTranslate に速さ・訳文・戻した HTML (restored: …)。llama.cpp のログは llama.cpp
adb shell am start --activity-clear-task -n es.aria_on_the_planet.quasar.debug/es.aria_on_the_planet.quasar.debug.DebugGalleryActivity \
    --es scenario translation --ei post 1 --es target en --ez autorun true [--es mode plain] [--es text "'原文'"] [--ei threads 4] [--es device cpu]
```

## 速さ

| 環境 | 読み込み | プロンプト | 生成 |
|---|---|---|---|
| エミュレーター (x86_64、2 スレッド、debug。参考程度) | 1.2〜3 秒 | 40〜80 tok/s | 20〜45 tok/s |
| ホスト (x86_64、llama-completion、4 スレッド) | 0.8 秒 | 約 370〜625 tok/s | 約 72〜85 tok/s |
| **OnePlus 13 (8 Elite) CPU** (4 スレッド、armv8.6) | 1.0〜1.2 秒 | 545〜620 tok/s | **100〜111 tok/s** |
| **OnePlus 13 NPU** (v79、burst) | 1.2 秒 | **1,950〜2,840 tok/s** | 78〜83 tok/s |
| **Nothing A024 (8s Gen 4) CPU** (4 スレッド、armv8.6) | (未計測) | 400〜440 tok/s | 70〜75 tok/s |
| **Nothing A024 NPU** (v73、HVX のみ) | 2.1 秒 | 175〜195 tok/s | 71〜76 tok/s |

実機の数字は b12129 の時期のもの (ギャラリーのポスト、プロンプト 65〜125・生成 53〜108 トークン)。**訳文は CPU と NPU で同じ**。
ホストでは b12129 と v1.0.0 で訳文がバイト単位で同じ・速さも同じだった。

* NPU はプロンプトの処理が速いが生成は少し遅いので、ポスト 1 件にかかる時間は CPU とほぼ同じか NPU が少し遅い (8 Elite で
  CPU 1.25 秒 / NPU 1.41 秒)。**CPU の使用時間は NPU が約 70% 少ない** (読み込み + 1 件で CPU 7.6 秒 / NPU 2.3 秒)。
* 8s Gen 4 (v73、HVX のみ) は、プロンプトの処理が CPU の半分以下、生成は同じ。CPU の使用時間はやはり約 70% 少ない。HMX を
  使えればプロンプトは速くなるはず。
* 画面が消える (Doze) と読み込み・生成がとても遅くなる (読み込み 13 秒、生成が 2 分で終わらない)。
* 未計測: 消費電力・発熱、スレッド数・電力モードを変えた場合、長いプロンプト、遅い端末 (eMMC 等) での読み込み、v1.0.0 の実機。

## 次

1. v1.0.0 の実機 (特に NPU。ライブラリの探し方が変わった) での確認。#29740 が入ったら v73 の HMX を戻す。
2. CPU と NPU のどちらを既定にするか (消費電力の計測)。v73 未満 (Galaxy Tab S7 等) での動作。
3. Hexagon SDK の再配布の条件の確認 (上記「ライセンス」)。
4. 実機の実際のタイムラインでの確認、訳したときの差し替えのアニメーション、文末の絵文字、通知の抜粋・引用の翻訳。
