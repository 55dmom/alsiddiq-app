# v3.8 admin additions — server LIVE since 2026-11-08 ~22:10 Riyadh (for iOS to mirror)

Five things, all basic. Four server migrations, all additive (new tables / one new nullable column / new policies / two
helper functions / one RPC). Each was run inside BEGIN…ROLLBACK on the live DB first (test files + outputs in
files/alsiddiq_app/server_v33/: test_v38_rollback.sql + rollback_test_v38.txt, test_v38b_rollback.sql + rollback_test_v38b.txt,
test_v38c_rollback.sql + rollback_test_v38c.txt, test_v38d_rollback.sql + rollback_test_v38d.txt), then applied live. No data rows were written; live counts unchanged
(the 3 new tables have 0 rows; the existing tables' counts did not move with the migrations).
Repo: supabase/migrations/0015_v38_teacher_attendance.sql, 0016_v38_budget.sql, 0017_v38_circle_photo.sql, 0018_v38_subscriptions.sql.

## 1) «حضور وغياب المعلمين» (admin page «الإدارة») — his words 9:42 PM «خلي في صفحه الاداره حظور وغياب المعلمين»
Table `public.teacher_attendance`
| column | type | notes |
|---|---|---|
| id | uuid pk default gen_random_uuid() | |
| center_id | uuid not null → centers(id) | the admin's centre |
| teacher_profile_id | uuid not null → profiles(id) | a profile with role 'teacher' of the same centre |
| day | date not null | 'YYYY-MM-DD' (phone's local date) |
| status | text not null | check in ('present','absent') |
| marked_by | uuid default auth.uid() → profiles(id) | must equal the caller |
| created_at | timestamptz default now() | |
Constraint `teacher_attendance_teacher_day_key unique (teacher_profile_id, day)` → one mark per teacher per day.
Helper `public.is_my_center_teacher(pid uuid)`.
RLS (admin of the centre only; teachers, viewers, other centres, anon: denied — reads return 0 rows):
`ta_admin_read` (select), `ta_admin_insert`, `ta_admin_update`, `ta_admin_delete`.

Client (same as Android):
- Teachers listed = profiles of the centre with role 'teacher' and active (the admin's «المعلمون» list), sorted by name.
- Read a day / a month: `GET /rest/v1/teacher_attendance?select=teacher_profile_id,day,status&day=gte.<from>&day=lte.<to>&order=day.desc`
- Save a day (all teachers at once):
  `POST /rest/v1/teacher_attendance?on_conflict=teacher_profile_id,day` with `Prefer: resolution=merge-duplicates,return=minimal`,
  body = array of `{center_id, teacher_profile_id, day, status, marked_by: <admin profile id>}`. Changing a mark = the same upsert.
- Screen: date picker (default today, ‹ › to move a day), each teacher with two chips حاضر / غائب, «حفظ».
  Below: «السجل» (the last days that have marks: date + «حاضر X • غائب Y», tap opens that day) and
  «هذا الشهر» per teacher: «حاضر N • غائب M». Online only (no offline queue): saving needs internet.
- Not done on purpose (he said basic): no notes, no reasons, no late/excused, no notifications.

## 2) «الميزانية» (admin page «الإدارة») — his words 9:56 PM + 10:00 PM «وكمان ملاحظه عند اي عمليه صرف»
Table `public.budget_entries`
| column | type | notes |
|---|---|---|
| id | uuid pk default gen_random_uuid() | |
| center_id | uuid not null → centers(id) | |
| kind | text not null | check in ('income','expense') — دخل / صرف |
| amount | numeric(14,2) not null | check amount > 0, Yemeni rial |
| day | date not null default current_date | |
| student_id | uuid null → students(id) on delete set null | income: the paying student (must be of the centre) |
| note | text null | ≤ 500 chars. **REQUIRED for expense**: check `budget_entries_expense_note_chk` = kind <> 'expense' or btrim(note) <> '' |
| created_by | uuid default auth.uid() → profiles(id) | must equal the caller on insert |
| created_at | timestamptz default now() | |
Helper `public.is_my_center_student(sid uuid)` (null = ok).
RLS admin-only: `be_admin_read`, `be_admin_insert`, `be_admin_update`, `be_admin_delete`. Teachers / viewers / anon: denied
(and the app must not show the item to them at all).

Client (same as Android):
- Read: `GET /rest/v1/budget_entries?select=*&order=day.desc,created_at.desc`
- Add: `POST /rest/v1/budget_entries` `{center_id, kind, amount, day, student_id|null, note|null, created_by}`; edit: `PATCH ?id=eq.<id>`;
  delete: `DELETE ?id=eq.<id>` (with a confirm).
- Screen «الميزانية»: filter chips «الشهر» (current month, default) / «الكل»; summary cards «الدخل» (sum income), «الصرف»
  (sum expense), «المتبقي» = income − expense (red when negative). Two tabs «الدخل» / «الصرف», newest first.
  - Income form: student picker (active students), amount prefilled from the student's circle fee (`circles.fee_yer`, v3.5)
    and editable, date (default today), optional «ملاحظة». List row: student name • amount • date (+ note if any).
  - Expense form: amount, date, «ملاحظة» REQUIRED (save disabled until filled; the server refuses an empty one too).
    The note is shown under each expense in the list.
- Amounts shown like the fees: «٥٬٠٠٠ ريال يمني». Online only.
- Not done on purpose: no categories, charts, exports.

## 3) Circle photo = the circle's «لوحة الحلقة» — his words 10:02 PM «إمكانيه تسوي صوره للحلقه نفسها» / «خلي بدل هذا يقدر يعدل ويضع صورة لوحه الحلقة»
- New nullable column `public.circles.photo_path text` (null = the current look, no photo).
- Storage: the SAME private bucket as the student photos, `student-photos`, path
  `<center_id>/circles/<circle_id>/<uuid>.jpg` (JPEG, compressed like the student photos, ≤ 300 KB bucket limit).
  - Read: existing `sp_read` (any signed-in account of the centre). Download: `GET /storage/v1/object/authenticated/student-photos/<path>`.
  - Write: admin via the existing sp_* policies; the circle's own teacher via the new `cp_insert` / `cp_update` / `cp_delete`
    (folder[2] = 'circles' and teaches_circle(folder[3])). Upload: `POST /storage/v1/object/student-photos/<path>` with `x-upsert: true`.
- Set / remove: RPC `POST /rest/v1/rpc/set_circle_photo` `{"cid": "<circle id>", "path": "<path>" | null}` (SECURITY DEFINER):
  allowed for the admin (any circle of his centre) or the teacher of that circle; the path must start with
  `<my centre>/circles/<cid>/`; it also bumps `circles.updated_at`. After a change, delete the old object (Storage API).
  Teachers have no UPDATE on `circles`, so always use the RPC (admin too, for one code path).
- UI: circle card in lists shows the photo as a round thumbnail instead of the default icon when set; the circle page shows it
  as a WIDE BANNER at the top («لوحة الحلقة», 16:9-ish, rounded corners) with a small «تعديل اللوحة» button for whoever may edit. «صورة الحلقة»: اختيار من الصور / الكاميرا / حذف الصورة — admin on every circle, teacher on his own.
  Online only (needs internet).

## 4) «الاشتراكات الشهرية» / كشف الاشتراكات (monthly subscriptions sheet) — his words 10:03 PM «سوي كشف نفس هذا عند المدرسين الاشتراكات الشهرية واكتب سلم أو لم يسلم سلم مع المبلغ»
Table `public.student_subscriptions`
| column | type | notes |
|---|---|---|
| id | uuid pk default gen_random_uuid() | |
| center_id | uuid not null → centers(id) | |
| student_id | uuid not null → students(id) on delete cascade | |
| month | date not null | the FIRST day of the month ('2026-11-01'); check extract(day) = 1 |
| paid | boolean not null default false | true = «سلم», false = «لم يسلم» |
| amount | numeric(14,2) null | ≥ 0; the amount paid (prefilled from the circle fee `circles.fee_yer`, editable) |
| marked_by | uuid default auth.uid() → profiles(id) | must equal the caller |
| created_at / updated_at | timestamptz default now() | send updated_at = now on every change |
Constraint `student_subscriptions_student_month_key unique (student_id, month)`. No row for a student = not marked = shown as «لم يسلم».
Helper `public.can_mark_subscription(sid uuid)` = student of my centre and (is_admin() or teaches_student(sid)).
RLS `ss_read` / `ss_insert` / `ss_update` / `ss_delete`: the admin reads/writes every student of the centre; a teacher reads/writes
ONLY students of his own circle; viewers, other centres, anon: denied.
NOT linked to «الميزانية» (no budget rows are created from it).

Client (same as Android):
- Read a month: `GET /rest/v1/student_subscriptions?select=*&month=eq.<YYYY-MM-01>`
- Mark: `POST /rest/v1/student_subscriptions?on_conflict=student_id,month` with `Prefer: resolution=merge-duplicates,return=minimal`,
  body `{center_id, student_id, month, paid, amount (null when not paid), marked_by, updated_at}`.
- Where: teacher «المزيد» (his circle(s) only); admin «الإدارة» (any circle). Viewers don't see it. Online only.

  Screen layout (his reference, same look as «الحضور والغياب»): title «الاشتراكات الشهرية»; circle chips on top (teachers with
  more than one circle / the admin see all); a month card with ‹ › showing the Gregorian month and the Hijri month(s) it spans;
  chips «سلم N» · «لم يسلم N» · «المجموع X ر.ي»; one card per student (photo/initials + name) with two buttons «سلم» (green when
  chosen) / «لم يسلم» (red); when «سلم» the amount field shows, prefilled from the circle fee; «حفظ» saves only the changed rows.
  Menu: teacher «المزيد» → «الاشتراكات الشهرية»; admin «الإدارة» → «الاشتراكات الشهرية».


## 5) App-only changes (no server) — his words 9:42 PM
- Inside the app: no top/header buttons for «تواصل مع المطور», «الثيمات», «عن المركز», «الأدوات».
  «الثيمات», «عن المركز» and «تواصل مع المطور» are ONLY in the settings page of each section (viewer الإعدادات, admin الإدارة,
  teacher المزيد). «الأدوات» is ONLY the bottom-bar tab (the tools lists were removed from the settings pages and the viewer
  home; the «الأدوات» tab itself now shows only his tools block, without the themes/about/developer rows).
- Opening (password) screen: the top «الأدوات» and «الثيمات» buttons are gone. Exactly two buttons stay under the card:
  «عن المركز» and «المطوّر» beside it (opens «تواصل مع المطوّر»). The tools block below the card stays as it was.
- The «عن المركز» page no longer has the «تواصل مع المطوّر» row at its bottom (it's in settings only).
- Hifz «تسميع»: the mic keeps listening until the user taps stop — the recogniser restarts by itself after every result,
  silence or recoverable error; silence lengths 5000 ms (complete and possibly-complete), partial results on; matching unchanged.
- Logo: the round white disc inside the gold ring (already done in iOS build 25).
- Home greeting (10:15 PM «خليه سلام كامل»): «السلام عليكم ورحمة الله وبركاته» on every home header, up to two lines on 360dp; the name line under it unchanged.

## 6) More than one teacher per circle — his words 10:05 PM (migration 0019, LIVE, applied with NO backfill: 0 rows written)
Table `public.circle_teachers (circle_id uuid → circles(id) on delete cascade, teacher_profile_id uuid → profiles(id) on delete cascade,
created_at timestamptz default now(), primary key (circle_id, teacher_profile_id))`.
- A circle's teachers = `circles.teacher_id` ∪ the `circle_teachers` rows for it, de-duplicated. Existing circles have NO rows (no
  backfill); their single `teacher_id` keeps working exactly as before. Older builds keep reading/writing `teacher_id` only.
- RLS: everyone of the centre reads; ONLY the admin inserts/deletes; the profile added must be an active teacher of the same centre.
- `teaches_circle(cid)` / `teaches_student(sid)` now = `teacher_id = me` OR a `circle_teachers` row for me — so every listed teacher
  gets the circle's students, attendance, recitations, subscriptions and the «لوحة» upload, with no other policy changed.
- Trigger `circles_sync_teacher` (on insert/update of `circles.teacher_id`): adds the new teacher_id to circle_teachers and removes the
  old one, so an old build's reassignment stays consistent.
- Client save (admin, «تعديل الحلقة» → «المعلمون» list with «إضافة أستاذ» and ✕ per teacher): PATCH `circles.teacher_id` = the FIRST
  teacher (null if none; `teacher_name` the same), then `DELETE circle_teachers?circle_id=eq.X&teacher_profile_id=not.in.(kept)`,
  then upsert the kept ids (`on_conflict=circle_id,teacher_profile_id`, `resolution=ignore-duplicates`). Removing the teacher_id
  teacher therefore moves teacher_id to the next listed teacher, or null.
- Load: `GET /rest/v1/circle_teachers?select=circle_id,teacher_profile_id&order=created_at` (missing table = single teacher only).
- Cards / circle page show all names joined with «، ». Rollback test: server_v33/rollback_test_v38e.txt (21 checks).
- «طالب في أكثر من حلقة» (circle_students) was CANCELLED by him at 10:13 PM: nothing was created on the server.

## 7) «وسام الشرف لهذا الأسبوع» — his words 10:15 PM, then «تلقائي» (automatic)
Computed on the phone from the synced data; NO server table is used. For each active circle, for the week from SATURDAY to today:
the student (active, in the circle) with the most ayat in his recitation records this week; ties → fewest «غائب» attendance marks
this week → higher average grade → name. No record in the circle this week → «لم يُحدد بعد».
Shown on every home screen (admin, teacher, viewer) as a card «وسام الشرف لهذا الأسبوع» with one row per circle:
«من حلقة الأستاذ <teacher>» / «الطالب <name>» with the student photo. Also INSIDE EACH CIRCLE: a gold strip «نجم الحلقة لهذا الأسبوع»
+ round photo + name on every circle card (circles list, teacher home) and at the top of the circle page (under the «لوحة»).
Separate from «المتفوقون».
Note: a table `public.weekly_honors` (migration 0020) was created live (empty, 0 rows) before he chose «تلقائي»; it is unused —
leave it, do not read or write it.

## 8) A photo on an announcement (admin) — his words 10:24 PM «في الإعلانات إضافه صوره كمان من الاداره» (migration 0021, LIVE, 0 rows written)
- Column `public.announcements.image_path text null` (existing announcements stay null). Check `announcements_image_path_chk`:
  null or `<center_id>/announcements/<announcement id>/%`.
- Restrictive insert policy `ann_image_admin`: a row WITH image_path only from the admin; teachers keep posting text-only as before.
- Storage: the existing private bucket `student-photos`, path `<center_id>/announcements/<announcement id>/<8 chars>.jpg`.
  Policies `annimg_insert` / `annimg_update` / `annimg_delete`: admin of that centre only. Read: the existing `sp_read`
  (anyone signed in to the centre, viewer included). Other centres and anon: no access.
- Client (Android): «إعلان جديد» (admin) has «صورة (اختياري)» with المعرض / الكاميرا / إزالة; the photo is reduced to 1280 px,
  JPEG ≤ ~280 KB, uploaded FIRST (`POST /storage/v1/object/student-photos/<path>`; needs internet), then the announcement row is
  inserted with `image_path`. Deleting the announcement also removes the object (best effort).
- Display: full-width photo in the announcement card (tap = full screen), and a smaller one in the viewer home «آخر التقارير والإعلانات».
- Rollback test: server_v33/rollback_test_v38g.txt (20 checks).
