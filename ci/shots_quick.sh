#!/bin/bash
# Short screenshot pass (build 13: the recording modes; build 12: teachers' sheet with حاضر / غائب / مستأذن and حضور / انصراف; «تواصل مع المركز»).
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
  for i in 1 2 3; do
  timeout -k 3 20 adb exec-out uiautomator dump /dev/tty 2>/dev/null | timeout -k 3 10 python3 -c '
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
# v3.9 (build 12): the teachers' sheet — three statuses per teacher and «حضور / انصراف» times (demo data, admin)
st 20_teacher_attendance default --es demo_login admin --es route teacher_attendance
adb shell input swipe 540 1600 540 1000 400; sleep 2; adb exec-out screencap -p > shots/20b_teacher_attendance_scrolled.png
tapt "حضور —"; sleep 2; adb exec-out screencap -p > shots/20c_time_picker_checkin.png
adb shell input keyevent 4; sleep 2
# the admin's switch «تواصل مع المركز» (default off), then the opening screen with the button
st 30_gate_contact_off default --es demo_login gate
st 31_admin_more default --es demo_login admin --es route more
tapt "تواصل مع المركز"; sleep 3; adb exec-out screencap -p > shots/31b_admin_switch_on.png
st 32_gate_contact_on default --es demo_login gate
st 33_gate_contact_on_360dp default --es demo_login gate
adb shell wm size 720x1280; adb shell wm density 320; sleep 2
st 34_gate_contact_on_360dp_b default --es demo_login gate
adb shell wm size reset; adb shell wm density reset; sleep 2
# build 13 (v3.10): the recording layout — three modes, each with its own rating row (admin, demo data)
st 40_record_hifz default --es demo_login admin --es route record/s1
adb shell input swipe 540 1500 540 700 400; sleep 1.5; adb exec-out screencap -p > shots/40b_record_hifz_grades.png
adb shell input swipe 540 700 540 1500 400; sleep 1
tapt "مراجعة"; sleep 2; adb exec-out screencap -p > shots/41_record_muraja.png
adb shell input swipe 540 1500 540 700 400; sleep 1.5; adb exec-out screencap -p > shots/41b_record_muraja_grades.png
adb shell input swipe 540 700 540 1500 400; sleep 1
tapt "تسميع"; sleep 2; adb exec-out screencap -p > shots/42_record_tasmee.png
adb shell input swipe 540 1500 540 700 400; adb shell input swipe 540 1500 540 700 400; sleep 1.5; adb exec-out screencap -p > shots/42b_record_tasmee_bottom_row.png
# build 14 (v3.11): the fourth recording type «أظافر لم يسمع» with its own rating row
adb shell input swipe 540 700 540 1500 400; sleep 1
tapt "أظافر لم يسمع"; sleep 2; adb exec-out screencap -p > shots/43_record_azfar.png
adb shell input swipe 540 1500 540 700 400; sleep 1.5; adb exec-out screencap -p > shots/43b_record_azfar_grades.png
# build 15 (v3.12): «سرد السورة» in the recording row (in place of «مراجعة»), the «مراجعة» field (من / إلى or لم يراجع),
# and center reports for the admin and the teacher only (the general password gets the locked screen).
st 50_reports_admin default --es demo_login admin --es route reports
st 51_reports_teacher default --es demo_login teacher --es route reports
st 52_reports_viewer default --es demo_login viewer --es route reports
st 53_record_sard default --es demo_login admin --es route record/s1
tapt "سرد السورة"; sleep 2; adb exec-out screencap -p > shots/53b_record_sard_selected.png
adb shell input swipe 540 1500 540 900 400; sleep 1.5; adb exec-out screencap -p > shots/54_record_review_box.png
tapt "لم يراجع"; sleep 1.5; adb exec-out screencap -p > shots/55_record_review_not_reviewed.png
tapt "راجع من … إلى"; sleep 1.5; adb exec-out screencap -p > shots/56_record_review_range.png
# build 15: the admin tile in the settings with the shield icon (the key is gone)
st 57_settings_admin_shield default --es demo_login viewer --es route viewer_settings
adb logcat -d -s AndroidRuntime:E | tail -40 > shots/record_layout.crash.txt; [ -s shots/record_layout.crash.txt ] || rm -f shots/record_layout.crash.txt
ls -la shots
exit 0
