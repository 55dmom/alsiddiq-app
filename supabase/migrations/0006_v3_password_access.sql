-- v3 access model: one shared viewer password, one admin password, one password per teacher.
-- No phone logins, no student or parent accounts. Each password belongs to a Supabase Auth user whose
-- bcrypt hash Supabase stores (auth.users.encrypted_password); the app never stores or compares passwords.
-- The edge function "access" matches an entered password against those hashes and returns that user's session.

alter table public.profiles alter column phone set default '';
alter table public.profiles drop constraint if exists profiles_center_id_phone_key;
-- Set only for v3 password accounts: 'viewer' | 'admin' | 'teacher'. NULL = an old phone account (unused by v3).
alter table public.profiles add column if not exists access_kind text check (access_kind in ('viewer', 'admin', 'teacher'));
create index if not exists profiles_access_kind_idx on public.profiles (center_id, access_kind);

-- Wrong-password log for rate limiting. Only the server (service role) can touch it.
create table if not exists public.access_attempts (
  id bigserial primary key,
  ip text not null default '',
  kind text,
  ok boolean not null,
  at timestamptz not null default now()
);
create index if not exists access_attempts_ip_at on public.access_attempts (ip, at desc);
alter table public.access_attempts enable row level security;
revoke all on public.access_attempts from anon, authenticated;

-- Which v3 account(s) does this password open? (bcrypt compare on the server; service role only)
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

-- End every session of an account (used when its password changes or a teacher is removed).
create or replace function public.access_revoke(p_user uuid) returns void
language sql security definer set search_path = public, auth as $$
  delete from auth.sessions where user_id = p_user;
$$;
revoke execute on function public.access_revoke(uuid) from public, anon, authenticated;
grant execute on function public.access_revoke(uuid) to service_role;

-- ---------- RLS for v3 ----------
create or replace function public.is_viewer() returns boolean
language sql stable security definer set search_path = public as
$$ select coalesce(my_role() = 'viewer', false) $$;

-- The viewer reads every student of the centre (read only: no write policy names the viewer).
create or replace function public.can_see_student(sid uuid) returns boolean
language sql stable security definer set search_path = public as
$$ select exists (select 1 from students s where s.id = sid and s.center_id = my_center()) and (
     is_admin()
     or is_viewer()
     or teaches_student(sid)
     or exists (select 1 from parent_students ps where ps.parent_id = auth.uid() and ps.student_id = sid)
     or exists (select 1 from profiles p where p.id = auth.uid() and p.student_id = sid)
   ) $$;

-- Teachers circles: a teacher may add students to, and edit students of, their own circle(s) only.
create or replace function public.teaches_circle(cid uuid) returns boolean
language sql stable security definer set search_path = public as
$$ select exists (select 1 from circles c where c.id = cid and c.teacher_id = auth.uid() and c.center_id = my_center()) $$;

drop policy if exists students_teacher_insert on public.students;
create policy students_teacher_insert on public.students for insert with check (
  center_id = my_center() and my_role() = 'teacher' and teaches_circle(circle_id));
drop policy if exists students_teacher_update on public.students;
create policy students_teacher_update on public.students for update
  using (my_role() = 'teacher' and teaches_student(id))
  with check (center_id = my_center() and teaches_circle(circle_id));

-- Profiles: everyone in the centre sees the staff names (the viewer sees teachers' names, never passwords).
drop policy if exists profiles_read on public.profiles;
create policy profiles_read on public.profiles for select using (
  center_id = my_center() and (id = auth.uid() or my_role() = 'admin' or role in ('admin', 'teacher')));
-- Profiles are created and changed only by the "access" edge function (service role).
drop policy if exists profiles_admin_update on public.profiles;

-- Teachers' circle reports are announcements with audience 'all'; viewers read them.
-- Viewer may mark its own notifications read (already covered by notif_mark).
