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
st 99_back_default default --es demo_login gate
ls -la shots
exit 0
