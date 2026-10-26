# testbed: 動作確認用の連合環境

自分の管理下にない実サーバで確かめる代わりに、Docker の中に Mastodon を 3 台立てて互いに連合させ、
10 アカウントずつが投稿・リアクションし合う環境。

| ドメイン | 版 (`.env`) |
| --- | --- |
| `alpha.quasar.test` | latest alpha (`ALPHA_TAG`。alpha は版で tag されないので main の nightly を日付で固定) |
| `stable.quasar.test` | latest stable (`STABLE_TAG`) |
| `legacy.quasar.test` | 3.5.19 (`LEGACY_TAG`) |

## 使い方

```sh
./testbed.sh up            # 初回は数分。初期化 → DB → 起動 → アカウント作成 → 全員を相互フォロー
./testbed.sh bots start    # 自動投稿を始める (BOT_INTERVAL 秒ごと、既定 12)。stop / logs / burst [N]
./testbed.sh accounts      # アカウント・ID・トークンの一覧
./testbed.sh down          # 止める (データは残る)
./testbed.sh reset         # データ・CA・トークンを全部消す
```

版を変えるときは `.env` を書き換えて `./testbed.sh up` (既存の DB には `db:migrate` が走る)。

## 構成

* `compose.yaml`: サーバごとに web / sidekiq / streaming / redis。Postgres は 1 つを共有し、サーバごとにロールと DB を
  分ける (`postgres/10-mastodon.sh`)。
* **閉じ込め**: Mastodon・Postgres・Redis・bots は `internal` のネットワーク (`fediverse`) だけに繋がり、外には出られない。
  `proxy` (nginx) だけが外向きにも繋がり、3 つのドメインの別名を持って TLS を終端する。連合の通信も proxy を通る
  (`ALLOWED_PRIVATE_ADDRESSES` でプライベートアドレスへの配送を許している)。
* `state/` (git 管理外): `init` が作る自前の CA・サーバ証明書 (`*.quasar.test`)・各サーバの秘密の値と、
  `mastodon/accounts.rb` (`rails runner`) が作ったトークン (`tokens/<server>.json`)。各コンテナは CA を `SSL_CERT_FILE` で信頼する。
* アカウント: 各サーバに `user1`〜`user10` (パスワード `quasar-testbed`、メールは `userN@<domain>`)。`user10` は鍵
  アカウント (フォローリクエストを半分だけ承認済み)、`user9` は bot。カスタム絵文字 `:quasar:` `:testbed:` `:blobok:`。
* `bots/bots.py` (標準ライブラリのみ): 投稿 (CW・投票・画像・メンション・ハッシュタグ・長文)・返信・お気に入り・ブースト・
  ブックマーク・引用 (4.5+)・投票・編集・削除・フォローリクエストへの返事・フォロー / 解除。

## 端末 (エミュレーター) から繋ぐ

proxy は `127.0.0.1:443`、端末用の DNS (`dns` サービス、dnsmasq) は `127.0.0.1:53` に出ている。DNS は `*.quasar.test` を
`DEVICE_HOST_ADDR` (`.env`、エミュレーターから見たホスト = `10.0.2.2`) に向け、他は普通に引く。

エミュレーターの起動・CA のインストール・アプリ側の設定 (ローカルのみ)・ログインの手順は
[docs/testbed.md](../docs/testbed.md)。
