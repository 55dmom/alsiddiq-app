#!/bin/bash
# Build 17 screenshot pass: dismisses any Hijri occasion popup («شكراً») first, then shoots all 5 approved changes cleanly.
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
dismiss_occasion() {
  timeout -k 3 20 adb exec-out uiautomator dump /dev/tty 2>/dev/null | timeout -k 3 10 python3 -c '
import re,sys
x=sys.stdin.read()
for m in re.finditer(r"<node [^>]*>", x):
    n=m.group(0)
    if "text=\"شكراً\"" in n:
        b=re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", n)
        if b:
            a=list(map(int,b.groups())); print((a[0]+a[2])//2, (a[1]+a[3])//2); break
' > /tmp/occ_xy.txt
  if [ -s /tmp/occ_xy.txt ]; then
    adb shell input tap $(cat /tmp/occ_xy.txt)
    echo "dismissed occasion popup at $(cat /tmp/occ_xy.txt)"
    sleep 2
  fi
}
st() { # name theme extra-args...
  local n="$1" th="$2"; shift 2
  adb shell am force-stop $PKG; adb shell am start -n $PKG/$ACT --es theme "$th" "$@"; sleep 12; nodlg; dismiss_occasion; sleep 1
  adb exec-out screencap -p > "shots/$n.png"
  adb logcat -d -s AndroidRuntime:E | tail -40 > "shots/$n.crash.txt"; [ -s "shots/$n.crash.txt" ] || rm -f "shots/$n.crash.txt"
  adb logcat -c
}
tapt() { # tap the first on-screen element whose text/content-desc is exactly $1 (scrolls down up to 4 times)
  for i in 1 2 3 4; do
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

# 1) General / Parent view (العامة): Home + Open Reports tab (all past days + 500-point grades, read-only)
st 10_viewer_home default --es demo_login viewer
st 11_viewer_reports_days default --es demo_login viewer --es route reports
tapt "اليوم السابق"; sleep 2; adb exec-out screencap -p > shots/12_viewer_reports_prev_day.png
tapt "الدرجات"; sleep 2; adb exec-out screencap -p > shots/13_viewer_reports_grades.png

# 2) Student Detail (500-point Monthly Grades breakdown) & Student Edit (الهدف الشهري)
st 14_student_detail_grades default --es demo_login viewer --es route student/s1
st 15_student_edit_target default --es demo_login admin --es route student_edit/s1

# 3) Recording screen: independent error counters per section + separate مراجعة error counter & rating
st 20_record_hifz_errors default --es demo_login admin --es route record/s1
tapt "راجع من … إلى"; sleep 2
adb shell input swipe 540 1600 540 700 400; sleep 1.5; adb exec-out screencap -p > shots/21_record_review_errors.png
adb shell input swipe 540 1600 540 800 400; sleep 1.5; adb exec-out screencap -p > shots/21b_record_hifz_counter.png
st 22_record_sard default --es demo_login admin --es route record/s1
tapt "سرد السورة"; sleep 2
adb shell input swipe 540 1600 540 700 400; sleep 1.5; adb exec-out screencap -p > shots/22b_record_sard_counter.png

# 4) Teachers list (active only, no موقوف) & full teacher delete confirmation dialog
st 30_teachers_list default --es demo_login admin --es route teachers
tapt "مسح المعلم"; sleep 2; adb exec-out screencap -p > shots/31_teachers_delete_dialog.png

# 5) Tasbih (original Tafaul visual style intact, /33, no الدورات)
st 40_tools default --es demo_login viewer --es route tools
tapt "المسبحة"; sleep 3; adb exec-out screencap -p > shots/41_tasbeeh_tafaul.png

adb logcat -d -s AndroidRuntime:E | tail -40 > shots/build17.crash.txt; [ -s "shots/build17.crash.txt" ] || rm -f "shots/build17.crash.txt"
ls -la shots
exit 0
