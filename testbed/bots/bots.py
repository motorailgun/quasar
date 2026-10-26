"""テスト環境のアカウントを動かす (標準ライブラリのみ)。

  python bots.py wait          3 サーバの API が応答するまで待つ
  python bots.py setup         プロフィールの設定・全員を相互フォロー (冪等)
  python bots.py run           投稿・返信・お気に入り等を一定間隔で続ける (BOT_INTERVAL 秒、既定 12)
  python bots.py burst N       N 回の操作をすぐに行う

トークンは testbed.sh が state/tokens/<server>.json に書いたものを使う。
"""

import json
import os
import random
import struct
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zlib
from pathlib import Path

STATE = Path("/testbed/state")
SERVERS = ["alpha", "stable", "legacy"]
# 鍵アカウントにする番号 (フォローがリクエストになる)
LOCKED = {"user10"}


def log(*args):
    print(time.strftime("%H:%M:%S"), *args, flush=True)


class ApiError(Exception):
    def __init__(self, status, body):
        super().__init__(f"HTTP {status}: {body[:200]}")
        self.status = status


def request(url, method="GET", token=None, form=None, files=None, timeout=60):
    headers = {"User-Agent": "quasar-testbed-bots"}
    data = None
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if files:
        boundary = uuid.uuid4().hex
        data = multipart(boundary, form or [], files)
        headers["Content-Type"] = f"multipart/form-data; boundary={boundary}"
    elif form is not None:
        data = urllib.parse.urlencode(form).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as res:
            body = res.read()
    except urllib.error.HTTPError as e:
        raise ApiError(e.code, e.read().decode(errors="replace")) from None
    return json.loads(body) if body else None


def multipart(boundary, form, files):
    out = []
    for k, v in form:
        out.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
    for name, filename, ctype, content in files:
        out.append(
            f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'
            f"Content-Type: {ctype}\r\n\r\n".encode()
        )
        out.append(content + b"\r\n")
    out.append(f"--{boundary}--\r\n".encode())
    return b"".join(out)


def png(width, height, seed):
    """斜めの縞の PNG (メディア・アイコン用)。"""
    rnd = random.Random(seed)
    c1 = [rnd.randrange(256) for _ in range(3)]
    c2 = [rnd.randrange(256) for _ in range(3)]
    stripe = rnd.randrange(8, 40)
    rows = []
    for y in range(height):
        row = bytearray([0])
        for x in range(width):
            row += bytes(c1 if ((x + y) // stripe) % 2 else c2)
        rows.append(bytes(row))
    raw = zlib.compress(b"".join(rows), 9)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))

    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
        + chunk(b"IDAT", raw)
        + chunk(b"IEND", b"")
    )


class Server:
    def __init__(self, name):
        self.name = name
        data = json.loads((STATE / "tokens" / f"{name}.json").read_text())
        self.domain = data["domain"]
        self.emojis = data.get("emojis", [])
        self.bots = [Bot(self, a) for a in data["accounts"]]
        self._version = None

    @property
    def base(self):
        return f"https://{self.domain}"

    @property
    def version(self):
        if self._version is None:
            v = request(f"{self.base}/api/v1/instance")["version"]
            nums = [int(n) for n in v.split("-")[0].split("+")[0].split(".")[:2]]
            self._version = tuple(nums)
        return self._version

    @property
    def supports_quotes(self):
        return self.version >= (4, 5)


class Bot:
    def __init__(self, server, account):
        self.server = server
        self.username = account["username"]
        self.id = account["id"]
        self.token = account["token"]

    @property
    def acct(self):
        return f"{self.username}@{self.server.domain}"

    def __str__(self):
        return f"@{self.acct}"

    def api(self, method, path, form=None, files=None):
        return request(f"{self.server.base}{path}", method, self.token, form, files)

    def get(self, path, **params):
        query = urllib.parse.urlencode(params, doseq=True)
        return self.api("GET", f"{path}?{query}" if query else path)

    def resolve(self, other):
        """他のアカウントの、自分のサーバ内での ID。"""
        if other.server is self.server:
            return other.id
        res = self.get("/api/v2/search", q=f"@{other.acct}", resolve="true", type="accounts", limit=1)
        for a in res["accounts"]:
            if a["acct"].lower() == other.acct.lower():
                return a["id"]
        raise ApiError(404, f"could not resolve {other}")

    def home(self, limit=30):
        return [s for s in self.get("/api/v1/timelines/home", limit=limit) if s["account"]["id"] != self.id]


def load():
    servers = [Server(n) for n in SERVERS]
    return servers, [b for s in servers for b in s.bots]


# ---- 文面 ----

PHRASES = [
    "おはようございます", "今日もいい天気ですね", "ラーメン食べたい", "テスト投稿です",
    "Hello from the testbed!", "Just setting up my fediverse", "連合のテスト中",
    "長い長い文章を書いてみるテスト。" * 3, "絵文字のテスト 🎉🚀✨", "日本語と English の混在",
    "Compose のレイアウト確認用", "スクロールの負荷を確認したい", "Lorem ipsum dolor sit amet.",
]
TAGS = ["quasar", "testbed", "日本語タグ", "fediverse", "compose", "android"]


def compose_text(bot, everyone):
    parts = [random.choice(PHRASES)]
    if random.random() < 0.25:
        other = random.choice([b for b in everyone if b is not bot])
        parts.insert(0, f"@{other.username}" if other.server is bot.server else f"@{other.acct}")
    if random.random() < 0.35:
        parts.append(" ".join(f"#{t}" for t in random.sample(TAGS, random.randint(1, 3))))
    if bot.server.emojis and random.random() < 0.35:
        parts.append(" ".join(f":{e}:" for e in random.sample(bot.server.emojis, min(3, len(bot.server.emojis)))))
    if random.random() < 0.1:
        target = random.choice(SERVERS)
        parts.append(f"https://{target}.quasar.test/about")
    if random.random() < 0.08:
        parts.append("\n".join(f"{i}行目" for i in range(1, random.randint(10, 20))))
    return "\n".join(parts)


def visibility():
    return random.choices(["public", "unlisted", "private"], [6, 2, 1])[0]


# ---- 操作 ----

def act_post(bot, everyone):
    form = [("status", compose_text(bot, everyone)), ("visibility", visibility())]
    if random.random() < 0.15:
        form.append(("spoiler_text", random.choice(["ネタバレ", "CW テスト", "Long post", "食べ物"])))
    roll = random.random()
    if roll < 0.12:
        opts = random.sample(["りんご", "みかん", "ぶどう", "Option A", "Option B", "どれでもない"], random.randint(2, 4))
        form += [("poll[options][]", o) for o in opts]
        form += [("poll[expires_in]", str(random.choice([300, 3600, 86400]))),
                 ("poll[multiple]", "true" if random.random() < 0.3 else "false")]
    elif roll < 0.24:
        sensitive = random.random() < 0.3
        for i in range(random.randint(1, 4)):
            w, h = random.choice([(320, 240), (240, 320), (300, 300), (480, 160)])
            media = bot.api("POST", "/api/v2/media" if bot.server.version >= (3, 1) else "/api/v1/media",
                            form=[("description", f"テスト画像 {i + 1} ({w}x{h})")],
                            files=[("file", "image.png", "image/png", png(w, h, random.random()))])
            form.append(("media_ids[]", media["id"]))
        if sensitive:
            form.append(("sensitive", "true"))
        # 処理待ち (202) の画像を待つ
        time.sleep(2)
    if bot.server.supports_quotes:
        form.append(("quote_approval_policy", "public"))
    s = bot.api("POST", "/api/v1/statuses", form=form)
    return f"posted {s['id']} ({s['visibility']}{', poll' if s.get('poll') else ''}{', media' if s['media_attachments'] else ''})"


def pick(bot, cond=lambda s: True):
    candidates = [s for s in bot.home() if cond(s)]
    if not candidates:
        return None
    return random.choice(candidates)


def act_reply(bot, everyone):
    s = pick(bot, lambda s: s["reblog"] is None)
    if not s:
        return None
    vis = s["visibility"] if s["visibility"] in ("private", "direct") else random.choice(["public", "unlisted"])
    text = f"@{s['account']['acct']} {random.choice(['それな', 'いいね！', 'わかる', 'Nice!', 'へー', '詳しく'])}"
    r = bot.api("POST", "/api/v1/statuses", form=[("status", text), ("in_reply_to_id", s["id"]), ("visibility", vis)])
    return f"replied {r['id']} -> {s['id']}"


def act_toggle(kind):
    def act(bot, everyone):
        def cond(s):
            target = s["reblog"] or s
            if kind == "reblog" and target["visibility"] not in ("public", "unlisted"):
                return False
            return True

        s = pick(bot, cond)
        if not s:
            return None
        target = s["reblog"] or s
        done = target.get({"favourite": "favourited", "reblog": "reblogged", "bookmark": "bookmarked"}[kind])
        verb = f"un{kind}" if done and random.random() < 0.5 else kind
        if done and verb == kind:
            return None
        bot.api("POST", f"/api/v1/statuses/{target['id']}/{verb}")
        return f"{verb} {target['id']} by @{target['account']['acct']}"

    return act


def act_quote(bot, everyone):
    if not bot.server.supports_quotes:
        return None
    s = pick(bot, lambda s: (s["reblog"] or s)["visibility"] in ("public", "unlisted"))
    if not s:
        return None
    target = s["reblog"] or s
    form = [("status", random.choice(["これ", "引用のテスト", "Quote test", "見て"])), ("quoted_status_id", target["id"]),
            ("visibility", "public"), ("quote_approval_policy", "public")]
    q = bot.api("POST", "/api/v1/statuses", form=form)
    state = (q.get("quote") or {}).get("state")
    return f"quoted {target['id']} -> {q['id']} (state={state})"


def act_vote(bot, everyone):
    def cond(s):
        p = (s["reblog"] or s).get("poll")
        return p and not p["expired"] and not p.get("voted")

    s = pick(bot, cond)
    if not s:
        return None
    poll = (s["reblog"] or s)["poll"]
    n = len(poll["options"])
    choices = random.sample(range(n), random.randint(1, n)) if poll["multiple"] else [random.randrange(n)]
    bot.api("POST", f"/api/v1/polls/{poll['id']}/votes", form=[("choices[]", str(c)) for c in choices])
    return f"voted {choices} on poll {poll['id']}"


def own_posts(bot):
    return bot.get(f"/api/v1/accounts/{bot.id}/statuses", limit=20, exclude_reblogs="true")


def act_edit(bot, everyone):
    posts = [s for s in own_posts(bot) if not s["media_attachments"] and not s.get("poll")]
    if not posts:
        return None
    s = random.choice(posts)
    src = bot.get(f"/api/v1/statuses/{s['id']}/source")
    form = [("status", src["text"] + "\n(編集しました)"), ("spoiler_text", src.get("spoiler_text", ""))]
    bot.api("PUT", f"/api/v1/statuses/{s['id']}", form=form)
    return f"edited {s['id']}"


def act_delete(bot, everyone):
    posts = own_posts(bot)
    if len(posts) < 10:
        return None
    s = random.choice(posts[5:])
    bot.api("DELETE", f"/api/v1/statuses/{s['id']}")
    return f"deleted {s['id']}"


def act_follow_requests(bot, everyone):
    reqs = bot.get("/api/v1/follow_requests", limit=40)
    if not reqs:
        return None
    a = random.choice(reqs)
    verb = random.choices(["authorize", "reject"], [4, 1])[0]
    bot.api("POST", f"/api/v1/follow_requests/{a['id']}/{verb}")
    return f"{verb} follow request from @{a['acct']}"


def act_refollow(bot, everyone):
    other = random.choice([b for b in everyone if b is not bot])
    oid = bot.resolve(other)
    rel = bot.get("/api/v1/accounts/relationships", **{"id[]": oid})[0]
    if rel["following"] or rel["requested"]:
        bot.api("POST", f"/api/v1/accounts/{oid}/unfollow")
        return f"unfollowed {other}"
    bot.api("POST", f"/api/v1/accounts/{oid}/follow")
    return f"followed {other}"


ACTIONS = [
    (act_post, 30), (act_reply, 15), (act_toggle("favourite"), 25), (act_toggle("reblog"), 10),
    (act_toggle("bookmark"), 4), (act_quote, 5), (act_vote, 6), (act_edit, 2), (act_delete, 1),
    (act_follow_requests, 2), (act_refollow, 1),
]


def step(everyone):
    bot = random.choice(everyone)
    fn = random.choices([a for a, _ in ACTIONS], [w for _, w in ACTIONS])[0]
    try:
        result = fn(bot, everyone)
        if result:
            log(f"{bot}: {result}")
    except (ApiError, OSError) as e:
        log(f"{bot}: {fn.__name__} failed: {e}")


# ---- コマンド ----

def cmd_wait():
    deadline = time.time() + 600
    for name in SERVERS:
        url = f"https://{name}.quasar.test/api/v1/instance"
        while True:
            try:
                v = request(url, timeout=10)["version"]
                log(f"{name}: up ({v})")
                break
            except Exception as e:
                if time.time() > deadline:
                    sys.exit(f"{name}: not ready: {e}")
                time.sleep(3)


def cmd_setup():
    servers, everyone = load()
    for bot in everyone:
        n = int(bot.username.removeprefix("user"))
        form = [
            ("display_name", f"{bot.server.name.capitalize()} {n:02d}" + (f" :{bot.server.emojis[0]}:" if bot.server.emojis else "")),
            ("note", f"{bot.server.name} サーバのテスト用アカウント {n} 号です。\n#quasar #testbed"),
            ("locked", "true" if bot.username in LOCKED else "false"),
            ("bot", "true" if n == 9 else "false"),
            ("fields_attributes[0][name]", "Server"),
            ("fields_attributes[0][value]", f"https://{bot.server.domain}"),
            ("fields_attributes[1][name]", "番号"),
            ("fields_attributes[1][value]", str(n)),
        ]
        files = [("avatar", "avatar.png", "image/png", png(128, 128, f"{bot.acct}-avatar")),
                 ("header", "header.png", "image/png", png(600, 200, f"{bot.acct}-header"))]
        try:
            bot.api("PATCH", "/api/v1/accounts/update_credentials", form=form, files=files)
            log(f"{bot}: profile updated")
        except ApiError as e:
            log(f"{bot}: profile failed: {e}")

    for bot in everyone:
        followed = 0
        for other in everyone:
            if other is bot:
                continue
            try:
                oid = bot.resolve(other)
                rel = bot.get("/api/v1/accounts/relationships", **{"id[]": oid})[0]
                if not rel["following"] and not rel["requested"]:
                    bot.api("POST", f"/api/v1/accounts/{oid}/follow")
                    followed += 1
            except ApiError as e:
                log(f"{bot}: follow {other} failed: {e}")
        log(f"{bot}: followed {followed} accounts")

    # 鍵アカウントは半分ほど承認し、残りはリクエストのまま置いておく
    time.sleep(5)
    for bot in everyone:
        if bot.username not in LOCKED:
            continue
        reqs = bot.get("/api/v1/follow_requests", limit=80)
        for a in reqs[: len(reqs) // 2]:
            bot.api("POST", f"/api/v1/follow_requests/{a['id']}/authorize")
        log(f"{bot}: authorized {len(reqs) // 2} of {len(reqs)} follow requests")


def cmd_run():
    _, everyone = load()
    interval = float(os.environ.get("BOT_INTERVAL", "12"))
    log(f"running {len(everyone)} bots, interval {interval}s")
    while True:
        step(everyone)
        time.sleep(random.uniform(interval * 0.5, interval * 1.5))


def cmd_burst(n):
    _, everyone = load()
    for _ in range(n):
        step(everyone)


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "run"
    if cmd == "wait":
        cmd_wait()
    elif cmd == "setup":
        cmd_setup()
    elif cmd == "run":
        cmd_run()
    elif cmd == "burst":
        cmd_burst(int(sys.argv[2]) if len(sys.argv) > 2 else 20)
    else:
        sys.exit(__doc__)
