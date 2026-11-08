-- v3.6 (2026-11-08): a teacher registers and dismisses (فصل) students of his OWN circle(s); the admin is told.
-- His words: «اريد الأستاذ كمان عنده صلاحية تسجيل الطلاب وفصلهم ويوصل إشعار عند الإدارة».
-- Additive and idempotent. No row is written or deleted; no existing column or function is changed.
--
-- Before v3.6 (0006/0010) a teacher was meant to insert students into his own circle and edit his own students.
-- The v3.6 rollback test found two places where the live policies did not do that; both are fixed below (step 1):
--   a) a teacher's add sent the way the apps send it (PostgREST insert / upsert, which use RETURNING / ON CONFLICT)
--      was refused: the new row must pass a SELECT policy, and can_see_student() looks the row up before it exists;
--   b) a teacher COULD move his student into another teacher's circle (or to none): the inert v3.1 policy
--      students_teachers_update kept a WITH CHECK without is_teachers(), and Postgres ORs every permissive WITH CHECK.

-- 1) Policy fixes on students (no new right: teachers already read every student of their centre, v3.3).
drop policy if exists students_teacher_read on public.students;
create policy students_teacher_read on public.students for select to authenticated
  using (center_id = my_center() and my_role() = 'teacher' and is_circle_teacher());

drop policy if exists students_teachers_update on public.students;          -- v3.1 shared account (inert since v3.3)
create policy students_teachers_update on public.students for update
  using (center_id = my_center() and is_teachers())
  with check (center_id = my_center() and is_teachers()
              and (circle_id is null or exists (select 1 from circles c where c.id = students.circle_id and c.center_id = my_center())));

-- 2) Dismiss = soft. The row stays and stays readable as before; the apps hide active=false from normal lists.
alter table public.students add column if not exists active       boolean not null default true;
alter table public.students add column if not exists dismissed_at timestamptz;
alter table public.students add column if not exists dismissed_by uuid references public.profiles(id) on delete set null;

-- 3) The admin's notifications. Written ONLY by the trigger in step 5 (SECURITY DEFINER).
create table if not exists public.admin_events (
  id               uuid primary key default gen_random_uuid(),
  center_id        uuid not null references public.centers(id) on delete cascade,
  kind             text not null check (kind in ('student_added', 'student_dismissed')),
  actor_profile_id uuid,
  actor_name       text,
  student_id       uuid,
  student          jsonb not null default '{}'::jsonb,
  circle_id        uuid,
  created_at       timestamptz not null default now(),
  read_at          timestamptz
);
create index if not exists admin_events_center_created_idx on public.admin_events (center_id, created_at desc);
create index if not exists admin_events_unread_idx on public.admin_events (center_id, created_at desc) where read_at is null;

alter table public.admin_events enable row level security;
revoke all on public.admin_events from public, anon, authenticated;
grant select on public.admin_events to authenticated;
grant update (read_at) on public.admin_events to authenticated;   -- the only column a client may change
grant all on public.admin_events to service_role;

drop policy if exists ae_admin_read on public.admin_events;
create policy ae_admin_read on public.admin_events for select to authenticated
  using (center_id = my_center() and is_admin());
drop policy if exists ae_admin_mark on public.admin_events;
create policy ae_admin_mark on public.admin_events for update to authenticated
  using (center_id = my_center() and is_admin())
  with check (center_id = my_center() and is_admin());
-- no insert / delete policy and no insert / delete grant: refused for every client.

do $$ begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime')
     and not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime'
                     and schemaname = 'public' and tablename = 'admin_events') then
    alter publication supabase_realtime add table public.admin_events;
  end if;
end $$;

-- 4) Guard (BEFORE insert/update on students): the server decides the dismiss fields.
--    - dismissed_by is always the signed-in caller; dismissed_at is the client's time if sane (offline outbox), else now().
--    - a non-admin can't add a student already dismissed (active is forced true) and can't restore one (false -> true).
--    - an update that doesn't change active keeps dismissed_at/dismissed_by as they were (a full-row upsert can't
--      revive or rewrite a dismissal).
--    - the admin may dismiss and restore; a restore clears dismissed_at/dismissed_by.
create or replace function public.students_v36_guard() returns trigger
language plpgsql set search_path = public as
$$
declare
  uid uuid := auth.uid();
  adm boolean := coalesce(is_admin(), false);
begin
  if tg_op = 'INSERT' then
    if uid is not null and not adm then new.active := true; end if;
    if new.active then
      new.dismissed_at := null; new.dismissed_by := null;
    else
      new.dismissed_by := coalesce(uid, new.dismissed_by);
      new.dismissed_at := coalesce(new.dismissed_at, now());
    end if;
    return new;
  end if;

  if old.active and not new.active then                        -- dismissed now
    new.dismissed_by := coalesce(uid, new.dismissed_by);
    if new.dismissed_at is null or new.dismissed_at > now() + interval '5 minutes'
       or new.dismissed_at < now() - interval '30 days' then
      new.dismissed_at := now();
    end if;
  elsif not old.active and new.active then                     -- restore
    if uid is not null and not adm then
      raise exception 'إرجاع الطالب المفصول للإدارة فقط' using errcode = '42501';
    end if;
    new.dismissed_at := null; new.dismissed_by := null;
  else                                                         -- active unchanged
    new.dismissed_at := old.dismissed_at; new.dismissed_by := old.dismissed_by;
  end if;
  return new;
end
$$;

drop trigger if exists students_v36_guard on public.students;
create trigger students_v36_guard before insert or update on public.students
  for each row execute function public.students_v36_guard();

-- 5) Log (AFTER, SECURITY DEFINER): one event when a TEACHER adds a student, one when a teacher dismisses one.
--    The admin's own adds/dismissals are not logged (he did them). Edits never log; a repeated dismiss doesn't log.
create or replace function public.students_v36_log() returns trigger
language plpgsql security definer set search_path = public as
$$
declare
  uid uuid := auth.uid();
  k text; aname text; arole text; cname text; clevel text; tname text;
begin
  if uid is null then return null; end if;
  select p.full_name, p.role::text into aname, arole from profiles p where p.id = uid;
  if arole is distinct from 'teacher' then return null; end if;
  if tg_op = 'INSERT' then
    k := 'student_added';
  elsif old.active and not new.active then
    k := 'student_dismissed';
  else
    return null;
  end if;

  select c.name, c.level, coalesce(c.teacher_name, tp.full_name) into cname, clevel, tname
    from circles c left join profiles tp on tp.id = c.teacher_id where c.id = new.circle_id;

  insert into admin_events (center_id, kind, actor_profile_id, actor_name, student_id, student, circle_id)
  values (new.center_id, k, uid, aname, new.id,
          jsonb_build_object(
            'full_name',    new.full_name,
            'circle_name',  cname,
            'teacher_name', tname,
            'level',        clevel,
            'phone',        new.phone,
            'birth_year',   new.birth_year,
            'joined_on',    new.joined_on,
            'goal_juz',     new.goal_juz,
            'status',       new.status::text,
            'notes',        new.notes,
            'photo_path',   new.photo_path),
          new.circle_id);
  return null;
end
$$;
revoke execute on function public.students_v36_log() from public, anon, authenticated;

drop trigger if exists students_v36_log on public.students;
create trigger students_v36_log after insert or update on public.students
  for each row execute function public.students_v36_log();
