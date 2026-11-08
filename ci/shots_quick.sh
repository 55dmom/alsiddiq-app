#!/bin/bash
# Short screenshot pass (build 11: logo inside the gold ring + his 6 tools in their original look).
# Used by CI only while ci/QUICK_SHOTS exists; the full pass is ci/shots.sh.
set -x
APK="$1"
PKG=com.alsiddiq.app.debug
ACT=com.alsiddiq.app.MainActivity
mkdir -p shots
adb wait-for-device
adb shell settings put global window_animation_scale 0
adb install -r "$APK" || exit 1
adb shell settings put global hide_error_dialogs 1 || true
nodlg() { adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1; }
st() { # name theme extra-args...
  local n="$1" th="$2"; shift 2
  adb shell am force-stop $PKG; adb shell am start -n $PKG/$ACT --es theme "$th" "$@"; sleep 12; nodlg; sleep 1
  adb exec-out screencap -p > "shots/$n.png"
  adb logcat -d -s AndroidRuntime:E | tail -40 > "shots/$n.crash.txt"; [ -s "shots/$n.crash.txt" ] || rm -f "shots/$n.crash.txt"
  adb logcat -c
}
tapt() { # tap the first on-screen element whose text/content-desc is exactly $1 (scrolls down up to 4 times)
  for i in 1 2 3 4 5; do
  adb exec-out uiautomator dump /dev/tty 2>/dev/null | python3 -c '
import re,sys
x=sys.stdin.read(); t=sys.argv[1]
for m in re.finditer(r"<node [^>]*>", x):
    n=m.group(0)
    if ("text=\""+t+"\"" in n) or ("content-desc=\""+t+"\"" in n):
        b=re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", n)
        if b:
            a=list(map(int,b.groups())); print((a[0]+a[2])//2, (a[1]+a[3])//2); break
' "$1" > /tmp/xy.txt
  if [ -s /tmp/xy.txt ]; then adb shell input tap $(cat /tmp/xy.txt); echo "tapped $1 at $(cat /tmp/xy.txt)"; sleep 3; return 0; fi
  adb shell input swipe 540 1700 540 900 300; sleep 1
  done
  echo "NOT FOUND: $1"; return 1
}
adb logcat -c
# opening screen: default phone (411dp), then 360dp, then a large phone (480dp)
st 01_gate_default default --es demo_login gate
st 01b_gate_night night --es demo_login gate
st 02_admin_home_header default --es demo_login admin --es route home
adb shell wm size 720x1280; adb shell wm density 320; sleep 2
st 03_gate_360dp default --es demo_login gate
adb shell wm size reset; adb shell wm density reset
adb shell wm size 1440x3200; adb shell wm density 480; sleep 2
st 04_gate_large_480dp default --es demo_login gate
adb shell wm size reset; adb shell wm density reset; sleep 2
# his 6 tools, opened while the app's «night» theme is on: they must keep their own original colours
st 10_tool_mushaf_page1 night --es open_tool mushaf_page --es page 1
st 11_tool_hifz night --es open_tool hifz
st 12_tool_khatma night --es open_tool khatma
st 13_tool_prayer night --es open_tool prayer
st 14_tool_adhkar night --es open_tool adhkar
st 15_tool_duas night --es open_tool duas
# v3.8 admin pages (demo data)
st 05_viewer_home_salaam_star default --es demo_login viewer --es route home
adb shell input swipe 540 1600 540 700 400; sleep 2; adb exec-out screencap -p > shots/05b_viewer_home_scrolled.png
st 20_teacher_attendance default --es demo_login admin --es route teacher_attendance
st 21_budget default --es demo_login admin --es route budget
st 22_subscriptions default --es demo_login admin --es route subscriptions/c1
st 23_subscriptions_teacher default --es demo_login teacher --es route subscriptions
st 24_circle_banner default --es demo_login admin --es route circle/c1
st 25_circle_multi_teachers default --es demo_login admin --es route circle_edit/c2
st 28_circles_list_star default --es demo_login admin --es route circles
st 26_admin_more default --es demo_login admin --es route more
st 29_announcements_photo default --es demo_login admin --es route announcements
# back check (his 10:24 PM report on iPhone): الإدارة tab → a row → back must return to الإدارة, not the tools
st 30_back_start default --es demo_login admin --es route home
tapt "الإدارة"; adb exec-out screencap -p > shots/30a_admin_tab.png
tapt "حضور وغياب المعلمين"; adb exec-out screencap -p > shots/30b_row_opened.png
adb shell input keyevent 4; sleep 3; adb exec-out screencap -p > shots/30c_after_back.png
tapt "عن المركز"; sleep 2; adb exec-out screencap -p > shots/30d_about_opened.png
adb shell input keyevent 4; sleep 3; adb exec-out screencap -p > shots/30e_after_back_about.png
tapt "الأدوات"; adb exec-out screencap -p > shots/30f_tools_tab.png
tapt "الإدارة"; tapt "الميزانية"; adb shell input keyevent 4; sleep 3; adb exec-out screencap -p > shots/30g_tools_then_admin_row_back.png
adb shell wm size 720x1280; adb shell wm density 320; sleep 2
st 27_home_360dp_salaam default --es demo_login teacher --es route home
adb shell wm size reset; adb shell wm density reset; sleep 2
st 99_back_default default --es demo_login gate
ls -la shots
exit 0
