-- v3.1 (2026-11-08): ONE shared teachers' password that opens every circle, plus optional student photos.
-- Additive and safe for existing rows: nothing is dropped except a CHECK constraint that is re-created wider,
-- and two helper functions that are re-created as supersets of what they allowed before.

-- 1) A new access kind 'teachers' = the single shared teachers' account (role 'teacher').
--    Old per-teacher accounts (access_kind 'teacher') keep their own-circle rules if any exist.
alter table public.profiles drop constraint if exists profiles_access_kind_check;
alter table public.profiles add constraint profiles_access_kind_check
  check (access_kind in ('viewer', 'admin', 'teacher', 'teachers'));

create or replace function public.is_teachers() returns boolean
language sql stable security definer set search_path = public as
$$ select exists (select 1 from profiles p where p.id = auth.uid() and p.active
                  and p.access_kind = 'teachers' and p.role = 'teacher') $$;

-- The shared teachers' account "teaches" every student and every circle of its centre.
-- Every existing write policy (recitations, attendance, rewards, students) already goes through these two.
create or replace function public.teaches_student(sid uuid) returns boolean
language sql stable security definer set search_path = public as
$$ select exists (select 1 from students s join circles c on c.id = s.circle_id
                  where s.id = sid and c.teacher_id = auth.uid())
       or (is_teachers() and exists (select 1 from students s where s.id = sid and s.center_id = my_center())) $$;

create or replace function public.teaches_circle(cid uuid) returns boolean
language sql stable security definer set search_path = public as
$$ select exists (select 1 from circles c where c.id = cid and c.teacher_id = auth.uid() and c.center_id = my_center())
       or (is_teachers() and exists (select 1 from circles c where c.id = cid and c.center_id = my_center())) $$;

-- Shared teachers may also add/edit students with no circle yet (any circle of the centre otherwise).
drop policy if exists students_teachers_insert on public.students;
create policy students_teachers_insert on public.students for insert with check (
  center_id = my_center() and is_teachers()
  and (circle_id is null or exists (select 1 from circles c where c.id = circle_id and c.center_id = my_center())));
drop policy if exists students_teachers_update on public.students;
create policy students_teachers_update on public.students for update
  using (center_id = my_center() and is_teachers())
  with check (center_id = my_center()
  and (circle_id is null or exists (select 1 from circles c where c.id = circle_id and c.center_id = my_center())));

-- 2) Teacher NAMES (no passwords): the admin keeps the list; a teacher optionally picks his name when recording.
create table if not exists public.teacher_names (
  id uuid primary key default gen_random_uuid(),
  center_id uuid not null references public.centers(id) on delete cascade,
  name text not null,
  active boolean not null default true,
  created_at timestamptz not null default now()
);
create index if not exists teacher_names_center on public.teacher_names (center_id);
alter table public.teacher_names enable row level security;
drop policy if exists tn_read on public.teacher_names;
create policy tn_read on public.teacher_names for select using (center_id = my_center());
drop policy if exists tn_admin on public.teacher_names;
create policy tn_admin on public.teacher_names for all
  using (center_id = my_center() and is_admin()) with check (center_id = my_center() and is_admin());
grant select, insert, update, delete on public.teacher_names to authenticated;

-- Attribution: who recorded it (the name picked from teacher_names, free text, optional).
alter table public.recitations   add column if not exists by_name text;
alter table public.attendance    add column if not exists by_name text;
alter table public.rewards       add column if not exists by_name text;
alter table public.announcements add column if not exists by_name text;

-- 3) Optional student photo: a path inside the private Storage bucket 'student-photos'.
alter table public.students add column if not exists photo_path text;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('student-photos', 'student-photos', false, 307200, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update set public = false, file_size_limit = 307200,
  allowed_mime_types = array['image/jpeg', 'image/png', 'image/webp'];

-- Objects live at '<center_id>/<student_id>/<anything>.jpg'. Only signed-in v3 sessions of that centre read;
-- admin and teachers write; the viewer reads only. No public URL exists (bucket is private).
drop policy if exists sp_read on storage.objects;
create policy sp_read on storage.objects for select to authenticated using (
  bucket_id = 'student-photos' and public.my_center() is not null
  and (storage.foldername(name))[1] = public.my_center()::text);
drop policy if exists sp_insert on storage.objects;
create policy sp_insert on storage.objects for insert to authenticated with check (
  bucket_id = 'student-photos' and (public.is_admin() or public.my_role() = 'teacher')
  and (storage.foldername(name))[1] = public.my_center()::text);
drop policy if exists sp_update on storage.objects;
create policy sp_update on storage.objects for update to authenticated using (
  bucket_id = 'student-photos' and (public.is_admin() or public.my_role() = 'teacher')
  and (storage.foldername(name))[1] = public.my_center()::text);
drop policy if exists sp_delete on storage.objects;
create policy sp_delete on storage.objects for delete to authenticated using (
  bucket_id = 'student-photos' and (public.is_admin() or public.my_role() = 'teacher')
  and (storage.foldername(name))[1] = public.my_center()::text);

-- access_match: kind 'teacher' (Settings → المعلمين) also opens the shared teachers' account.
create or replace function public.access_match(p_center uuid, p_kind text, p_password text)
returns table (user_id uuid, email text, kind text)
language sql stable security definer set search_path = public, extensions, auth as $$
  select u.id, u.email::text, p.access_kind
  from auth.users u join public.profiles p on p.id = u.id
  where p.center_id = p_center and p.active and p.access_kind is not null
    and (p_kind is null or p.access_kind = p_kind or (p_kind = 'teacher' and p.access_kind = 'teachers'))
    and u.encrypted_password is not null and u.encrypted_password <> ''
    and u.encrypted_password = extensions.crypt(p_password, u.encrypted_password)
$$;
revoke execute on function public.access_match(uuid, text, text) from public, anon, authenticated;
grant execute on function public.access_match(uuid, text, text) to service_role;
