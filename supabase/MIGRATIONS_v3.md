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
