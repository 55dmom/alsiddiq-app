-- v3.3 (2026-11-08): each teacher has his OWN password again (reverses v3.1's shared teachers' password).
-- The admin adds a teacher, assigns his circle and sets his password (access function: teacher_save / teacher_remove).
-- A teacher EDITS only his own circle (students incl. photo, recitations, attendance, rewards) and VIEWS every circle
-- of the centre read-only. The shared 'teachers' account (access_kind 'teachers', v3.1) no longer opens edit access;
-- any such row is left in place (not deleted), it is simply ignored for edits and the access function no longer
-- issues it a session.
-- Additive and safe for existing rows: no table, column or row is touched; only functions and policies are replaced.

-- 1) The v3.1 shared account no longer counts. Every old policy that references is_teachers() becomes inert.
create or replace function public.is_teachers() returns boolean
language sql stable security definer set search_path = public as
$$ select false $$;

-- 2) Is the caller an ACTIVE per-teacher account (access_kind 'teacher') with a live session?
create or replace function public.is_circle_teacher() returns boolean
language sql stable security definer set search_path = public as
$$ select exists (select 1 from profiles p where p.id = auth.uid() and p.active
                  and p.role = 'teacher' and p.access_kind = 'teacher')
          and session_alive() $$;
revoke execute on function public.is_circle_teacher() from public, anon;
grant execute on function public.is_circle_teacher() to authenticated, service_role;

-- 3) Own circle only, centre-scoped. Every write policy (recitations, attendance, rewards, students) goes through these.
create or replace function public.teaches_circle(cid uuid) returns boolean
language sql stable security definer set search_path = public as
$$ select is_circle_teacher()
          and exists (select 1 from circles c where c.id = cid and c.teacher_id = auth.uid() and c.center_id = my_center()) $$;

create or replace function public.teaches_student(sid uuid) returns boolean
language sql stable security definer set search_path = public as
$$ select is_circle_teacher()
          and exists (select 1 from students s join circles c on c.id = s.circle_id
                      where s.id = sid and c.teacher_id = auth.uid()
                        and s.center_id = my_center() and c.center_id = my_center()) $$;

-- 4) Reading: every teacher reads every student of his centre (and through it recitations, attendance, rewards).
create or replace function public.can_see_student(sid uuid) returns boolean
language sql stable security definer set search_path = public as
$$ select exists (select 1 from students s where s.id = sid and s.center_id = my_center()) and (
     is_admin()
     or is_viewer()
     or my_role() = 'teacher'
     or teaches_student(sid)
     or exists (select 1 from parent_students ps where ps.parent_id = auth.uid() and ps.student_id = sid)
     or exists (select 1 from profiles p where p.id = auth.uid() and p.student_id = sid)
   ) $$;

-- 5) Announcements («تقرير اليوم» is an announcement): admin or an active per-teacher account only
--    (before: any my_role()='teacher', which included the shared account).
drop policy if exists ann_write on public.announcements;
create policy ann_write on public.announcements for insert with check (
  center_id = my_center() and created_by = auth.uid() and (is_admin() or is_circle_teacher()));
drop policy if exists ann_delete on public.announcements;
create policy ann_delete on public.announcements for delete using (
  is_admin() or (created_by = auth.uid() and is_circle_teacher()));

-- 6) Student photos at '<center_id>/<student_id>/<file>': admin, or the teacher whose own circle holds that student.
--    A folder name that is not a uuid yields NULL (no error) and is simply refused.
create or replace function public.try_uuid(t text) returns uuid
language plpgsql immutable set search_path = public as
$$ begin
  if t is null or t !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then return null; end if;
  return t::uuid;
exception when others then return null;
end $$;
grant execute on function public.try_uuid(text) to authenticated, service_role;

drop policy if exists sp_insert on storage.objects;
create policy sp_insert on storage.objects for insert to authenticated with check (
  bucket_id = 'student-photos'
  and (storage.foldername(name))[1] = public.my_center()::text
  and (public.is_admin() or public.teaches_student(public.try_uuid((storage.foldername(name))[2]))));
drop policy if exists sp_update on storage.objects;
create policy sp_update on storage.objects for update to authenticated using (
  bucket_id = 'student-photos'
  and (storage.foldername(name))[1] = public.my_center()::text
  and (public.is_admin() or public.teaches_student(public.try_uuid((storage.foldername(name))[2]))))
with check (
  bucket_id = 'student-photos'
  and (storage.foldername(name))[1] = public.my_center()::text
  and (public.is_admin() or public.teaches_student(public.try_uuid((storage.foldername(name))[2]))));
drop policy if exists sp_delete on storage.objects;
create policy sp_delete on storage.objects for delete to authenticated using (
  bucket_id = 'student-photos'
  and (storage.foldername(name))[1] = public.my_center()::text
  and (public.is_admin() or public.teaches_student(public.try_uuid((storage.foldername(name))[2]))));
-- sp_read (every signed-in session of the centre reads its photos) is unchanged.

-- 7) access_match: kind 'teacher' (Settings → المعلمين) matches per-teacher accounts only again.
--    (kind NULL still returns every match incl. a v3.1 'teachers' row; the access function refuses that one.)
create or replace function public.access_match(p_center uuid, p_kind text, p_password text)
returns table (user_id uuid, email text, kind text)
language sql stable security definer set search_path = public, extensions, auth as $$
  select u.id, u.email::text, p.access_kind
  from auth.users u join public.profiles p on p.id = u.id
  where p.center_id = p_center and p.active and p.access_kind is not null
    and (p_kind is null or p.access_kind = p_kind)
    and u.encrypted_password is not null and u.encrypted_password <> ''
    and u.encrypted_password = extensions.crypt(p_password, u.encrypted_password)
$$;
revoke execute on function public.access_match(uuid, text, text) from public, anon, authenticated;
grant execute on function public.access_match(uuid, text, text) to service_role;
