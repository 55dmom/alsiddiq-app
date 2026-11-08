-- v3.8 (2026-11-08): «حضور وغياب المعلمين» in the admin page. Basic only: one mark per teacher per day (حاضر / غائب).
-- His words (9:42 PM): «خلي في صفحه الاداره حظور وغياب المعلمين».
-- Only the admin of the centre reads and writes. No notes, no reasons, no notifications.
-- Additive and idempotent; no existing table, row or policy is touched.

create table if not exists public.teacher_attendance (
  id                 uuid primary key default gen_random_uuid(),
  center_id          uuid not null references public.centers(id) on delete cascade,
  teacher_profile_id uuid not null references public.profiles(id) on delete cascade,
  day                date not null,
  status             text not null,
  marked_by          uuid default auth.uid() references public.profiles(id) on delete set null,
  created_at         timestamptz not null default now(),
  constraint teacher_attendance_status_chk check (status in ('present', 'absent')),
  constraint teacher_attendance_teacher_day_key unique (teacher_profile_id, day)
);
create index if not exists teacher_attendance_center_day_idx on public.teacher_attendance (center_id, day desc);

alter table public.teacher_attendance enable row level security;
revoke all on public.teacher_attendance from public, anon, authenticated;
grant select, insert, update, delete on public.teacher_attendance to authenticated;
grant all on public.teacher_attendance to service_role;

-- the teacher must be a teacher profile of the admin's own centre
create or replace function public.is_my_center_teacher(pid uuid) returns boolean
language sql stable security definer set search_path = public as
$$ select exists (select 1 from profiles p where p.id = pid and p.role = 'teacher' and p.center_id = my_center()) $$;
revoke execute on function public.is_my_center_teacher(uuid) from public, anon;
grant execute on function public.is_my_center_teacher(uuid) to authenticated, service_role;

drop policy if exists ta_admin_read on public.teacher_attendance;
create policy ta_admin_read on public.teacher_attendance for select to authenticated
  using (center_id = my_center() and is_admin());
drop policy if exists ta_admin_insert on public.teacher_attendance;
create policy ta_admin_insert on public.teacher_attendance for insert to authenticated
  with check (center_id = my_center() and is_admin() and marked_by = auth.uid() and is_my_center_teacher(teacher_profile_id));
drop policy if exists ta_admin_update on public.teacher_attendance;
create policy ta_admin_update on public.teacher_attendance for update to authenticated
  using (center_id = my_center() and is_admin())
  with check (center_id = my_center() and is_admin() and marked_by = auth.uid() and is_my_center_teacher(teacher_profile_id));
drop policy if exists ta_admin_delete on public.teacher_attendance;
create policy ta_admin_delete on public.teacher_attendance for delete to authenticated
  using (center_id = my_center() and is_admin());
