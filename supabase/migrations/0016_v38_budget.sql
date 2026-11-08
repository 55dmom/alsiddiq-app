-- v3.8 (2026-11-08): «الميزانية» in the admin page. Basic only, admin only.
-- His words (9:56 PM): «عند الاداره ميزانيه حق الطلاب المشتركين وكمان سجل خرج وصرف وكم متبقى من الميزانيه ما تضهر لحد غير الاداره».
-- One row = one entry: income (دخل, a student's subscription payment) or expense (صرف). The summary is computed on the phone.
-- Only the admin of the centre reads and writes; teachers, viewers and anon are denied. No categories, no charts, no exports.
-- Additive and idempotent; no existing table, row or policy is touched.

create table if not exists public.budget_entries (
  id          uuid primary key default gen_random_uuid(),
  center_id   uuid not null references public.centers(id) on delete cascade,
  kind        text not null,
  amount      numeric(14,2) not null,
  day         date not null default current_date,
  student_id  uuid references public.students(id) on delete set null,
  note        text,
  created_by  uuid default auth.uid() references public.profiles(id) on delete set null,
  created_at  timestamptz not null default now(),
  constraint budget_entries_kind_chk check (kind in ('income', 'expense')),
  constraint budget_entries_amount_chk check (amount > 0),
  constraint budget_entries_note_len check (note is null or char_length(note) <= 500)
);
create index if not exists budget_entries_center_day_idx on public.budget_entries (center_id, day desc);

alter table public.budget_entries enable row level security;
revoke all on public.budget_entries from public, anon, authenticated;
grant select, insert, update, delete on public.budget_entries to authenticated;
grant all on public.budget_entries to service_role;

-- a linked student must belong to the admin's own centre
create or replace function public.is_my_center_student(sid uuid) returns boolean
language sql stable security definer set search_path = public as
$$ select sid is null or exists (select 1 from students s where s.id = sid and s.center_id = my_center()) $$;
revoke execute on function public.is_my_center_student(uuid) from public, anon;
grant execute on function public.is_my_center_student(uuid) to authenticated, service_role;

drop policy if exists be_admin_read on public.budget_entries;
create policy be_admin_read on public.budget_entries for select to authenticated
  using (center_id = my_center() and is_admin());
drop policy if exists be_admin_insert on public.budget_entries;
create policy be_admin_insert on public.budget_entries for insert to authenticated
  with check (center_id = my_center() and is_admin() and created_by = auth.uid() and is_my_center_student(student_id));
drop policy if exists be_admin_update on public.budget_entries;
create policy be_admin_update on public.budget_entries for update to authenticated
  using (center_id = my_center() and is_admin())
  with check (center_id = my_center() and is_admin() and is_my_center_student(student_id));
drop policy if exists be_admin_delete on public.budget_entries;
create policy be_admin_delete on public.budget_entries for delete to authenticated
  using (center_id = my_center() and is_admin());

-- (10:00 PM) «وكمان ملاحظه عند اي عمليه صرف»: every expense needs a non-empty note; income's note stays optional.
do $$ begin
  if not exists (select 1 from pg_constraint where conname = 'budget_entries_expense_note_chk' and conrelid = 'public.budget_entries'::regclass) then
    alter table public.budget_entries add constraint budget_entries_expense_note_chk
      check (kind <> 'expense' or (note is not null and btrim(note) <> ''));
  end if;
end $$;
