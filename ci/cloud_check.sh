#!/bin/bash
# v3 end-to-end check of the APK against the REAL central database, with throwaway passwords and rows.
# Runs only while no admin password exists yet (before the owner's own first-time setup).
# Everything it creates is removed by the maintainer right after the run.
set -x
APK="$1"
PKG=com.alsiddiq.app.debug
ACT=com.alsiddiq.app.MainActivity
U="$SUPABASE_URL"; K="$SUPABASE_ANON_KEY"
AP=A$(openssl rand -hex 6); VP=V$(openssl rand -hex 6); TA=T$(openssl rand -hex 6); TB=B$(openssl rand -hex 6)
for p in "$AP" "$VP" "$TA" "$TB"; do echo "::add-mask::$p"; done
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
rest() { curl -s -X POST "$U/rest/v1/$1" -H "apikey: $K" -H "Authorization: Bearer $2" -H "Content-Type: application/json" -H "Prefer: return=minimal" -d "$3" -w " HTTP %{http_code}\n"; }
get() { curl -s "$U/rest/v1/$1" -H "apikey: $K" -H "Authorization: Bearer $2"; echo; }
jget() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)"; }
uid() { python3 -c 'import uuid;print(uuid.uuid4())'; }
shot() { nodlg; sleep 1; adb exec-out screencap -p > "shots/$1.png"; }
app() { adb shell am force-stop $PKG; adb shell am start -n $PKG/$ACT "$@"; }

ST=$(fn '{"action":"status"}'); echo "$ST"
case "$ST" in *'"needs_setup":true'*) ;; *) echo "An admin password already exists (owner set up) - skipping the live check."; exit 0;; esac

# 1) Fresh install -> the first-time setup screen (centre name + admin password + general password)
app; sleep 16; shot 20_cloud_setup
echo "== setup (server function, same call the setup screen makes)" | tee -a $LOG
SJ=$(fn "{\"action\":\"setup\",\"center_name\":\"مركز الصديق لتعليم القرآن الكريم\",\"admin_password\":\"$AP\",\"viewer_password\":\"$VP\"}")
AT=$(echo "$SJ" | jget 'd["session"]["access_token"]'); echo "::add-mask::$AT"
echo "setup role: $(echo "$SJ" | jget 'd["profile"]["access_kind"]')" | tee -a $LOG
echo "setup again: $(fn "{\"action\":\"setup\",\"admin_password\":\"$TA\",\"viewer_password\":\"$TB\"}")" | tee -a $LOG
CID=$(get "centers?select=id" "$AT" | jget 'd[0]["id"]')
C1=$(uid); C2=$(uid); S1=$(uid); S2=$(uid)
rest circles "$AT" "[{\"id\":\"$C1\",\"center_id\":\"$CID\",\"name\":\"TEST حلقة أ\"},{\"id\":\"$C2\",\"center_id\":\"$CID\",\"name\":\"TEST حلقة ب\"}]" | tee -a $LOG
rest students "$AT" "[{\"id\":\"$S1\",\"center_id\":\"$CID\",\"full_name\":\"TEST طالب أ\",\"circle_id\":\"$C1\"},{\"id\":\"$S2\",\"center_id\":\"$CID\",\"full_name\":\"TEST طالب ب\",\"circle_id\":\"$C2\"}]" | tee -a $LOG
fn "{\"action\":\"teacher_save\",\"name\":\"TEST معلم أ\",\"circle_id\":\"$C1\",\"password\":\"$TA\"}" "$AT" | tee -a $LOG
fn "{\"action\":\"teacher_save\",\"name\":\"TEST معلم ب\",\"circle_id\":\"$C2\",\"password\":\"$TB\"}" "$AT" | tee -a $LOG

# 2) The real password screen: type the general password with the keyboard and press Enter
app; sleep 14; nodlg; sleep 1; shot 21_cloud_password_screen
B=$(adb exec-out uiautomator dump /dev/tty 2>/dev/null | python3 -c '
import sys,re
x=sys.stdin.read(); m=re.search(r"class=\"android.widget.EditText\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"",x)
print("%d %d"%((int(m[1])+int(m[3]))//2,(int(m[2])+int(m[4]))//2) if m else "")')
echo "password field at: $B" | tee -a $LOG
if [ -n "$B" ]; then adb shell input tap $B; sleep 1; adb shell input text "$VP"; sleep 1; adb shell input keyevent 66; sleep 12; fi
shot 22_cloud_viewer_home_after_typing
adb exec-out uiautomator dump /dev/tty 2>/dev/null | grep -o 'وضع العرض[^"]*' | head -1 | sed 's/^/viewer screen text: /' | tee -a $LOG

# 3) Viewer: the app's own writes are not offered; raw writes straight to the server are refused
app --es login_kind viewer --es login_pass "$VP" --ez selftest true --es try_student "$S1:$C1"; sleep 24
app --es login_kind viewer --es login_pass "$VP" --es route viewer_settings; sleep 12; shot 23_cloud_viewer_settings
# 4) Teacher A: writes for his own student; raw writes on teacher B's student are refused
app --es login_kind teacher --es login_pass "$TA" --es viewer_pass "$VP" --ez selftest true --es try_student "$S2:$C2"; sleep 26; shot 24_cloud_teacher_a_home
# 5) Teacher B: raw writes on teacher A's student are refused
app --es login_kind teacher --es login_pass "$TB" --es viewer_pass "$VP" --ez selftest true --es try_student "$S1:$C1"; sleep 26
# 6) Admin: may write anywhere
app --es login_kind admin --es login_pass "$AP" --es viewer_pass "$VP" --ez selftest true --es try_student "$S2:$C2"; sleep 26
app --es login_kind admin --es login_pass "$AP" --es viewer_pass "$VP" --es route more; sleep 12; shot 25_cloud_admin_panel
# 7) Wrong password in the teachers box
echo "teacher box with the admin password: $(fn "{\"action\":\"login\",\"kind\":\"teacher\",\"password\":\"$AP\"}")" | tee -a $LOG

adb logcat -d | grep -E "ALSIDDIQ_SELFTEST|AndroidRuntime" | tee -a $LOG
# 8) What the server holds now (read as the admin)
echo "server recitations:" | tee -a $LOG; get "recitations?select=student_id,notes,teacher_id" "$AT" | tee -a $LOG
echo "server attendance:" | tee -a $LOG; get "attendance?select=student_id,day,status,recorded_by" "$AT" | tee -a $LOG
echo "server students:" | tee -a $LOG; get "students?select=id,full_name,notes,circle_id" "$AT" | tee -a $LOG
echo "ids: S1(teacher A)=$S1 S2(teacher B)=$S2 C1=$C1 C2=$C2" | tee -a $LOG
