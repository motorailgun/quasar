#!/usr/bin/env python3
"""Material Symbols (Rounded, weight 300) から Compose の ImageVector を生成する。

アイコンを足すときは ICONS に 1 行足して実行し直す:
    python3 scripts/icons/generate.py

SVG は Google Fonts の配信元 (fonts.gstatic.com) から取る。outlined = FILL 0、filled = FILL 1。
GRAD 0・opsz 24 (配信の既定)。
"""

import pathlib
import re
import sys
import urllib.request

WEIGHT = 300

# (Kotlin の名前, Material Symbols の名前, 右から左の言語で反転するか, 作る種類)
O, F = "Outlined", "Filled"
ICONS = [
    ("Add", "add", False, [F]),
    ("AddPhotoAlternate", "add_photo_alternate", False, [O]),
    ("AlternateEmail", "alternate_email", False, [O]),
    ("Article", "article", False, [O, F]),
    ("ArrowBack", "arrow_back", True, [O]),
    ("ArrowDownward", "arrow_downward", False, [F]),
    ("ArrowDropDown", "arrow_drop_down", False, [F]),
    ("AudioFile", "audio_file", False, [O]),
    ("Block", "block", False, [O]),
    ("Bookmark", "bookmark", False, [O, F]),
    ("BrokenImage", "broken_image", False, [O]),
    ("CalendarMonth", "calendar_month", False, [O]),
    ("Check", "check", False, [F]),
    ("CheckCircle", "check_circle", False, [F]),
    ("Close", "close", False, [O, F]),
    ("Delete", "delete", False, [O]),
    ("Download", "download", False, [O]),
    ("Edit", "edit", False, [O]),
    ("EmojiEmotions", "emoji_emotions", False, [O]),
    ("Error", "error", False, [O]),
    ("FormatQuote", "format_quote", False, [O]),
    ("Groups", "groups", False, [O, F]),
    ("Help", "help", True, [O]),
    ("Home", "home", False, [O]),
    ("Image", "image", False, [O, F]),
    ("KeyboardArrowRight", "keyboard_arrow_right", True, [F]),
    ("Language", "language", False, [O]),
    ("ContentCopy", "content_copy", False, [O]),
    ("Lock", "lock", False, [O]),
    ("Logout", "logout", True, [O]),
    ("Mail", "mail", False, [O]),
    ("MoreVert", "more_vert", False, [O]),
    ("Nightlight", "nightlight", False, [O]),
    ("Notifications", "notifications", False, [O]),
    ("OpenInBrowser", "open_in_browser", False, [O]),
    ("OpenInNew", "open_in_new", True, [O]),
    ("Palette", "palette", False, [O]),
    ("Pause", "pause", False, [F]),
    ("Person", "person", False, [O, F]),
    ("PersonAdd", "person_add", False, [O]),
    ("PersonCheck", "person_check", False, [O, F]),
    ("PlayArrow", "play_arrow", False, [F]),
    ("Poll", "poll", False, [O]),
    ("Public", "public", False, [O]),
    ("PushPin", "push_pin", False, [O]),
    ("Refresh", "refresh", False, [O]),
    ("Repeat", "repeat", False, [O, F]),
    ("Reply", "reply", True, [O]),
    ("Schedule", "schedule", False, [O]),
    ("Search", "search", False, [O]),
    ("Send", "send", True, [F]),
    ("Settings", "settings", False, [O]),
    ("SmartToy", "smart_toy", False, [O]),
    ("Star", "star", False, [O, F]),
    ("SubdirectoryArrowRight", "subdirectory_arrow_right", True, [O]),
    ("Sync", "sync", False, [O]),
    ("TaskAlt", "task_alt", False, [O]),
    ("Verified", "verified", False, [O]),
    ("Visibility", "visibility", False, [O]),
    ("VisibilityOff", "visibility_off", False, [O]),
    ("VolumeOff", "volume_off", True, [O]),
    ("VolumeUp", "volume_up", True, [O]),
    ("Warning", "warning", False, [O]),
]

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/main/java/es/aria_on_the_planet/quasar/ui/component/icon/Symbols.kt"


def fetch_paths(symbol: str, style: str) -> list[str]:
    variant = f"wght{WEIGHT}" + ("fill1" if style == F else "")
    url = f"https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsrounded/{symbol}/{variant}/24px.svg"
    with urllib.request.urlopen(url) as res:
        svg = res.read().decode()
    if 'viewBox="0 -960 960 960"' not in svg:
        sys.exit(f"{symbol}: unexpected viewBox")
    paths = re.findall(r'<path d="([^"]+)"', svg)
    if not paths or svg.count("<path") != len(paths):
        sys.exit(f"{symbol}: unexpected SVG: {svg}")
    return paths


def main() -> None:
    blocks = {O: [], F: []}
    for name, symbol, mirror, styles in ICONS:
        for style in styles:
            paths = ", ".join(f'"{p}"' for p in fetch_paths(symbol, style))
            blocks[style].append(
                f'        val {name}: ImageVector by lazy {{ symbol("{style}.{name}", autoMirror = {str(mirror).lower()}, {paths}) }}'
            )
    out = f"""// scripts/icons/generate.py が生成する。手で編集しない。
package es.aria_on_the_planet.quasar.ui.component.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Material Symbols (Rounded, weight {WEIGHT}) のアイコン。
 * [Outlined] は FILL 0 (未リアクション等)、[Filled] は FILL 1 (リアクション済み等)。
 * 向きのあるもの (矢印・返信等) は右から左の言語で反転する (autoMirror)。
 */
object Symbols {{
    object Outlined {{
{chr(10).join(blocks[O])}
    }}

    object Filled {{
{chr(10).join(blocks[F])}
    }}
}}

/** viewBox が `0 -960 960 960` の Material Symbols の SVG のパスから作る。 */
private fun symbol(name: String, autoMirror: Boolean, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
        autoMirror = autoMirror,
    ).apply {{
        addGroup(translationY = 960f)
        paths.forEach {{ addPath(addPathNodes(it), fill = SolidColor(Color.Black)) }}
        clearGroup()
    }}.build()
"""
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(out)
    print(f"wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
