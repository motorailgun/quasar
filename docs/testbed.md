# testbed: 動作確認用の連合環境の使い方

`testbed/` は Docker の中に Mastodon を 3 台立てて互いに連合させ、bots が投稿・リアクションし合う環境。
自分の管理下にない実サーバで条件に合う投稿を探したり、版ごとの API の違いを確かめたりする代わりに使う。
環境そのものの構成 (compose・CA・アカウント・bots の中身) は [testbed/README.md](../testbed/README.md)。
ここには、Quasar の開発で使うときの設定とセットアップの手順をまとめる。

| ドメイン | 版 (`testbed/.env`) |
| --- | --- |
| `alpha.quasar.test` | main の nightly を日付で固定 (`ALPHA_TAG`。alpha は版で tag されない) |
| `stable.quasar.test` | 最新安定版 (`STABLE_TAG`) |
| `legacy.quasar.test` | 3.5.19 (`LEGACY_TAG`) |

## 前提

* Docker (compose v2)。ホストの `127.0.0.1:443` (proxy) と `127.0.0.1:53` (端末用の DNS) を使うので、空いていること。
  ポートを変えるなら `.env` の `TESTBED_BIND` / `TESTBED_HTTPS_PORT`。DNS の 53 は変えられない
  (エミュレーターの `-dns-server` はポートを指定できない)。
* エミュレーター (今は AVD `Pixel_10a`)。PlayStore のイメージは root を取れず `/etc/hosts` を書けないので、
  名前解決は testbed の DNS を使う。
* 初回の `up` はイメージの取得 + DB の初期化で 10 分を超えることがある。エージェントから走らせるときはバックグラウンドで。

## 設定 (`testbed/.env`)

| 項目 | 意味 |
| --- | --- |
| `ALPHA_TAG` / `STABLE_TAG` / `LEGACY_TAG` | 各サーバのイメージの tag。書き換えて `./testbed.sh up` で入れ替わる (既存の DB には `db:migrate`) |
| `TESTBED_BIND` / `TESTBED_HTTPS_PORT` | proxy (HTTPS) と DNS を公開するアドレス・ポート。外に出したくなければ `127.0.0.1` のまま |
| `DEVICE_HOST_ADDR` | 端末から見たホストのアドレス。DNS が `*.quasar.test` をここに向ける。エミュレーターは `10.0.2.2` |
| `BOT_INTERVAL` | bots の動作間隔 (秒、既定 12)。変えたら `./testbed.sh bots start` で作り直される |

## 環境を立てる

```sh
cd testbed
./testbed.sh up            # 初期化 (CA・秘密の値) → DB → 起動 → アカウント作成 → 全員を相互フォロー。何度走らせてもよい
./testbed.sh bots start    # 自動投稿を始める (stop / logs / burst [N])
./testbed.sh accounts      # アカウント・ID・トークンの一覧
./testbed.sh down          # 止める (データは残る)
./testbed.sh reset         # データ・CA・トークンを全部消す (端末の CA も入れ直しになる)
```

* アカウントは各サーバに `user1`〜`user10`。ログインはメール `userN@<domain>` / パスワード `quasar-testbed`。
  `user10` は鍵アカウント、`user9` は bot。カスタム絵文字 `:quasar:` `:testbed:` `:blobok:`。
* ホストから API を叩くときは名前を proxy に向ける (ホストの DNS は `*.quasar.test` を知らない。CA も自前):
  `curl -sk --resolve legacy.quasar.test:443:127.0.0.1 https://legacy.quasar.test/api/v1/timelines/public?local=true`

## エミュレーターから繋ぐ

1. **DNS 付きで起動する** (毎回):
   `~/Android/Sdk/emulator/emulator -avd Pixel_10a -dns-server 127.0.0.1 -no-snapshot-save -no-audio -no-boot-anim`
   (`-no-window` も可)。`adb shell ping -c1 legacy.quasar.test` が `10.0.2.2` を返せば OK。
   `-dns-server` 無しで起動したエミュレーターは起動し直す必要がある。
2. **CA を入れる** (初回と `reset` の後。AVD に残る):
   * CA のインストールには画面ロックが要る: `adb shell locksettings set-pin 1234` (testbed 用の AVD は PIN `1234`)。
   * `adb push testbed/state/certs/ca.der /sdcard/Download/`
   * 設定 > セキュリティとプライバシー > その他のセキュリティ設定 > 暗号化と認証情報 > 証明書のインストール > CA 証明書
     → 警告を進めて `ca.der` を選ぶ。元の画面に戻れば入っている。
3. **アプリにユーザーの CA を信頼させる** (下記「アプリ側の設定」。コミットしない)。
4. debug ビルドを入れ、サーバに `legacy.quasar.test` 等を入れてログイン。認可は Chrome の Custom Tab で開く。
   * Chrome もユーザーの CA を信頼するので、証明書の警告は出ない。
   * 初回は Chrome の初回起動の画面と、「ローカル ネットワークへのアクセス」の許可 (`ERR_LOCAL_NETWORK_PERMISSION_MISSING`。
     `10.0.2.2` がプライベートアドレスのため) が出る。許可して読み込み直す。
   * ログイン → 戻り (OAuth の往復) はこの手順で確認済み。

## アプリ側の設定 (ローカルのみ・コミットしない)

Android 7 以降、アプリは既定でユーザーが入れた CA を信頼しないので、testbed の自前の CA で TLS が通らない。
testbed を使う間だけ、手元で次を足す。**release ビルドにはどうせ効かないが、ブランチ (master / experimental 等) には
入れない** (常にそのまま release を出せる状態に保つため)。

`app/src/main/res/xml/network_security_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- debuggable なビルド (debug) だけ、ユーザーが入れた CA も信頼する。testbed の自前の CA のため。 -->
<network-security-config>
    <debug-overrides>
        <trust-anchors>
            <certificates src="system" />
            <certificates src="user" />
        </trust-anchors>
    </debug-overrides>
</network-security-config>
```

`app/src/main/AndroidManifest.xml` の `<application>` に:

```xml
android:networkSecurityConfig="@xml/network_security_config"
```

`debug-overrides` なので効くのは debuggable なビルド (debug) だけで、benchmark (debuggable ではない) からは testbed に
繋げない。使い終わったら `git checkout app/src/main/AndroidManifest.xml` と xml の削除で戻す。コミット前に `git status` で
残っていないことを確かめる。

## 確かめ方の例

* **共有から開く** (`Route.OpenLink`): ログインしていない方のサーバのポストの URL を取って共有する。
  ```sh
  url=$(curl -sk --resolve legacy.quasar.test:443:127.0.0.1 \
      "https://legacy.quasar.test/api/v1/timelines/public?local=true&limit=1" | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["url"])')
  adb shell "am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT '$url' \
      -n es.aria_on_the_planet.quasar.debug/es.aria_on_the_planet.quasar.OpenLink"
  ```
* **通知**: bots が動いていればお気に入り・ブースト・返信・フォローの通知が溜まる。足りなければ `./testbed.sh bots burst 50`。
* **gap・自動更新**: bots の流量 (12 秒ごと) で足りる。速くしたいなら `BOT_INTERVAL` を下げる。

## 版ごとの違い (分かっていること)

* **3.5.19 (legacy)**: `GET /api/v2/notifications` が無い (404)。既定の「Grouped」では通知が出ず、エラーとして 404 の
  HTML がそのまま画面に出る。設定 > Timelines > Grouping を「Individual」(v1) にすると出る。v2 → v1 の自動切り替え・
  バージョン判定はまだ無い。
* **3.5.19**: 引用を知らないので、4.x からの引用ポストは本文の「RE: <リンク>」として届く (サーバ側の仕様)。
* 引用 (bots の quote) は 4.5 以降のサーバだけで行う。

## つまずいたこと

* nginx で `location` に `proxy_set_header` を書くと `server` レベルのものが消え、`Host` が渡らず全リクエストが 403 に
  なった。ヘッダは各 `location` に書いてある (`proxy/nginx.conf`)。
* 3.5.19 のイメージは `/opt/mastodon` (4.x は `/mastodon`)。メディアのボリュームは root で作られるので、`up` が
  `chown 991:991` している。
* `init` はホストのユーザーで走らせる (root で作ると `state/` にホスト側から書けない)。
