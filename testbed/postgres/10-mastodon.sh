#!/bin/sh
# 1 つの Postgres に、サーバごとのロールとデータベースを作る (データが空の初回起動時だけ実行される)
set -eu
for s in alpha stable legacy; do
  DB_PASS=$(sed -n 's/^DB_PASS=//p' "/testbed/state/secrets/$s.env")
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<EOF
CREATE ROLE mastodon_$s LOGIN PASSWORD '$DB_PASS';
CREATE DATABASE mastodon_$s OWNER mastodon_$s;
EOF
done
