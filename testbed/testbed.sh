#!/bin/sh
# Quasar の動作確認用の連合環境 (README.md)。
#
#   ./testbed.sh up            初期化 (初回のみ) → DB の準備 → 全サーバ起動 → アカウント作成 → 相互フォロー
#   ./testbed.sh down          止める (データは残す)
#   ./testbed.sh reset         データごと消す (state/ の CA・トークンも)
#   ./testbed.sh bots start    自動投稿を始める (stop / logs / burst [N] / setup)
#   ./testbed.sh accounts      アカウントとトークンの一覧
#   ./testbed.sh ps | logs [service...]
set -eu
cd "$(dirname "$0")"

SERVERS="alpha stable legacy"
dc() { docker compose "$@"; }
log() { printf '\033[1m==> %s\033[0m\n' "$*"; }

wait_healthy() {
  for svc in "$@"; do
    printf 'waiting for %s' "$svc"
    i=0
    while :; do
      cid=$(dc ps -q "$svc")
      st=$(docker inspect -f '{{.State.Health.Status}}' "$cid" 2>/dev/null || echo starting)
      [ "$st" = healthy ] && break
      i=$((i + 1))
      if [ $i -gt 120 ]; then echo; echo "$svc did not become healthy" >&2; dc logs --tail 50 "$svc" >&2; exit 1; fi
      printf .
      sleep 3
    done
    echo " ok"
  done
}

prepare_db() {
  s=$1
  has=$(dc exec -T db psql -U postgres -d "mastodon_$s" -tAc "select to_regclass('public.accounts') is not null")
  if [ "$has" = t ]; then
    log "$s: db:migrate"
    dc run --rm --no-deps "$s-web" bundle exec rails db:migrate
  else
    log "$s: db:schema:load + db:seed"
    dc run --rm --no-deps -e DISABLE_DATABASE_ENVIRONMENT_CHECK=1 "$s-web" bundle exec rails db:schema:load db:seed
  fi
}

create_accounts() {
  s=$1
  log "$s: accounts"
  out=$(dc exec -T "$s-web" bundle exec rails runner /testbed/mastodon/accounts.rb | tail -n 1)
  case "$out" in
    '{'*) ;;
    *) echo "$s: accounts.rb failed" >&2; exit 1 ;;
  esac
  # state/ はコンテナからは読み取り専用なので、ホスト側で書く
  printf '%s\n' "$out" > "state/tokens/$s.json"
}

cmd_up() {
  log "init (CA, secrets)"
  mkdir -p state
  dc --profile tools run --rm --build --user "$(id -u):$(id -g)" init
  dc up -d db alpha-redis stable-redis legacy-redis
  wait_healthy db
  for s in $SERVERS; do prepare_db "$s"; done
  log "starting servers"
  dc up -d --remove-orphans
  # メディアのボリュームは root で作られるので、Mastodon (uid 991) が書けるようにする
  for s in $SERVERS; do
    docker run --rm -v "quasar-testbed_$s-system:/v" alpine:3.22 chown 991:991 /v
  done
  wait_healthy alpha-web stable-web legacy-web
  for s in $SERVERS; do create_accounts "$s"; done
  log "bots: wait + setup (profiles, follows)"
  dc run --rm bots python -u bots.py wait
  dc run --rm bots python -u bots.py setup
  log "ready. ./testbed.sh bots start で自動投稿を始める"
}

cmd_bots() {
  case "${1:-}" in
    start) dc --profile bots up -d bots ;;
    stop) dc --profile bots stop bots ;;
    logs) dc --profile bots logs -f bots ;;
    burst) dc run --rm bots python -u bots.py burst "${2:-20}" ;;
    setup) dc run --rm bots python -u bots.py setup ;;
    *) echo "usage: $0 bots start|stop|logs|burst [N]|setup" >&2; exit 2 ;;
  esac
}

cmd_accounts() {
  for s in $SERVERS; do
    [ -f "state/tokens/$s.json" ] || continue
    docker run --rm -i python:3.13-alpine python -c '
import json, sys
d = json.load(sys.stdin)
print("# %s  (password: %s)" % (d["domain"], d["password"]))
for a in d["accounts"]:
    print("  %-7s id=%-20s token=%s" % (a["username"], a["id"], a["token"]))
' < "state/tokens/$s.json"
  done
}

case "${1:-}" in
  up) cmd_up ;;
  down) dc --profile bots down ;;
  reset)
    dc --profile bots --profile tools down -v
    rm -rf state
    ;;
  bots) shift; cmd_bots "$@" ;;
  accounts) cmd_accounts ;;
  ps) dc --profile bots ps ;;
  logs) shift; dc --profile bots logs -f "$@" ;;
  *) sed -n '2,10p' "$0" >&2; exit 2 ;;
esac
