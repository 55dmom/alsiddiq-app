#!/bin/bash
# End-to-end check of the APK against the REAL central database, with throwaway accounts.
# Accounts and rows it creates are removed by the maintainer right after the run.
set -x
APK="$1"
PKG=com.alsiddiq.app.debug
ACT=com.alsiddiq.app.MainActivity
U="$SUPABASE_URL"; K="$SUPABASE_ANON_KEY"
PW=$(openssl rand -hex 8); echo "::add-mask::$PW"
mkdir -p shots
adb uninstall $PKG >/dev/null 2>&1
adb install -r "$APK" || exit 1
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb logcat -c

# 1) Fresh install against an empty database -> first-time setup screen
adb shell am start -n $PKG/$ACT; sleep 16
adb exec-out screencap -p > shots/20_cloud_first_setup.png

fn() { curl -s -X POST "$U/functions/v1/admin-members" -H "apikey: $K" -H "Authorization: Bearer ${2:-$K}" -H "Content-Type: application/json" -d "$1"; echo; }
tok() { curl -s -X POST "$U/auth/v1/token?grant_type=password" -H "apikey: $K" -H "Content-Type: application/json" -d "{\"email\":\"$1@alsiddiq.app\",\"password\":\"$PW\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("access_token",""))'; }
rest() { curl -s -X POST "$U/rest/v1/$1" -H "apikey: $K" -H "Authorization: Bearer $2" -H "Content-Type: application/json" -H "Prefer: return=minimal" -d "$3" -w " HTTP %{http_code}\n"; }

ST=$(fn '{"action":"status"}'); echo "$ST"
case "$ST" in *'"needs_setup":true'*) ;; *) echo "Database already set up by the owner - skipping the live check (it only runs on an empty database)."; exit 0;; esac
fn "{\"action\":\"bootstrap\",\"phone\":\"0599999901\",\"password\":\"$PW\",\"full_name\":\"CI ADMIN\"}"
AT=$(tok 0599999901); echo "::add-mask::$AT"
TJSON=$(fn "{\"action\":\"create\",\"full_name\":\"CI TEACHER\",\"phone\":\"0599999902\",\"role\":\"teacher\",\"password\":\"$PW\"}" "$AT")
TID=$(echo "$TJSON" | python3 -c 'import sys,json;print(json.load(sys.stdin)["profile"]["id"])')
CID=$(curl -s "$U/rest/v1/centers?select=id" -H "apikey: $K" -H "Authorization: Bearer $AT" | python3 -c 'import sys,json;print(json.load(sys.stdin)[0]["id"])')
C1=$(python3 -c 'import uuid;print(uuid.uuid4())'); C2=$(python3 -c 'import uuid;print(uuid.uuid4())')
S1=$(python3 -c 'import uuid;print(uuid.uuid4())'); S2=$(python3 -c 'import uuid;print(uuid.uuid4())')
rest circles "$AT" "[{\"id\":\"$C1\",\"center_id\":\"$CID\",\"name\":\"حلقة الاختبار\",\"teacher_id\":\"$TID\"},{\"id\":\"$C2\",\"center_id\":\"$CID\",\"name\":\"حلقة أخرى\",\"teacher_id\":null}]"
rest students "$AT" "[{\"id\":\"$S1\",\"center_id\":\"$CID\",\"full_name\":\"طالب الاختبار\",\"circle_id\":\"$C1\"},{\"id\":\"$S2\",\"center_id\":\"$CID\",\"full_name\":\"طالب حلقة أخرى\",\"circle_id\":\"$C2\"}]"
fn "{\"action\":\"create\",\"full_name\":\"CI PARENT\",\"phone\":\"0599999903\",\"role\":\"parent\",\"password\":\"$PW\",\"child_ids\":[\"$S1\"]}" "$AT"

# 2) The app signs in as the teacher, records hifz + an absence through its own repository, reads back
adb shell am force-stop $PKG
adb shell am start -n $PKG/$ACT --es login_phone 0599999902 --es login_pass "$PW" --ez selftest true
sleep 30
adb exec-out screencap -p > shots/21_cloud_teacher_home.png
adb shell am start -n $PKG/$ACT --es login_phone 0599999902 --es login_pass "$PW" --es route "circle/$C1"
sleep 14
adb exec-out screencap -p > shots/22_cloud_circle.png

# 3) Parent signs in on the same phone: sees the child, the new record and the absence notification
adb shell am force-stop $PKG
adb shell am start -n $PKG/$ACT --es login_phone 0599999903 --es login_pass "$PW" --es route notifications
sleep 20
adb exec-out screencap -p > shots/23_cloud_parent_notifications.png

adb logcat -d | grep -E "ALSIDDIQ_SELFTEST|AndroidRuntime" | tee shots/cloud_selftest.txt
# 4) What the server holds now (as the admin)
echo "server recitations:" | tee -a shots/cloud_selftest.txt
curl -s "$U/rest/v1/recitations?select=student_id,kind,from_surah,from_ayah,to_ayah,grade,notes" -H "apikey: $K" -H "Authorization: Bearer $AT" | tee -a shots/cloud_selftest.txt; echo
echo "server attendance:" | tee -a shots/cloud_selftest.txt
curl -s "$U/rest/v1/attendance?select=student_id,day,status" -H "apikey: $K" -H "Authorization: Bearer $AT" | tee -a shots/cloud_selftest.txt; echo
PT=$(tok 0599999903); echo "::add-mask::$PT"
echo "parent notifications:" | tee -a shots/cloud_selftest.txt
curl -s "$U/rest/v1/notifications?select=title,body" -H "apikey: $K" -H "Authorization: Bearer $PT" | tee -a shots/cloud_selftest.txt; echo
echo "parent sees students:" | tee -a shots/cloud_selftest.txt
curl -s "$U/rest/v1/students?select=full_name" -H "apikey: $K" -H "Authorization: Bearer $PT" | tee -a shots/cloud_selftest.txt; echo
