#!/bin/bash
# Installs the APK on the emulator and captures the main screens of every role (demo data).
set -x
APK="$1"
PKG=com.alsiddiq.app.debug
ACT=com.alsiddiq.app.MainActivity
mkdir -p shots
adb wait-for-device
adb shell settings put global window_animation_scale 0
adb install -r "$APK" || exit 1

launch() { # role route
  adb shell am force-stop $PKG
  if [ -z "$1" ]; then adb shell am start -n $PKG/$ACT; else adb shell am start -n $PKG/$ACT --es demo_login "$1" --es route "$2"; fi
}
shot() { # name role route wait
  launch "$2" "$3"
  sleep "${4:-7}"
  adb exec-out screencap -p > "shots/$1.png"
  adb logcat -d -s AndroidRuntime:E | tail -40 > "shots/$1.crash.txt"
  [ -s "shots/$1.crash.txt" ] || rm -f "shots/$1.crash.txt"
  adb logcat -c
}

adb logcat -c
shot 00_login "" "" 9
shot 01_admin_home admin home 9
shot 02_admin_circles admin circles
shot 03_admin_students admin students
shot 04_admin_reports admin reports
shot 05_admin_more admin more
shot 06_teacher_home teacher home
shot 07_teacher_record teacher record/s1
shot 08_teacher_attendance teacher attendance
shot 09_circle_detail teacher circle/c1
shot 10_student_home student home
shot 11_student_rewards student rewards
shot 12_parent_home parent home
shot 13_student_profile parent student/s1
shot 14_parent_notifications parent notifications
shot 15_admin_add_student admin student_edit/new
ls -la shots
