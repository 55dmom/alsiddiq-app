-- v3.9 (2026-11-09): teachers' sheet — three statuses (حاضر / غائب / مستأذن), «حضور و انصراف» (check-in, check-out),
-- and the admin's «تواصل مع المركز» switch (default off).
-- Additive only: two nullable columns, the status check widened to also accept 'excused' (nothing removed), one new table.
-- No existing row is written, changed or backfilled. Old app builds keep working: they select and upsert only the old columns.
-- Rollback-tested inside BEGIN … ROLLBACK (server_v33/test_v39_rollback.sql).

alter table public.teacher_attendance add column if not exists check_in  time null;
alter table public.teacher_attendance add column if not exists check_out time null;

alter table public.teacher_attendance drop constraint if exists teacher_attendance_status_chk;
alter table public.teacher_attendance add constraint teacher_attendance_status_chk
  check (status in ('present', 'absent', 'excused'));

create table if not exists public.centre_settings (
  center_id      uuid primary key references public.centers(id) on delete cascade,
  contact_button boolean not null default false,            -- «تواصل مع المركز»: off until the admin switches it on
  contact_phone  text not null default '',                  -- the number the button dials (typed by the admin)
  updated_at     timestamptz not null default now()          -- the client sends it (no trigger in this schema)
);
alter table public.centre_settings enable row level security;

drop policy if exists settings_read on public.centre_settings;
create policy settings_read on public.centre_settings for select to anon, authenticated using (true);
drop policy if exists settings_admin_insert on public.centre_settings;
create policy settings_admin_insert on public.centre_settings for insert to authenticated
  with check (center_id = public.my_center() and public.is_admin());
drop policy if exists settings_admin_update on public.centre_settings;
create policy settings_admin_update on public.centre_settings for update to authenticated
  using (center_id = public.my_center() and public.is_admin())
  with check (center_id = public.my_center() and public.is_admin());

revoke all on public.centre_settings from anon, authenticated;
grant select on public.centre_settings to anon, authenticated;
grant insert, update on public.centre_settings to authenticated;
grant all on public.centre_settings to service_role;
