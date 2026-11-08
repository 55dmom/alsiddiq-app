#!/bin/bash
# Installs the demo APK on the emulator and captures the v3 screens (sample data on the phone only).
set -x
APK="$1"
PKG=com.alsiddiq.app.debug
ACT=com.alsiddiq.app.MainActivity
mkdir -p shots
adb wait-for-device
adb shell settings put global window_animation_scale 0
adb install -r "$APK" || exit 1
adb shell settings put global hide_error_dialogs 1 || true
adb shell settings put secure anr_show_background 0 || true
nodlg() { adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1; }

launch() { # role route
  adb shell am force-stop $PKG
  if [ -z "$1" ]; then adb shell am start -n $PKG/$ACT; else adb shell am start -n $PKG/$ACT --es demo_login "$1" --es route "$2"; fi
}
shot() { # name role route wait
  launch "$2" "$3"
  sleep "${4:-10}"
  nodlg; sleep 1
  adb exec-out screencap -p > "shots/$1.png"
  adb logcat -d -s AndroidRuntime:E | tail -40 > "shots/$1.crash.txt"
  [ -s "shots/$1.crash.txt" ] || rm -f "shots/$1.crash.txt"
  adb logcat -c
}

adb logcat -c
shot 00_password gate "" 14
adb shell am force-stop $PKG; adb shell am start -n $PKG/$ACT; sleep 0.8; adb exec-out screencap -p > shots/00b_splash.png; sleep 8
shot 01_viewer_home viewer home 14
shot 02_viewer_settings viewer viewer_settings
shot 03_role_login_admin viewer role_login/ADMIN
shot 04_role_login_teacher viewer role_login/TEACHER
shot 05_viewer_students viewer students
shot 06_viewer_student viewer student/s1
shot 07_admin_panel admin more
shot 08_admin_home admin home
shot 09_admin_teachers admin teachers
shot 11_admin_passwords admin passwords
shot 11b_admin_circle_edit admin circle_edit/c1
shot 12_teacher_home teacher home
shot 13_teacher_circle teacher circle/c1
shot 13b_teacher_circle_other teacher circle/c3
shot 13c_teacher_students teacher students
shot 14_teacher_record teacher record/s1
shot 15_teacher_attendance teacher attendance
shot 16_teacher_report teacher circle_report/c1
shot 17_teacher_more teacher teacher_more
# His side buttons («الأدوات»): the menu on the opening screen, then each tool
adb shell am force-stop $PKG; adb shell am start -n $PKG/$ACT --es demo_login gate --es open_tool menu; sleep 12; nodlg; sleep 1; adb exec-out screencap -p > shots/18_tools_menu.png
adb shell am force-stop $PKG; adb shell am start -n $PKG/$ACT --es open_tool mushaf; sleep 10; nodlg; sleep 1; adb exec-out screencap -p > shots/18a_tool_mushaf_index.png
adb shell am force-stop $PKG; adb shell am start -n $PKG/$ACT --es open_tool mushaf_page --es page 1; sleep 10; nodlg; sleep 1; adb exec-out screencap -p > shots/18a2_tool_mushaf_page1.png
adb shell am force-stop $PKG; adb shell am start -n $PKG/$ACT --es open_tool mushaf_page --es page 50; sleep 10; nodlg; sleep 1; adb exec-out screencap -p > shots/18a3_tool_mushaf_page50.png
adb shell am force-stop $PKG; adb shell am start -n $PKG/$ACT --es open_tool hifz; sleep 10; nodlg; sleep 1; adb exec-out screencap -p > shots/18a4_tool_hifz.png
adb shell am force-stop $PKG; adb shell am start -n $PKG/$ACT --es open_tool khatma; sleep 10; nodlg; sleep 1; adb exec-out screencap -p > shots/18a5_tool_khatma.png
adb shell am force-stop $PKG; adb shell am start -n $PKG/$ACT --es open_tool prayer; sleep 10; nodlg; sleep 1; adb exec-out screencap -p > shots/18b_tool_prayer.png
adb shell input swipe 540 1700 540 700 400; sleep 1.5; adb exec-out screencap -p > shots/18c_tool_prayer_scrolled.png
adb shell am force-stop $PKG; adb shell am start -n $PKG/$ACT --es open_tool tasbeeh; sleep 10; nodlg; sleep 1; adb exec-out screencap -p > shots/19_tool_tasbeeh.png
adb shell input tap 540 1000; sleep 0.5; adb shell input tap 540 1000; sleep 0.5; adb shell input tap 540 1000; sleep 1; adb exec-out screencap -p > shots/19b_tool_tasbeeh_count.png
adb shell am force-stop $PKG; adb shell am start -n $PKG/$ACT --es open_tool asma; sleep 10; nodlg; sleep 1; adb exec-out screencap -p > shots/19c_tool_asma.png
adb shell am force-stop $PKG; adb shell am start -n $PKG/$ACT --es open_tool adhkar; sleep 10; nodlg; sleep 1; adb exec-out screencap -p > shots/19d_tool_adhkar.png
adb shell am force-stop $PKG; adb shell am start -n $PKG/$ACT --es open_tool duas; sleep 10; nodlg; sleep 1; adb exec-out screencap -p > shots/19e_tool_duas.png
adb logcat -d -s AndroidRuntime:E | tail -40 > shots/19_tools.crash.txt; [ -s shots/19_tools.crash.txt ] || rm -f shots/19_tools.crash.txt
shot 01b_viewer_home_tools viewer home 14
adb shell input swipe 540 1500 540 700 300; sleep 1; adb exec-out screencap -p > shots/01c_viewer_home_scrolled.png
# «الثيمات»: the picker (open to everyone, before any password), then themes applied across the app and a tool
adb logcat -c
st() { # name theme extra-args...
  local n="$1" th="$2"; shift 2
  adb shell am force-stop $PKG; adb shell am start -n $PKG/$ACT --es theme "$th" "$@"; sleep 12; nodlg; sleep 1
  adb exec-out screencap -p > "shots/$n.png"
}
st 20_themes_picker default --es demo_login gate --ez open_themes true
st 21_theme_emerald_gate emerald --es demo_login gate
st 21b_theme_emerald_home emerald --es demo_login viewer --es route home
st 22_theme_night_gate night --es demo_login gate
st 22b_theme_night_home night --es demo_login viewer --es route home
st 22c_theme_night_settings night --es demo_login viewer --es route viewer_settings
st 22d_theme_night_tools night --es demo_login gate --es open_tool menu
st 22e_theme_night_prayer night --es open_tool prayer
st 22f_theme_night_mushaf night --es open_tool mushaf_page --es page 3
st 23_theme_sand_home sand --es demo_login viewer --es route home
st 23b_theme_sand_tasbeeh sand --es open_tool tasbeeh
st 24_theme_royal_gate royal --es demo_login gate
st 24b_theme_royal_admin royal --es demo_login admin --es route more
adb logcat -d -s AndroidRuntime:E | tail -40 > shots/20_themes.crash.txt; [ -s shots/20_themes.crash.txt ] || rm -f shots/20_themes.crash.txt
# Small phone (360x640 dp) overflow check, original theme
adb shell wm size 720x1280; adb shell wm density 320; sleep 2
st 25_small_gate default --es demo_login gate
st 25b_small_tools default --es demo_login gate --es open_tool menu
st 25c_small_home default --es demo_login viewer --es route home
st 25d_small_settings default --es demo_login viewer --es route viewer_settings
st 25e_small_themes default --es demo_login gate --ez open_themes true
adb shell wm size reset; adb shell wm density reset
# «ثيمات المناسبات» with a simulated date (debug build only): popup on open, small card on home, picker locked, a tool in its colours
adb logcat -c
oc() { # name sim_date extra-args...
  local n="$1" d="$2"; shift 2
  adb shell am force-stop $PKG; adb shell am start -n $PKG/$ACT --es sim_date "$d" "$@"; sleep 12
  adb exec-out screencap -p > "shots/$n.png"
}
oc 30_occ_ramadan_popup 2027-02-20 --es demo_login gate; adb shell input keyevent 4; sleep 1
oc 30b_occ_ramadan_home 2027-02-20 --es demo_login viewer --es route home
oc 30c_occ_ramadan_picker 2027-02-20 --es demo_login gate --ez open_themes true
oc 30d_occ_ramadan_prayer 2027-02-20 --es open_tool prayer
oc 31_occ_eidfitr_popup h:10-1 --es demo_login gate; adb shell input keyevent 4; sleep 1
oc 31b_occ_eidfitr_home h:10-1 --es demo_login viewer --es route home
oc 32_occ_arafah_popup h:12-9 --es demo_login gate; adb shell input keyevent 4; sleep 1
oc 32b_occ_arafah_home h:12-9 --es demo_login viewer --es route home
oc 33_occ_sep26_popup 2026-09-26 --es demo_login gate; adb shell input keyevent 4; sleep 1
oc 33b_occ_sep26_home 2026-09-26 --es demo_login viewer --es route home
oc 34_occ_none_after 2026-11-08 --es demo_login viewer --es route home
adb logcat -d -s AndroidRuntime:E | tail -40 > shots/30_occasions.crash.txt; [ -s shots/30_occasions.crash.txt ] || rm -f shots/30_occasions.crash.txt
ls -la shots
