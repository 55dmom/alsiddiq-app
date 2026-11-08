#!/bin/bash
# v3.1 end-to-end check of the APK against the REAL central database, with throwaway passwords and rows.
# Runs only while no admin password exists yet (before the owner's own first-time setup).
# Everything it creates is removed by the maintainer right after the run.
set -x
APK="$1"
PKG=com.alsiddiq.app.debug
ACT=com.alsiddiq.app.MainActivity
U="$SUPABASE_URL"; K="$SUPABASE_ANON_KEY"
AP=A$(openssl rand -hex 6); VP=V$(openssl rand -hex 6); TP=T$(openssl rand -hex 6); TP2=N$(openssl rand -hex 6)
for p in "$AP" "$VP" "$TP" "$TP2"; do echo "::add-mask::$p"; done
mkdir -p shots
LOG=shots/cloud_selftest.txt
adb uninstall $PKG >/dev/null 2>&1
adb install -r "$APK" || exit 1
adb shell settings put global hide_error_dialogs 1 || true
adb shell settings put secure anr_show_background 0 || true
nodlg() { adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1; }
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb logcat -c

fn() { curl -s -X POST "$U/functions/v1/access" -H "apikey: $K" -H "Authorization: Bearer ${2:-$K}" -H "Content-Type: application/json" -d "$1"; echo; }
fncode() { curl -s -o /dev/null -w "%{http_code}" -X POST "$U/functions/v1/access" -H "apikey: $K" -H "Authorization: Bearer ${2:-$K}" -H "Content-Type: application/json" -d "$1"; }
rest() { curl -s -X POST "$U/rest/v1/$1" -H "apikey: $K" -H "Authorization: Bearer $2" -H "Content-Type: application/json" -H "Prefer: return=minimal" -d "$3" -w " HTTP %{http_code}\n"; }
get() { curl -s "$U/rest/v1/$1" -H "apikey: $K" -H "Authorization: Bearer $2"; echo; }
jget() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)"; }
uid() { python3 -c 'import uuid;print(uuid.uuid4())'; }
shot() { nodlg; sleep 1; adb exec-out screencap -p > "shots/$1.png"; }
app() { adb shell am force-stop $PKG; adb shell am start -n $PKG/$ACT "$@"; }
fields() { adb exec-out uiautomator dump /dev/tty 2>/dev/null | python3 -c '
import sys,re
x=sys.stdin.read()
for m in re.finditer(r"class=\"android.widget.EditText\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"",x):
    print("%d %d"%((int(m[1])+int(m[3]))//2,(int(m[2])+int(m[4]))//2))'; }
screen_has() { adb exec-out uiautomator dump /dev/tty 2>/dev/null | grep -c "$1"; }
type_into_first() { # password : tap the first EditText, type, Enter
  local B; B=$(fields | head -1); echo "field at: $B" | tee -a $LOG
  if [ -n "$B" ]; then adb shell input tap $B; sleep 1; adb shell input text "$1"; sleep 1; adb shell input keyevent 66; fi
}

ST=$(fn '{"action":"status"}'); echo "$ST"
case "$ST" in *'"needs_setup":true'*) ;; *) echo "An admin password already exists (owner set up) - skipping the live check."; exit 0;; esac

# 1) Fresh install -> the first-time setup screen; type the three passwords on the real screen (Tab moves to the next field)
app; sleep 16; shot 20_cloud_setup
# Fill the 6 password fields by tapping each one (visible fields first, then scroll for the teachers' pair).
python3 - "$AP" "$VP" "$TP" <<'PY' | tee -a $LOG
import re, subprocess, sys, time
AP, VP, TP = sys.argv[1:4]
def sh(c): return subprocess.run(c, shell=True, capture_output=True, text=True).stdout
def dump():
    x = sh("adb exec-out uiautomator dump /dev/tty 2>/dev/null")
    out = []
    for m in re.finditer(r'<node [^>]*class="android.widget.EditText"[^>]*>', x):
        n = m.group(0)
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        out.append(((int(b[1]) + int(b[3])) // 2, (int(b[2]) + int(b[4])) // 2))
    return x, out
def typ(pt, v):
    sh(f"adb shell input tap {pt[0]} {pt[1]}"); time.sleep(0.8); sh(f"adb shell input text {v}"); time.sleep(0.6)
x, f = dump(); print("setup fields visible:", len(f))
vals = [AP, AP, VP, VP]
for pt, v in zip(f[1:5], vals): typ(pt, v)
sh("adb shell input keyevent 4"); time.sleep(1)          # hide the keyboard
sh("adb shell input swipe 540 1700 540 500 400"); time.sleep(1.5)
x, f = dump(); print("after scroll fields:", len(f))
for pt, v in zip(f[-2:], [TP, TP]): typ(pt, v)
sh("adb shell input keyevent 4"); time.sleep(1)
sh("adb shell input swipe 540 1700 540 500 400"); time.sleep(1.5)
sh("adb exec-out screencap -p > shots/20b_cloud_setup_filled.png")
x, _ = dump()
m = re.search(r'text="حفظ وبدء الاستخدام"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
if not m:
    m2 = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*>\s*<node[^>]*text="حفظ وبدء الاستخدام"', x)
    m = m2
if m:
    sh(f"adb shell input tap {(int(m[1]) + int(m[3])) // 2} {(int(m[2]) + int(m[4])) // 2}"); print("pressed save")
else:
    print("save button not found in dump")
PY
sleep 14; shot 20c_cloud_after_setup
ST2=$(fn '{"action":"status"}'); echo "status after typing setup: $ST2" | tee -a $LOG
case "$ST2" in *'"needs_setup":false'*) echo "SETUP THROUGH THE REAL SCREEN: ok" | tee -a $LOG ;;
  *) echo "setup screen typing did not complete; using the same server call the form makes" | tee -a $LOG
     fn "{\"action\":\"setup\",\"center_name\":\"مركز الصديق لتعليم القرآن الكريم\",\"admin_password\":\"$AP\",\"viewer_password\":\"$VP\",\"teachers_password\":\"$TP\"}" > /dev/null ;; esac
SJ=$(fn "{\"action\":\"login\",\"kind\":\"admin\",\"password\":\"$AP\"}")
AT=$(echo "$SJ" | jget 'd["session"]["access_token"]'); echo "::add-mask::$AT"
echo "admin login: $(echo "$SJ" | jget 'd["profile"]["access_kind"]')  has_teachers_password: $(fn '{"action":"status"}' | jget 'd["has_teachers_password"]')" | tee -a $LOG
CID=$(get "centers?select=id" "$AT" | jget 'd[0]["id"]')
C1=$(uid); C2=$(uid); S1=$(uid); S2=$(uid)
rest circles "$AT" "[{\"id\":\"$C1\",\"center_id\":\"$CID\",\"name\":\"TEST حلقة أ\",\"teacher_name\":\"TEST الشيخ أحمد\"},{\"id\":\"$C2\",\"center_id\":\"$CID\",\"name\":\"TEST حلقة ب\",\"teacher_name\":\"TEST الأستاذ خالد\"}]" | tee -a $LOG
rest students "$AT" "[{\"id\":\"$S1\",\"center_id\":\"$CID\",\"full_name\":\"TEST طالب أ\",\"circle_id\":\"$C1\"},{\"id\":\"$S2\",\"center_id\":\"$CID\",\"full_name\":\"TEST طالب ب\",\"circle_id\":\"$C2\"}]" | tee -a $LOG
rest teacher_names "$AT" "[{\"center_id\":\"$CID\",\"name\":\"TEST الشيخ أحمد\"},{\"center_id\":\"$CID\",\"name\":\"TEST الأستاذ خالد\"}]" | tee -a $LOG

# 2) The real password screen: type the general password
app; sleep 14; nodlg; sleep 1; shot 21_cloud_password_screen
type_into_first "$VP"; sleep 12
shot 22_cloud_viewer_home_after_typing
echo "viewer screen shows read-only banner: $(screen_has 'وضع العرض')" | tee -a $LOG

# 3) Settings -> «المعلمين»: type the shared teachers' password on the real screen
app --es login_kind viewer --es login_pass "$VP" --es route role_login/TEACHER; sleep 14; shot 23_cloud_teachers_box
type_into_first "$TP"; sleep 14
adb shell input keyevent 4; sleep 3   # the CI hook re-opens this route inside the new section; step back to its home
shot 24_cloud_teachers_home_after_typing
echo "teachers section after typing the password on the real screen: $(screen_has 'قسم المعلمين')" | tee -a $LOG

# 4) Viewer: app writes not offered; raw writes refused; viewer photo write refused
app --es login_kind viewer --es login_pass "$VP" --ez selftest true --es try_student "$S1:$C1"; sleep 26
# 5) Shared teachers: app writes for a student in EACH circle + photo through the app; raw probes on circle B's student
app --es login_kind teacher --es login_pass "$TP" --es viewer_pass "$VP" --ez selftest true --ez photo_test true --es try_student "$S2:$C2"; sleep 32
app --es login_kind teacher --es login_pass "$TP" --es viewer_pass "$VP" --es route circles; sleep 14; shot 25_cloud_teachers_all_circles
app --es login_kind teacher --es login_pass "$TP" --es viewer_pass "$VP" --es route "student/$S1"; sleep 16; shot 26_cloud_student_photo_teacher
app --es login_kind teacher --es login_pass "$TP" --es viewer_pass "$VP" --es route "circle_report/$C2"; sleep 14; shot 27_cloud_today_report
# 6) Viewer sees the photo (read-only) + raw read of the photo
app --es login_kind viewer --es login_pass "$VP" --ez selftest true; sleep 22
app --es login_kind viewer --es login_pass "$VP" --es route "student/$S1"; sleep 16; shot 28_cloud_student_photo_viewer
app --es login_kind viewer --es login_pass "$VP" --es route students; sleep 14; shot 29_cloud_viewer_students
# 7) Admin: writes anywhere; passwords screen
app --es login_kind admin --es login_pass "$AP" --es viewer_pass "$VP" --ez selftest true --es try_student "$S2:$C2"; sleep 26
app --es login_kind admin --es login_pass "$AP" --es viewer_pass "$VP" --es route passwords; sleep 12; shot 30_cloud_admin_passwords
app --es login_kind admin --es login_pass "$AP" --es viewer_pass "$VP" --es route teachers; sleep 12; shot 31_cloud_admin_teacher_names
# 8) Admin changes the teachers' password: the old one stops working at once
app --es login_kind teacher --es login_pass "$TP" --es viewer_pass "$VP" --es route home; sleep 14; shot 32_cloud_teachers_before_change
echo "set teachers password (admin): $(fn "{\"action\":\"set_password\",\"target\":\"teachers\",\"password\":\"$TP2\"}" "$AT")" | tee -a $LOG
echo "old teachers password -> HTTP $(fncode "{\"action\":\"login\",\"kind\":\"teacher\",\"password\":\"$TP\"}")" | tee -a $LOG
echo "new teachers password -> HTTP $(fncode "{\"action\":\"login\",\"kind\":\"teacher\",\"password\":\"$TP2\"}")" | tee -a $LOG
adb shell am force-stop $PKG; adb shell am start -n $PKG/$ACT; sleep 25; shot 33_cloud_old_session_after_change
echo "after change, app still in teachers section: $(screen_has 'قسم المعلمين')" | tee -a $LOG
# 9) Wrong kinds
echo "teachers box with the admin password: HTTP $(fncode "{\"action\":\"login\",\"kind\":\"teacher\",\"password\":\"$AP\"}")" | tee -a $LOG
echo "admin box with the teachers password: HTTP $(fncode "{\"action\":\"login\",\"kind\":\"admin\",\"password\":\"$TP2\"}")" | tee -a $LOG

adb logcat -d | grep -E "ALSIDDIQ_SELFTEST|AndroidRuntime" | tee -a $LOG
# 10) What the server holds now (read as the admin)
echo "server recitations:" | tee -a $LOG; get "recitations?select=student_id,notes,teacher_id,by_name" "$AT" | tee -a $LOG
echo "server attendance:" | tee -a $LOG; get "attendance?select=student_id,day,status,recorded_by,by_name" "$AT" | tee -a $LOG
echo "server students:" | tee -a $LOG; get "students?select=id,full_name,notes,circle_id,photo_path" "$AT" | tee -a $LOG
echo "ids: S1(circle A)=$S1 S2(circle B)=$S2 C1=$C1 C2=$C2" | tee -a $LOG
