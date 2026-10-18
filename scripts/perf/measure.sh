#!/bin/bash
# アプリの描画の負荷を測る: 描いたフレーム数 (fps)・ジャンク・CPU 時間 (プロセス / main / RenderThread)。
#
#   scripts/perf/measure.sh <label> <seconds> [swipe]
#
# - 端末は adb の既定 (複数あるときは ANDROID_SERIAL か ADB="adb -t 3" のように指定)。
# - 測る前に目的の画面を開いておく。ギャラリーなら:
#     adb shell am start --activity-clear-task -n es.aria_on_the_planet.quasar.debug/es.aria_on_the_planet.quasar.debug.DebugGalleryActivity \
#         --es scenario emoji-picker [--ez animate_images false] [--ei max_fps 60]
# - 性能は debug ビルドでは測らない (Compose が最適化されず数倍遅い)。AGENTS.md の「実機での確認」を参照。
# - CPU% は 1 コア比。fps は gfxinfo の値で、プロセス内の全ウィンドウ (ボトムシート等) の合計。
# - swipe を付けると、測っている間 上下にスクロールし続ける。
set -u
# benchmark ビルドは debug と同じ applicationId (.debug)。release を測るなら PKG=es.aria_on_the_planet.quasar
PKG=${PKG:-es.aria_on_the_planet.quasar.debug}
ADB=${ADB:-adb}
label=$1; secs=$2; mode=${3:-}
P=$($ADB shell pidof $PKG)
if [ -z "$P" ]; then echo "$PKG is not running" >&2; exit 1; fi
# 合計 / main / RenderThread (2 本あるので合計) の utime + stime (単位は 1/100 秒)
snap() {
  $ADB shell "set -- \$(cut -d' ' -f14-15 /proc/$P/stat); p=\$((\$1+\$2)); set -- \$(cut -d' ' -f14-15 /proc/$P/task/$P/stat); m=\$((\$1+\$2)); r=0; for t in /proc/$P/task/*; do if [ \"\$(cat \$t/comm 2>/dev/null)\" = RenderThread ]; then set -- \$(cut -d' ' -f14-15 \$t/stat 2>/dev/null) 0 0; r=\$((r+\$1+\$2)); fi; done; echo \$p \$m \$r"
}
$ADB shell dumpsys gfxinfo $PKG reset >/dev/null
a=($(snap)); t0=$(date +%s.%N)
if [ "$mode" = swipe ]; then
  end=$(( $(date +%s) + secs )); dir=0
  while [ "$(date +%s)" -lt $end ]; do
    if [ $dir = 0 ]; then $ADB shell input swipe 540 1800 540 700 600; else $ADB shell input swipe 540 700 540 1800 600; fi
    dir=$((1 - dir)); sleep 0.3
  done
else
  sleep "$secs"
fi
b=($(snap)); t1=$(date +%s.%N)
g=$($ADB shell dumpsys gfxinfo $PKG)
el=$(echo "$t1 - $t0" | bc)
frames=$(echo "$g" | grep -m1 "Total frames rendered" | awk '{print $4}')
jank=$(echo "$g" | grep -m1 "Janky frames:" | sed 's/.*Janky frames: //')
pct() { echo "scale=1; $1 / $el" | bc; }
printf "%-28s %5.1fs frames=%-5s fps=%-6s cpu%%=%-5s main=%-5s RT=%-5s jank=%s p50=%s p90=%s p99=%s\n" \
  "$label" "$el" "$frames" "$(pct "$frames")" "$(pct $((b[0] - a[0])))" "$(pct $((b[1] - a[1])))" "$(pct $((b[2] - a[2])))" \
  "$jank" \
  "$(echo "$g" | grep -m1 "50th percentile" | awk '{print $3}')" \
  "$(echo "$g" | grep -m1 "90th percentile" | awk '{print $3}')" \
  "$(echo "$g" | grep -m1 "99th percentile" | awk '{print $3}')"
