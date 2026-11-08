# مركز الصديق — v3 schema and auth (for the iOS app to mirror)

Applied to the live Supabase project "alsiddiq" (ref `otdtvxeuyqrhtuqponxn`) on 2026-11-07. Committed in
`55dmom/alsiddiq-app`: `supabase/migrations/0005_v3_viewer_role.sql`, `0006_v3_password_access.sql`,
and the edge function `supabase/functions/access/index.ts` (deployed as `access`, verify_jwt off;
it checks tokens itself).

Android reference implementation: `app/src/main/java/com/alsiddiq/app/data/cloud/CloudRepository.kt`
(functions `needsSetup`, `setup`, `signIn`, `leaveRole`, `saveTeacher`, `removeTeacher`, `setAccessPassword`).

## What changed, in one paragraph
There are no phone numbers, usernames, student accounts or parent accounts any more. There are exactly three
kinds of password: ONE general (viewer) password, ONE admin password, and ONE password per teacher. Each
password belongs to a Supabase Auth user (email `<kind>-<uuid>@access.alsiddiq.app`, never shown to anyone).
Supabase Auth stores the bcrypt hash. The app never stores or compares a password: it sends the typed password
to the `access` function, which compares it with the hashes on the server and returns that user's normal
Supabase session (access_token + refresh_token). From then on the app talks to PostgREST with that token
exactly as before, and Row Level Security decides what it can read and write.

## Schema (additive; old tables untouched)
- `app_role` enum: new value `viewer` (old values admin/teacher/student/parent remain for old rows).
- `profiles`:
  - new column `access_kind text` — `'viewer' | 'admin' | 'teacher'`. NULL means an old build-5 phone account; v3 ignores those.
  - `phone` now defaults to `''`; the unique (center_id, phone) constraint was dropped.
  - A v3 viewer row has role `viewer`, an admin row role `admin`, a teacher row role `teacher`.
- Teacher ↔ circle: unchanged, `circles.teacher_id` → `profiles.id`. The admin screen gives each teacher one circle.
- `access_attempts(id, ip, kind, ok, at)`: wrong-password log for rate limiting. RLS on, no policies (server only).
- SQL functions (service_role only):
  - `access_match(p_center uuid, p_kind text, p_password text)` → the v3 accounts whose bcrypt hash matches
    (`crypt(p_password, encrypted_password) = encrypted_password`).
  - `access_revoke(p_user uuid)`: deletes that user's sessions (used when a password changes or a teacher is removed).
- RLS helpers: `is_viewer()`, `teaches_circle(cid)`. `can_see_student()` now also returns true for the viewer.

## Row Level Security (what the server enforces)
| who | read | write |
|---|---|---|
| viewer (general password) | everything in the centre: circles, students, recitations, attendance, rewards, announcements with audience `all`, staff names | nothing (no write policy names the viewer) |
| teacher | circles list; ONLY the students of their own circle(s) and those students' records | recitations / attendance / rewards for their own students; insert a student into their own circle; edit their own students (can't move them to another teacher's circle); post announcements (circle reports) |
| admin | everything | everything (circles, students, records). Teachers and passwords only through the `access` function |

Profiles are written only by the `access` function (the old `profiles_admin_update` policy was dropped).

## The `access` edge function
`POST {SUPABASE_URL}/functions/v1/access`, headers `apikey: <publishable key>`, `Authorization: Bearer <publishable key or the user's access_token>`, JSON body:

| action | who may call | body | answer |
|---|---|---|---|
| `status` | anyone | — | `{needs_setup: bool, center_name}`; needs_setup = no v3 admin password exists yet |
| `setup` | anyone, ONLY while needs_setup | `center_name, admin_password, viewer_password` (6+ chars, different) | `{session, profile, center_name}` signed in as the admin. A second call → 403 |
| `login` | anyone (rate-limited) | `password`, optional `kind`: `null` (opening screen: any password), `"admin"` (Settings → الإدارة), `"teacher"` (Settings → المعلمين) | 200 `{session, profile: {id, full_name, role, access_kind, circle_ids}, center_name}`; 401 wrong password (after a ~1.2 s delay); 429 after 6 wrong tries from one address in 15 min, or 60 from everyone in 10 min |
| `set_password` | admin token | `target: "viewer" | "admin"`, `password` | `{ok}`. Ends every session of that account |
| `teacher_save` | admin token | `id?` (absent = new), `name`, `circle_id` (`""` = none), `password?` (required for new) | `{profile}`. Moves the teacher to that one circle |
| `teacher_remove` | admin token | `id` | `{ok}`. Soft: profile inactive, circle released, password scrambled, sessions ended; past records keep the name |

Rules the function enforces: a password must be 6+ characters; the same password can't belong to two accounts
(409 "كلمة السر هذي مستخدمة لحساب ثاني"); Arabic-Indic digits are normalised to 0–9 before hashing/comparing
(do the same on iOS so ١٢٣٤٥٦ and 123456 are the same password).

`session` is Supabase's own token response (access_token, refresh_token, expires_in, user). Refresh it with the
normal `POST /auth/v1/token?grant_type=refresh_token`. If refresh answers 4xx, that login was ended on the
server (password changed / teacher removed): drop it.

## App flow to mirror
1. Open → if `status.needs_setup`: setup screen (centre name prefilled from `center_name`, admin password ×2, general password ×2).
2. Otherwise one password field → `login` with `kind: null` → normally the viewer (read-only tabs: الرئيسية, الطلاب, الحلقات, التقارير, الإعدادات).
3. الإعدادات shows two buttons «الإدارة» and «المعلمين»; each opens a password screen → `login` with `kind` admin/teacher.
   Keep the viewer session stored underneath; "خروج" returns to it.
4. Admin tabs: الرئيسية, الحلقات, الطلاب, التقارير, الإدارة (المعلمون, كلمات السر, الحضور, تقارير الحلقات, الإعلانات, بيانات المركز).
5. Teacher tabs: الرئيسية, حلقتي, تسجيل, الحضور, المزيد (تقرير اليوم per circle, طالب جديد, المكافآت, الإعلانات).
6. A teacher's "تقرير اليوم" is posted as an `announcements` row with audience `all` and title `تقرير <circle> — <date>`, so the viewer sees it.

## What the old (build 5 / iOS build 3) clients still have
The old `admin-members` function and phone logins were left in place (nothing destroyed). السالمي's build-5
admin login (phone 735868330) still exists and still works in build 5; v3 does not use it. Once everyone is on
v3 they can be removed.


---

# v3.1 — shared teachers' password (+ student photos) — LIVE on the server since 2026-11-08 ~10:25 Riyadh

His rule (Nov 8, 10:12 AM): «اريد كلمة مرور حق المعلمين واحده تفتح كل الحلقات والمعلمين يقدرون يعدلون على كل الطلاب في اي حلقه»
and (10:14 AM): «وسوي خيار اظافه صوره الطلاب».

Committed in `55dmom/alsiddiq-app`: `supabase/migrations/0007_v31_shared_teachers_photos.sql`,
`0008_v31_revoke_tokens_now.sql`, and `supabase/functions/access/index.ts` (deployed, version 2). All additive;
nothing of his was touched (DB after the test = his centre in needs-setup + his old build-5 login, as before).

## The model now
- Passwords: ONE general (viewer, read-only, unchanged), ONE admin, ONE **shared teachers' password**.
  The per-teacher passwords are gone from the apps (the old `teacher_save`/`teacher_remove` actions and
  `access_kind = 'teacher'` accounts still work on the server for old clients, but v3.1 apps must not use them).
- The shared teachers' account is a profile with `role = 'teacher'`, `access_kind = 'teachers'` (new value),
  full_name «المعلمون». It can read and write EVERY student and record in EVERY circle of the centre:
  recitations (hifz/revision + grade), attendance, rewards, add students (any circle or none), edit students
  (incl. moving between circles), announcements («تقرير اليوم»), student photos.
  It can NOT delete students, circles, or manage teacher names / passwords (admin only).
- Admin: unchanged full control + sets/changes the teachers' password + manages the teacher-names list.
- Viewer: unchanged, read-only (it also reads the photos).

## Server changes
- `profiles.access_kind` CHECK now allows `'teachers'`.
- `is_teachers()` helper. `teaches_student(sid)` and `teaches_circle(cid)` now return true for the shared teachers'
  account for any student/circle of its centre, so all existing write policies extend to every circle.
  New policies `students_teachers_insert` / `students_teachers_update` (circle may be null or any circle of the centre).
- **New table `teacher_names(id uuid pk default gen_random_uuid(), center_id, name, active bool default true, created_at)`**
  — names only, no passwords. Everyone in the centre reads; only admin writes (plain PostgREST on `/rest/v1/teacher_names`).
- **Attribution: new nullable `by_name text` on `recitations`, `attendance`, `rewards`, `announcements`.**
  When recording, the teacher optionally picks his name from `teacher_names`; the app sends that name in `by_name`
  (the row's `teacher_id` / `recorded_by` / `awarded_by` / `created_by` is still the shared account's own id, as RLS requires
  `= auth.uid()`). Reports show `by_name` when set, otherwise the profile name («المعلمون»).
- A changed password now ends access AT ONCE: `my_center()` and `my_role()` also require the token's session
  (`auth.jwt()->>'session_id'`) to still exist in `auth.sessions` (`session_alive()`). After `set_password`, old access
  tokens get empty reads / refused writes, and the old refresh token answers 400 → the app must drop that login and
  show the password screen again (treat "refresh 4xx" or "my profile row not readable" as logged out).

## `access` function — what changed
| action | change |
|---|---|
| `status` | also returns `has_teachers_password: bool` |
| `setup` | new optional `teachers_password` (6+ chars, must differ from the admin and general passwords). v3.1 apps ALWAYS send it (setup screen asks for admin, general and teachers' passwords). |
| `login` | `kind: "teacher"` (Settings → «المعلمين») now opens the shared teachers' account; `kind: null` (opening screen) also accepts it. Profile answer: `{id, full_name: "المعلمون", role: "teacher", access_kind: "teachers", all_circles: true, circle_ids: [every circle id]}`. Rate limit unchanged (≈1.2 s per wrong try, 6 wrong per address / 15 min → 429, 60 from all / 10 min). |
| `set_password` | `target` may now be `"teachers"` (admin token). Creates the shared account if it doesn't exist yet (that is how an already-set-up centre gets one), otherwise changes it and ends all its sessions. 409 if the password belongs to another account. |

## Student photos
- New nullable `students.photo_path text`: a path inside the PRIVATE Storage bucket **`student-photos`**
  (no public URL; 300 KB limit; image/jpeg, png, webp).
- Path convention: `<center_id>/<student_id>/<random8>.jpg` (a new random name on every change so caches refresh).
- Upload: `POST {SUPABASE_URL}/storage/v1/object/student-photos/<path>` with the user's access_token, `Content-Type: image/jpeg`,
  body = JPEG ≈512 px longest side, < ~150 KB. Then `PATCH /rest/v1/students?id=eq.<sid>` `{photo_path: <path>}`.
  On change, delete the previous object (`DELETE /storage/v1/object/student-photos/<old path>`). Remove = delete the object + `photo_path: null`.
- Read: `GET {SUPABASE_URL}/storage/v1/object/authenticated/student-photos/<path>` with the access_token (cache it locally by path).
  (`POST /storage/v1/object/sign/student-photos/<path>` also works for a short-lived URL, e.g. AsyncImage on iOS.)
- Storage policies: read = any live v3 session of that centre (first folder = `my_center()`); insert/update/delete =
  admin or teacher of that centre. Viewer upload/delete → 403. Anon → not found.
- UI: round avatar wherever a student appears (lists, student page, viewer dashboard, reports); no photo → initial letter.
  Admin + teachers: pick from gallery / take a photo / remove. Viewer: sees it only.

## Verified on the live DB 2026-11-08 (direct API calls, throwaway rows, all wiped after)
setup with teachers_password; teachers' password via «المعلمين» and via the opening screen; it recorded hifz and attendance,
edited students and moved a student in two different circles, added a student into circle B and one with no circle; it could
not delete a student or write teacher_names; teachers' password on the admin button → 401; viewer password on the teachers'
button → 401; viewer: recitation/attendance/student insert 403, student edit changed nothing, teacher_names 403, reads
everything incl. `by_name`; admin changed the teachers' password → old password 401, old refresh 400, old token's writes
refused and reads empty, new password works; viewer/teachers calling set_password → 403; duplicate password → 409;
photos: teachers + admin upload, viewer downloads, viewer upload/delete refused, anon and public URL refused, other-centre
path refused; rate limit: 429 after 6 wrong. Not verified here: anything in the apps' UI (that is the app builds' job).

### Addendum (same day): circle teacher as a name
- New nullable `circles.teacher_name text` (migration `0009_v31_circle_teacher_name.sql`, live). The admin's circle editor
  picks the teacher from `teacher_names` and stores the NAME here (leave `teacher_id` null for new circles). Show
  `teacher_name ?? profile(teacher_id).full_name ?? "بدون معلم"`. The viewer's "teachers" count = active `teacher_names` rows.
- Expired login detection: after a password change the old token is not rejected with 401 — reads simply come back empty
  (the account's own `profiles` row is no longer readable). Android: if the `profiles` read does not contain my own id,
  force a token refresh; refresh 4xx → drop that login (back to the viewer view / password screen). Do the same on iOS.
- Teacher-name attribution on Android: the teachers' section has «اسمي» (pick your name from `teacher_names`, remembered on the
  phone, optional); every recitation/attendance/reward/report row then carries `by_name`.


# v3.3 — per-teacher passwords (LIVE since 2026-11-08 12:43 Riyadh)
SQL `0010_v33_per_teacher.sql` applied 12:41 Riyadh (09:41Z); `access` function version 3 deployed 12:43 (09:43Z),
`verify_jwt` false as before. Reverses v3.1's shared teachers' password (his decision, 12:30).

## The model now
- **Each teacher has his own password.** The admin (Settings → الإدارة → المعلمين) adds a teacher: name + circle + password.
  He can later change the name, the password, move him to another circle, or disable him.
- **A teacher EDITS only his own circle**: its students (add, edit, photo), hifz/revision recitations, attendance, rewards,
  and «تقرير اليوم». He **VIEWS every circle of the centre read-only** (students, recitations, attendance, rewards).
  The server refuses every write outside his circle (RLS), whatever the app sends.
- **No shared teachers' password anywhere.** A v3.1 `access_kind = 'teachers'` account, if one exists, is left in the DB
  (not deleted) but gets no session from `login` and no write anywhere (RLS). As of 12:41 the live DB has none
  (it holds 1 centre in «needs setup» and his old build-5 admin login only).
- Setup asks **admin password + general (viewer) password only**.

## Server changes (0010)
- `is_teachers()` → always `false`: every v3.1 policy that referenced it (`students_teachers_insert/update`) is inert.
- New `is_circle_teacher()`: caller is an ACTIVE profile with `role='teacher'` and `access_kind='teacher'`, live session.
- `teaches_circle(cid)` / `teaches_student(sid)`: `is_circle_teacher()` AND the circle's `teacher_id = auth.uid()`,
  circle and student in `my_center()`. All write policies on recitations / attendance / rewards / students use these.
  (Old phone teacher accounts with `access_kind` NULL no longer write.)
- `can_see_student(sid)`: also true for any `my_role()='teacher'` of the same centre → teachers read every student,
  and through it every recitation / attendance / reward (`rec_read`, `att_read`, `rew_read`).
- `ann_write` (insert announcement / «تقرير اليوم»): admin or `is_circle_teacher()` (before: any role teacher).
  `ann_delete`: admin, or the author if he is still an active per-teacher account.
- Photos (`storage.objects`, bucket `student-photos`, path `<center_id>/<student_id>/<file>`): `sp_insert`, `sp_update`,
  `sp_delete` = admin, or `teaches_student(<student_id from folder 2>)` (new `try_uuid()` makes a non-uuid folder a plain
  refusal, never an error). `sp_read` unchanged (any live session of the centre).
- `access_match(center, kind, pw)`: kind `'teacher'` matches only `'teacher'` again (kind NULL still sees a `'teachers'` row;
  the function refuses it).
- Unchanged: `my_center()`, `my_role()`, `session_alive()` (a changed password still ends access at once), admin policies.

## `access` function (version 3) — what the apps use
- `status` → `{needs_setup, center_name, has_teachers_password, per_teacher: true}`. `has_teachers_password` only says an
  old v3.1 shared account exists (it opens nothing); apps should not show any shared-password UI.
- `setup` `{center_name, admin_password, viewer_password}` → `{session, profile, center_name}`. `teachers_password`, if an
  old client sends it, is ignored (no teachers account is created).
- `login` `{password, kind?}` (`kind`: null = opening screen, `"admin"`, `"teacher"`, `"viewer"`) →
  `{session, profile, center_name}` where `profile = {id, full_name, role, access_kind, all_circles, circle_ids}`.
  For a teacher: `access_kind: "teacher"`, `all_circles: false`, `circle_ids` = the circle(s) he may EDIT (normally one).
  Everything not in `circle_ids` is read-only in the UI.
  Errors: 401 `{"error":"كلمة السر غير صحيحة"}`; the old shared teachers' password →
  401 `{"error":"كلمة سر المعلمين المشتركة ما عادت تفتح التعديل، اطلب كلمة سرك من الإدارة","code":"shared_teachers_retired"}`;
  429 `{"error":"محاولات خاطئة كثيرة، انتظر ربع ساعة ثم حاول"}` (6 wrong per address per 15 min, unchanged); 409 not set up.
- Admin only (send the admin's `access_token` as `Authorization: Bearer`; anyone else → 403 `هذه العملية للإدارة فقط`):
  - `teacher_save` `{id?, name, circle_id?, password?}` → `{profile}` (same shape as above, with his `circle_ids`).
    New teacher: `name` + `password` (≥6) required. Existing: send `id`; `password` only to change it (ends his sessions at
    once); `circle_id` to assign (`""` = no circle). One circle per teacher: assigning releases his other circles; a circle
    that belonged to another teacher moves to this one. The function also keeps `circles.teacher_name` = his name on his
    circle(s) and clears it on circles it releases. A disabled teacher is enabled again by `teacher_save` with `id` +
    a NEW password (without one → 400 `المعلم موقوف، اختر له كلمة سر جديدة لتفعيله`).
    Errors: 400 `اكتب اسم المعلم`, 400 `اختر كلمة سر للمعلم`, 400 `كلمة السر 6 أحرف أو أرقام على الأقل`,
    409 `كلمة السر هذي مستخدمة لحساب ثاني، اختر غيرها`, 404 `المعلم غير موجود`, 404 `الحلقة غير موجودة`.
  - `teacher_remove` `{id}` → `{ok:true}`: disables him (`active=false`, password scrambled, sessions ended, his circles
    released). His past rows keep his id/name.
  - `set_password` `{target: "admin"|"viewer", password}` → `{ok:true}`. `target:"teachers"` → 400 with
    `code:"shared_teachers_retired"`. A teacher's password is changed with `teacher_save`.
- **Teacher list (no new action needed — RLS already allows it):** the admin reads
  `GET /rest/v1/profiles?select=id,full_name,active&access_kind=eq.teacher&order=full_name` (disabled ones included,
  `active=false`) and `GET /rest/v1/circles?select=id,name,teacher_id,teacher_name` (his circle = `teacher_id = profile id`).
  No passwords are readable anywhere. Verified inside a rollback test.

## What the apps must do
- Admin → «المعلمين»: list (name, circle, active/disabled), add (name + circle picker + password), edit (name, circle,
  new password), disable (`teacher_remove`), enable again (edit with a new password). Remove the v3.1 «كلمة سر المعلمين»
  field from the admin's password screen and from setup.
- Settings → «المعلمين» → his own password (`login` kind `"teacher"`). The teacher screen: his circle(s) from
  `circle_ids` editable; all other circles browsable read-only (no add/edit/grade/attendance/photo/reward buttons there).
  Show the `shared_teachers_retired` message as-is.
- `by_name` on new rows = the teacher's own `full_name`; the v3.1 «اسمي» picker / `teacher_names` is no longer needed
  (the table stays, unused).
- Offline queue: a write the server refuses (403/RLS) for a circle that is not his stays visible as refused, as already
  planned; never retry it forever.

## Verified 2026-11-08 (one `BEGIN … ROLLBACK` query, throwaway rows only, run before AND after going live, same result)
Two circles A (teacher T) and B (teacher T2), a student in each, sessions in `auth.sessions`, `role authenticated` + JWT claims:
- T: insert recitation / attendance / reward for A's student, edit A's student, add a student to A, upload a photo in A's
  student folder, publish a report → all ok. On B: recitation / attendance / reward / add student / photo upload → RLS
  refusal; edit B's student, move B's student into A, edit or delete B's recitations → 0 rows. Reads B's student, B's
  recitations and both circles. Non-uuid photo folder → plain refusal.
- T with an ended session (session id not in `auth.sessions`) → no reads, no writes.
- Shared `'teachers'` account → reads, but every write refused (incl. report). Viewer → reads, every write refused.
- Admin → every write ok.
- Live table counts identical before and after (1 centre, 1 profile, 1 user, 0 circles/students/recitations/…).
- Live `access`: `status` answers `per_teacher: true`; `set_password` without an admin token → 403.
Not verified: `login` / `setup` / `teacher_save` / `teacher_remove` against the live function (they write rows: attempt log,
accounts) — the code was bundled by Supabase without errors and its changes are small; the apps' UI.

# v3.4 — «عن المركز» about page (LIVE since 2026-11-08 12:45 Riyadh)
SQL `0011_v34_about.sql`. Readable by everyone with NO password (anon key), written by the admin only. No row is
seeded: **when there is no row the apps show their bundled text and photo** (and no buttons unless bundled).

## Schema
- `public.centre_about`: `center_id uuid PK → centers(id) on delete cascade`, `body text not null default ''`,
  `photos jsonb not null default '[]'` (ordered array of object paths inside bucket `center-about`),
  `updated_at timestamptz not null default now()` — there is no updated_at trigger in this schema: **the client sends
  `updated_at`** on every write.
  Policies: `about_read` (select, anon + authenticated, all rows), `about_admin_insert`, `about_admin_update`,
  `about_admin_delete` (authenticated, `center_id = my_center() and is_admin()`).
  Grants: select → anon, authenticated; insert/update/delete → authenticated.
- `public.centre_about_buttons`: `id uuid PK default gen_random_uuid()`, `center_id uuid not null → centers(id) on delete
  cascade`, `label text not null`, `kind text not null check (kind in ('call','whatsapp','link','email','map'))`,
  `value text not null`, `sort int not null default 0`, `created_at timestamptz not null default now()`;
  index `centre_about_buttons_center_sort (center_id, sort)`. Policies `about_buttons_read`, `about_buttons_admin_insert`,
  `about_buttons_admin_update`, `about_buttons_admin_delete` (same rules). Any other `kind` is refused by the CHECK.
- Storage bucket `center-about`: public, 5 MB (5242880), image/jpeg, image/png, image/webp.
  `storage.objects` policies: `ca_read` (select, anon + authenticated, bucket center-about — lets an upsert/list work),
  `ca_insert`, `ca_update`, `ca_delete` (authenticated, `is_admin()`).

## Client
- Read (before login, anon key in `apikey` + `Authorization: Bearer <anon>`):
  `GET /rest/v1/centre_about?select=*&limit=1` and `GET /rest/v1/centre_about_buttons?select=*&order=sort`.
  Empty arrays → bundled fallback. Cache the last answer for offline.
- Photo URL: `<SUPABASE_URL>/storage/v1/object/public/center-about/<path>` (no token). Suggested path:
  `<center_id>/<random8>.jpg` (a new name on every change so caches refresh); delete replaced objects.
- Admin writes (admin's access_token):
  - text/photos: `POST /rest/v1/centre_about?on_conflict=center_id` with
    `Prefer: resolution=merge-duplicates,return=representation`, body
    `{"center_id": <his centre id>, "body": "...", "photos": ["<path>", ...], "updated_at": "<now ISO>"}`.
  - upload: `POST /storage/v1/object/center-about/<path>` (`x-upsert: true` to replace), `Content-Type: image/jpeg`;
    delete: `DELETE /storage/v1/object/center-about/<path>`.
  - buttons: `POST /rest/v1/centre_about_buttons` `{center_id, label, kind, value, sort}`;
    edit/reorder `PATCH /rest/v1/centre_about_buttons?id=eq.<id>` `{label?, kind?, value?, sort}`;
    delete `DELETE /rest/v1/centre_about_buttons?id=eq.<id>`.
    `kind` → action: call = `tel:<value>`, whatsapp = `https://wa.me/<digits>`, link = URL, email = `mailto:<value>`,
    map = URL or `geo:`/maps link.
## Verified 2026-11-08 (rollback-only test, before and after going live)
anon reads both tables (1 row each inside the test), anon writes → `permission denied`, anon storage upload refused;
teacher and viewer read, every write refused (insert = RLS error, update = 0 rows, upload refused); admin upsert,
update, add/reorder/delete buttons, upload → ok; kind 'telegram' refused by the CHECK. Live, outside a transaction, with
the real anon key over REST: `centre_about` → 200 `[]`, `centre_about_buttons` → 200 `[]`, anon insert → 401
permission denied; public URL of a missing object → "Object not found" (the bucket exists and is public).
Live counts after: 0 rows in both tables, 0 objects; nothing else changed.

## v3.4 — app conventions the iOS app must mirror (Android build 9)
- **Bundled fallback**: the app ships his text (`about/about_text_ar.txt`, byte-identical to his file) and his photo
  (`about/about_photo_1.jpg`). No row → show them. `photos` entries starting with `bundled:` name a photo shipped in the
  app (`"bundled:about_photo_1"`), not a bucket path. When the admin first saves the text or a photo, the row
  is created with `body` = the current text (his bundled one if he hasn't edited it) and
  `photos = ["bundled:about_photo_1", …new paths]`, so his photo stays until the admin removes it.
  Buttons live in their own table and never create the row (no row + buttons = bundled text/photo + his buttons).
- The address card = the text after «العنوان:» (lines joined); tapping it opens maps. Everything before it is the body.
- Buttons are ordered by `sort` (then label). Reorder = swap `sort` of two neighbours (PATCH each). New button:
  `sort = max+1`. Android writes with `on_conflict=id` upserts (client uuid). Values: call/whatsapp = digits with country
  code (Arabic-Indic digits accepted), whatsapp → `https://wa.me/<digits without + or 00>`.
- Admin edits are online-only (no outbox); readers cache the last copy + photos on the phone for offline.
- «تواصل مع المطور» is a separate screen with PLACEHOLDER values (WhatsApp, email) until السالمي sends them.

# v3.2 — offline-first writes (Android build 9; client-only, NO server change)
Prepared on the client; nothing new was applied to the server for it. What makes it safe is already there: every
row the app creates carries a **client-made uuid** primary key, so sending the same write twice is harmless.

## The queue (outbox) on the phone
- Every teacher/admin write — recitation (hifz/revision), attendance, student add/edit, reward, announcement/report,
  circle, **student photo** — is applied on screen at once, written to a queue on disk, and sent in order.
  Each queued item carries: `owner` (the profile id that made it), `at` (the phone time it was made), the row keys it
  touches (for the «بانتظار الإرسال» chip), and for a photo the path of the local file.
- Sent: immediately when online; every 30 s while the app is open; the moment the network comes back
  (ConnectivityManager callback); on every app open; and by **WorkManager** (`SyncWorker`, `NetworkType.CONNECTED`,
  every 15 min + a one-off after each write that stayed waiting) so it goes up even if the app was closed.
- **Nothing is dropped**:
  - no network / 5xx / 408 / 429 → stays, retried later;
  - 401, or the login's refresh token refused (password changed, teacher stopped, session expired) → the items stay,
    and the app shows «انتهت جلسة <name> — أدخل كلمة السر لرفعها» with a password field. The password is checked with
    `access/login` (`kind` = that account's role) and **must be the same account** (`profile.id == owner`), else
    «هذي كلمة سر حساب ثاني»; then the queue is flushed with that session;
  - 409 → already there = done;
  - 403 / other 4xx (RLS or data refused) → moved to a «رفضها الخادم» list with the server's reason, shown to the user,
    kept until he clears it. When the refusal happens while the user is looking (online save), the change is undone on
    screen and the reason is shown at once instead.
- «خروج» from a section or signing out **keeps** the queue: that account's session is held only to send its waiting
  items, then ended (`/auth/v1/logout`) once its queue is empty. A new sign-in keeps the queue and the offline copy.
- Each item is sent with **its owner's** session (current, the general one kept underneath, or a held one), so a change
  is never sent under another account.

## Conflicts — last write wins, by the time on the phone
- Recitations, rewards, announcements: insert-only with client uuid (`resolution=ignore-duplicates`) → no conflict.
- Student edits: before sending, read the server row's `updated_at`; send only if it is not newer than the item's `at`
  (the row's `updated_at` = the phone time of the edit). Otherwise the server's newer version wins and ours is skipped.
- Attendance (`on_conflict=student_id,day`): per student, compare the server's `recorded_at` for that day with the
  item's `at`; only the marks not overtaken are sent.
- Photos: upload `student-photos/<center>/<student>/<random8>.jpg`, then `PATCH students.photo_path`, then delete the
  old object. Shown from the local file meanwhile.

## What the user sees
- A status line for teachers/admin: «كل شي مرفوع ✅» or «٣ بانتظار الإرسال» (Arabic-Indic digits), «… • أدخل كلمة السر
  لرفعها» when a login ended, and «رفضها الخادم (n)» with the reasons.
- A «بانتظار الإرسال» chip on each row still waiting (students, recitations, attendance marks, a student's photo);
  the attendance button reads «محفوظ على الجهاز • بانتظار الإرسال».
- Reads are cached on disk (the whole centre the account may see), so browsing works offline; waiting changes are
  overlaid on fresh server data until they are sent.

## Verified / not verified (Android build 9)
- Unit tests (CI): the decisions — HTTP answer → sent / retry / password again / refused; last-write-wins; the status
  line text.
- NOT verified against the live server (no live writes allowed today) and not in airplane mode on a device: the CI
  screenshots of the status line / chips use a simulated queue in the demo build.

# v3.5 — circle fees (LIVE since 2026-11-08 13:08 Riyadh)
His request (1:04 PM): each circle has a fee in Yemeni rial; only the admin sets it; everyone who can see the circle sees it.
SQL `0012_v35_circle_fees.sql` (additive, no rows written).

## Schema
- `circles.fee_yer integer` (nullable, `check (fee_yer >= 0)`): the amount in YER. **null = not set → show nothing.**
- `circles.fee_period text default 'monthly'` (`check in ('monthly','term','once')`): شهرياً / للفصل / مرة واحدة.
  Existing rows read `fee_period = 'monthly'` (column default) and `fee_yer = null`.
- Writes: circles are already admin-only (`circles_admin`, ALL, `center_id = my_center() and is_admin()`); everyone in the
  centre reads them (`circles_read`). Extra guard: trigger `trg_circles_fee_guard` (function `circles_fee_guard()`) refuses any
  insert with a fee / any change of `fee_yer` or `fee_period` by a signed-in caller who is not the admin
  («رسوم الحلقة تحددها الإدارة فقط», 42501). Server code without a user (the access function's service role) is unaffected.

## Client
- Read with the circle (`select=*`). Write in the admin's circle upsert: `{"fee_yer": 5000 | null, "fee_period": "monthly"|"term"|"once"}`.
  Goes through the offline queue like any admin circle edit.
- Display (card + circle page), only when `fee_yer` is not null: «الرسوم: ٥٬٠٠٠ ريال يمني شهرياً» — Arabic-Indic digits with
  the Arabic thousands separator «٬»; period words: monthly «شهرياً», term «للفصل», once «مرة واحدة». `fee_yer = 0` shows «الرسوم: مجاناً».
- Admin form: «رسوم الحلقة (ريال يمني)» (numeric keyboard; empty = not set) + period chips شهرياً / للفصل / مرة واحدة.

## Verified 2026-11-08 (one BEGIN … ROLLBACK query, before and after going live; live counts unchanged)
Teacher (owner of the circle) and viewer read the fee, every fee write = 0 rows (RLS); admin sets the fee and the period;
negative fee refused by the CHECK; with RLS bypassed and teacher claims the trigger refuses («رسوم الحلقة تحددها الإدارة فقط»);
the service path (no user) can still update `teacher_name`.
