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
shot 10_admin_teacher_edit admin teacher_edit/m-t1
shot 11_admin_passwords admin passwords
shot 12_teacher_home teacher home
shot 13_teacher_circle teacher circle/c1
shot 14_teacher_record teacher record/s1
shot 15_teacher_attendance teacher attendance
shot 16_teacher_report teacher circle_report/c1
shot 17_teacher_more teacher teacher_more
ls -la shots
