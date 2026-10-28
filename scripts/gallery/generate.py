#!/usr/bin/env python3
"""
デバッグ用ギャラリー (app/src/debug, DebugGalleryActivity) の画像と JSON を生成する。

    python3 scripts/gallery/generate.py

出力は app/src/debug/assets/gallery/ (images/ と *.json)。中身は毎回同じになる (乱数を使わない)。
画像はすべてここで描いたもので、実在の投稿・ユーザー・サーバの情報は含めない (ドメインは予約済みの .invalid)。
JSON は Mastodon の API の応答と同じ形 (Status / Account / CustomEmoji) で、アプリの DTO と mapper でそのまま読む。

必要なもの: Pillow (APNG とアニメーション WebP の書き出しに対応した版)。
"""

import json
import math
import shutil
from datetime import datetime, timedelta, timezone
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "app" / "src" / "debug" / "assets" / "gallery"
IMAGES = OUT / "images"
# Coil は file:///android_asset/ を assets として読む
ASSET_URL = "file:///android_asset/gallery/images/"
SERVER = "https://gallery.invalid"
# 投稿日時の起点 (過去にしておく。未来だと相対時刻が「0s」になる)。投稿ごとに 1 分ずつ古くする
CREATED_AT = datetime(2026, 1, 1, tzinfo=timezone.utc)

PALETTE = [(239, 83, 80), (255, 167, 38), (255, 238, 88), (102, 187, 106), (66, 165, 245), (171, 71, 188)]


# ---------------------------------------------------------------- 画像

def spinner_frame(size, i, n, color, dots=8):
    """点が回るスピナー (i / n コマ目)。"""
    w, h = size
    im = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    cx, cy, r = w / 2, h / 2, min(w, h) * 0.36
    for k in range(dots):
        a = 2 * math.pi * (k / dots + i / n)
        x, y = cx + r * math.cos(a), cy + r * math.sin(a)
        dot = min(w, h) * (0.05 + 0.06 * k / dots)
        d.ellipse((x - dot, y - dot, x + dot, y + dot), fill=color + (255,))
    return im


def bounce_frame(size, i, n, color):
    """跳ねる玉。"""
    w, h = size
    im = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    t = abs(math.sin(math.pi * i / n))
    r = min(w, h) * 0.18
    x = w / 2
    y = h - r - (h - 2 * r) * t
    d.ellipse((x - r, y - r, x + r, y + r), fill=color + (255,))
    return im


def face_frame(size, i, n, color, blink_frames=()):
    """丸い顔 (blink_frames のコマで目を閉じる)。"""
    w, h = size
    im = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    m = min(w, h) * 0.06
    d.ellipse((m, m, w - m, h - m), fill=color + (255,))
    ey, ex, er = h * 0.4, w * 0.18, min(w, h) * 0.07
    for x in (w / 2 - ex, w / 2 + ex):
        if i in blink_frames:
            d.line((x - er, ey, x + er, ey), fill=(40, 40, 40, 255), width=max(2, int(er / 2)))
        else:
            d.ellipse((x - er, ey - er, x + er, ey + er), fill=(40, 40, 40, 255))
    d.arc((w * 0.3, h * 0.45, w * 0.7, h * 0.75), 20, 160, fill=(40, 40, 40, 255), width=max(2, int(er / 2)))
    return im


def hue_frame(size, i, n):
    """色が回る縞 (ヘッダ・添付用)。"""
    w, h = size
    im = Image.new("RGBA", size, (0, 0, 0, 255))
    d = ImageDraw.Draw(im)
    bands = 6
    for k in range(bands + 1):
        c = PALETTE[(k + i) % len(PALETTE)]
        x0 = (k - (i / n)) * w / bands
        d.rectangle((x0, 0, x0 + w / bands, h), fill=c + (255,))
    return im


def gradient(size, c0, c1):
    w, h = size
    im = Image.new("RGB", size)
    d = ImageDraw.Draw(im)
    for y in range(h):
        t = y / max(1, h - 1)
        d.line((0, y, w, y), fill=tuple(int(a + (b - a) * t) for a, b in zip(c0, c1)))
    return im


def save_animation(name, frames, durations, fmt, loop=0):
    """アニメーション画像と、先頭のコマの静止画 (<name>_static.png) を書き出す。URL (アニメ, 静止) を返す。"""
    path = IMAGES / name
    first = frames[0]
    if fmt == "gif":
        # RGBA のまま渡すと Pillow が透過色 1 色の GIF にする (半透明は無いので縁は粗い)
        first.save(path, format="GIF", save_all=True, append_images=frames[1:], duration=durations, loop=loop,
                   disposal=2)
    elif fmt == "apng":
        # PIL は loop=0 を無限 (num_plays=0) として書く
        first.save(path, format="PNG", save_all=True, append_images=frames[1:], duration=durations, loop=loop,
                   disposal=1, default_image=False)
    elif fmt == "webp":
        first.save(path, format="WEBP", save_all=True, append_images=frames[1:], duration=durations, loop=loop,
                   lossless=True)
    else:
        raise ValueError(fmt)
    static = IMAGES / (path.stem + "_static.png")
    first.save(static, format="PNG")
    return ASSET_URL + path.name, ASSET_URL + static.name


def save_static(name, image):
    image.save(IMAGES / name)
    return ASSET_URL + name


def build_images():
    if IMAGES.exists():
        shutil.rmtree(IMAGES)
    IMAGES.mkdir(parents=True)
    e = {}  # shortcode -> (url, static_url)

    def emoji(code, file, frames, durations, fmt, loop=0):
        e[code] = save_animation(file, frames, durations, fmt, loop)

    s = (128, 128)
    emoji("spin_fast", "spin_fast.png", [spinner_frame(s, i, 8, PALETTE[4]) for i in range(8)], 20, "apng")
    emoji("spin_slow", "spin_slow.png", [spinner_frame(s, i, 12, PALETTE[0]) for i in range(12)], 70, "apng")
    # 0ms のコマ (アプリは 100ms として扱う)
    emoji("spin_zero", "spin_zero.png", [spinner_frame(s, i, 6, PALETTE[5], dots=6) for i in range(6)], 0, "apng")
    # 3 回だけ再生して最後のコマで止まる
    emoji("bounce_once", "bounce_once.png", [bounce_frame(s, i, 10, PALETTE[3]) for i in range(10)], 100, "apng", loop=3)
    emoji("wide_wave", "wide_wave.png", [hue_frame((384, 128), i, 8) for i in range(8)], 50, "apng")
    emoji("bounce_gif", "bounce_gif.gif", [bounce_frame(s, i, 10, PALETTE[1]) for i in range(10)], 50, "gif")
    emoji("blink_gif", "blink_gif.gif", [face_frame(s, i, 2, PALETTE[2], blink_frames=(1,)) for i in range(2)],
          [1200, 150], "gif")
    emoji("wobble_webp", "wobble_webp.webp", [spinner_frame(s, i, 12, PALETTE[2], dots=5) for i in range(12)], 40, "webp")
    emoji("pulse_webp", "pulse_webp.webp", [face_frame(s, i, 8, PALETTE[i % len(PALETTE)]) for i in range(8)], 80, "webp")
    still = face_frame(s, 0, 1, PALETTE[4])
    url = save_static("still.png", still)
    e["still"] = (url, url)

    a = (256, 256)
    avatars = {
        "static": (lambda u: (u, u))(save_static("avatar_static.png", face_frame(a, 0, 1, PALETTE[4]))),
        "gif": save_animation("avatar_gif.gif", [face_frame(a, i, 2, PALETTE[1], blink_frames=(1,)) for i in range(2)],
                              [1500, 150], "gif"),
        "apng": save_animation("avatar_apng.png", [face_frame(a, 0, 1, PALETTE[i]) for i in range(6)], 120, "apng"),
        "webp": save_animation("avatar_webp.webp", [spinner_frame(a, i, 10, PALETTE[5]) for i in range(10)], 60, "webp"),
    }
    header = save_animation("header.gif", [hue_frame((750, 250), i, 12) for i in range(12)], 100, "gif")
    media = [save_static(f"media{i}.jpg", gradient((800, 600), PALETTE[i], PALETTE[(i + 2) % 6]).convert("RGB"))
             for i in range(4)]
    # 閲覧注意のぼかしが分かるよう、細かい縞にする (滑らかなグラデーションはぼかしても見た目が変わらない)
    tall = save_static("media_tall.jpg", hue_frame((600, 900), 0, 1).convert("RGB"))
    stripes = save_static("media_stripes.jpg", hue_frame((800, 600), 3, 6).convert("RGB"))
    return e, avatars, header, media + [stripes], tall


# ---------------------------------------------------------------- JSON

def emoji_json(code, urls, category=None):
    return {"shortcode": code, "url": urls[0], "static_url": urls[1], "visible_in_picker": True, "category": category}


def account(acct_id, username, name, avatar, emojis, note="", header=None, domain=None, **extra):
    acct = username if domain is None else f"{username}@{domain}"
    base = SERVER if domain is None else f"https://{domain}"
    data = {
        "id": acct_id, "username": username, "acct": acct, "url": f"{base}/@{username}",
        "display_name": name, "note": note, "avatar": avatar[0], "avatar_static": avatar[1],
        "header": header[0] if header else f"{SERVER}/headers/original/missing.png",
        "header_static": header[1] if header else f"{SERVER}/headers/original/missing.png",
        "emojis": emojis, "created_at": "2024-04-01T00:00:00.000Z",
        "statuses_count": 1234, "following_count": 56, "followers_count": 78,
    }
    data.update(extra)
    return data


class Statuses:
    def __init__(self):
        self.n = 0

    def make(self, acc, content, emojis=(), **extra):
        self.n += 1
        status_id = str(1000 + self.n)
        data = {
            "id": status_id,
            # 投稿者のサーバのページ (リモートなら「<ホスト> で開く」がメニューに出る)
            "url": f"{acc['url']}/{status_id}",
            "uri": f"{acc['url'].rsplit('/@', 1)[0]}/users/{acc['username']}/statuses/{status_id}",
            "created_at": (CREATED_AT - timedelta(minutes=self.n)).strftime("%Y-%m-%dT%H:%M:%S.000Z"),
            "account": acc,
            "content": content, "visibility": "public", "emojis": list(emojis),
            "replies_count": self.n % 3, "reblogs_count": self.n % 5, "favourites_count": self.n % 7,
        }
        data.update(extra)
        return data


def p(text):
    return f"<p>{text}</p>"


def build_fixtures(e, avatars, header, media, tall):
    E = {code: emoji_json(code, urls) for code, urls in e.items()}
    animated = [c for c in e if c != "still"]

    def ems(*codes):
        return [E[c] for c in codes]

    alice = account("1", "alice", "Alice", avatars["static"], [])
    bob = account("2", "bob", "Bob :blink_gif: :spin_fast:", avatars["gif"], ems("blink_gif", "spin_fast"))
    carol = account("3", "carol", "Carol :wobble_webp:", avatars["apng"], ems("wobble_webp"))
    dave = account("4", "dave", "Dave", avatars["webp"], [], domain="remote.invalid")
    longname = account("5", "longname",
                       "とても長い表示名のユーザー :wide_wave: :pulse_webp: Very Long Display Name That Overflows",
                       avatars["static"], ems("wide_wave", "pulse_webp"), bot=True)
    st = Statuses()

    # --- animated-emoji: 本文・表示名・CW・投票にアニメ絵文字 (形式ごと)
    animated_emoji = [
        st.make(alice, p("APNG: :spin_fast: (20ms) :spin_slow: (70ms) :spin_zero: (0ms → 100ms) :bounce_once: (3 回で停止)"),
                ems("spin_fast", "spin_slow", "spin_zero", "bounce_once")),
        st.make(alice, p("GIF: :bounce_gif: :blink_gif: / WebP: :wobble_webp: :pulse_webp: / 静止: :still:"),
                ems("bounce_gif", "blink_gif", "wobble_webp", "pulse_webp", "still")),
        st.make(alice, p("横長の絵文字 :wide_wave: と文字の高さ :spin_fast: が揃うか"), ems("wide_wave", "spin_fast")),
        st.make(bob, p("表示名に GIF / APNG の絵文字、アイコンは GIF")),
        st.make(carol, p("表示名に WebP の絵文字、アイコンは APNG")),
        st.make(longname, p("長い表示名 + 横長の絵文字")),
        st.make(alice, p("CW の中身 :pulse_webp:"), ems("spin_slow", "pulse_webp"), spoiler_text="CW にも絵文字 :spin_slow:"),
        st.make(alice, p("投票の選択肢に絵文字"), ems("spin_fast", "bounce_gif", "wobble_webp"), poll={
            "id": "p1", "expires_at": "2099-01-01T00:00:00.000Z", "expired": False, "multiple": False,
            "votes_count": 12, "voters_count": 12,
            "options": [{"title": ":spin_fast: APNG", "votes_count": 5}, {"title": ":bounce_gif: GIF", "votes_count": 4},
                        {"title": ":wobble_webp: WebP", "votes_count": 3}],
            "emojis": ems("spin_fast", "bounce_gif", "wobble_webp"), "voted": False, "own_votes": [],
        }),
    ]

    # --- animated-avatars: アイコンが動く投稿を並べる
    avatar_users = [(bob, "GIF"), (carol, "APNG"), (dave, "WebP"), (alice, "静止画")] * 4
    animated_avatars = [st.make(u, p(f"アイコンの形式: {kind} ({i + 1})")) for i, (u, kind) in enumerate(avatar_users)]

    # --- emoji-stress: 1 投稿にアニメ絵文字を大量に
    many = " ".join(f":{animated[i % len(animated)]}:" for i in range(60))
    emoji_stress = [
        st.make(alice, p(many), ems(*animated)),
        st.make(alice, p(many), ems(*animated)),
    ]

    # --- emoji-picker: ピッカーに出す絵文字 (カテゴリ付き、アニメ多め)
    picker = []
    for i in range(80):
        code = animated[i % len(animated)] if i % 5 else "still"
        urls = e[code]
        picker.append(emoji_json(f"{code}_{i:02d}", urls, category=["Animated", "Mixed", None][i % 3]))

    # --- profile: ヘッダ・アイコンが動くプロフィール + フィールドに絵文字
    profile_account = account(
        "6", "gallery", "Gallery :spin_slow: Profile :pulse_webp:", avatars["gif"],
        ems("spin_slow", "pulse_webp", "bounce_gif", "wobble_webp"),
        note=p("bio にも絵文字 :bounce_gif: :wobble_webp:<br>2 行目"),
        header=header, locked=True, bot=True,
        fields=[
            {"name": "Web :spin_slow:", "value": f'<a href="{SERVER}/">gallery.invalid</a>',
             "verified_at": "2025-01-01T00:00:00.000+00:00"},
            {"name": "絵文字 :bounce_gif:", "value": ":wobble_webp: :pulse_webp:", "verified_at": None},
        ],
    )
    profile_posts = [st.make(profile_account, p(f"プロフィールの投稿 {i + 1} :spin_slow:"), ems("spin_slow")) for i in range(6)]

    # --- post-variety: 表示の組み合わせ
    stripes = media[4]
    media = media[:4]

    def mediaj(i, url, w=800, h=600, kind="image", desc=None):
        return {"id": f"m{i}", "type": kind, "url": url, "preview_url": url, "description": desc,
                "meta": {"original": {"width": w, "height": h}, "small": {"width": w // 2, "height": h // 2}}}

    quoted = st.make(carol, p("引用される投稿 :wobble_webp:"), ems("wobble_webp"))
    reply_target = st.make(alice, p("リプライ先"))
    post_variety = [
        # 本文の空行は段落の切れ目 (</p><p>) で来る。空行として表示されるか
        st.make(bob, p("1 段落目") + p("空行の後の 2 段落目<br>改行だけの行") + p("3 段落目")),
        st.make(alice, p("添付 4 枚"), media_attachments=[mediaj(i, u) for i, u in enumerate(media)]),
        st.make(alice, p("閲覧注意の添付 (ぼかし)"), sensitive=True,
                media_attachments=[mediaj(10, stripes), mediaj(11, tall, 600, 900)]),
        st.make(bob, p("CW 付き"), spoiler_text="ネタバレ注意"),
        st.make(alice, p("締め切った投票"), poll={
            "id": "p2", "expires_at": "2026-01-01T00:00:00.000Z", "expired": True, "multiple": True,
            "votes_count": 30, "voters_count": 20,
            "options": [{"title": "A", "votes_count": 15}, {"title": "B", "votes_count": 10}, {"title": "C", "votes_count": 5}],
            "emojis": [], "voted": True, "own_votes": [0, 2],
        }),
        st.make(dave, p("引用付き"), quote={"state": "accepted", "quoted_status": quoted}),
        st.make(alice, p("リプライ"), in_reply_to_id=reply_target["id"], in_reply_to_account_id=alice["id"]),
        st.make(alice, p(f'リンクプレビュー <a href="{SERVER}/article">{SERVER}/article</a>'), card={
            "url": f"{SERVER}/article", "title": "記事のタイトル", "description": "記事の説明文", "image": media[1]}),
        st.make(longname, p("未収載"), visibility="unlisted"),
        st.make(alice, p("フォロワー限定"), visibility="private"),
        st.make(bob, p("ブーストされた投稿"), reblog=st.make(carol, p("ブーストの中身 :pulse_webp:"), ems("pulse_webp"))),
    ]

    return {
        "animated-emoji.json": animated_emoji,
        "animated-avatars.json": animated_avatars,
        "emoji-stress.json": emoji_stress,
        "emoji-picker.json": picker,
        "profile.json": {"account": profile_account, "statuses": profile_posts},
        "post-variety.json": post_variety,
    }


def main():
    e, avatars, header, media, tall = build_images()
    for name, data in build_fixtures(e, avatars, header, media, tall).items():
        (OUT / name).write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    total = sum(f.stat().st_size for f in OUT.rglob("*") if f.is_file())
    print(f"wrote {OUT.relative_to(ROOT)} ({total / 1024:.0f} KiB)")


if __name__ == "__main__":
    main()
