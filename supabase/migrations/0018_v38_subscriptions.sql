-- v3.8 (2026-11-08): «كشف الاشتراكات» — the monthly subscriptions sheet. His words (10:03 PM):
-- «سوي كشف نفس هذا عند المدرسين الاشتراكات الشهرية واكتب سلم أو لم يسلم سلم مع المبلغ».
-- One row per student per month: paid (سلم) with the amount, or not paid (لم يسلم). No row = not marked yet (shown as لم يسلم).
-- The admin reads/writes every student of his centre; a teacher reads/writes ONLY the students of his own circle.
-- Separate from «الميزانية» (nothing is copied into budget_entries). Additive and idempotent; no existing row is touched.

create table if not exists public.student_subscriptions (
  id          uuid primary key default gen_random_uuid(),
  center_id   uuid not null references public.centers(id) on delete cascade,
  student_id  uuid not null references public.students(id) on delete cascade,
  month       date not null,
  paid        boolean not null default false,
  amount      numeric(14,2),
  marked_by   uuid default auth.uid() references public.profiles(id) on delete set null,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  constraint student_subscriptions_student_month_key unique (student_id, month),
  constraint student_subscriptions_month_first_chk check (extract(day from month) = 1),
  constraint student_subscriptions_amount_chk check (amount is null or amount >= 0)
);
create index if not exists student_subscriptions_center_month_idx on public.student_subscriptions (center_id, month);

alter table public.student_subscriptions enable row level security;
revoke all on public.student_subscriptions from public, anon, authenticated;
grant select, insert, update, delete on public.student_subscriptions to authenticated;
grant all on public.student_subscriptions to service_role;

-- the student must belong to the caller's centre: the admin any of them, a teacher only his own circle's
create or replace function public.can_mark_subscription(sid uuid) returns boolean
language sql stable security definer set search_path = public as
$$ select exists (select 1 from students s where s.id = sid and s.center_id = my_center())
          and (is_admin() or teaches_student(sid)) $$;
revoke execute on function public.can_mark_subscription(uuid) from public, anon;
grant execute on function public.can_mark_subscription(uuid) to authenticated, service_role;

drop policy if exists ss_read on public.student_subscriptions;
create policy ss_read on public.student_subscriptions for select to authenticated
  using (center_id = my_center() and can_mark_subscription(student_id));
drop policy if exists ss_insert on public.student_subscriptions;
create policy ss_insert on public.student_subscriptions for insert to authenticated
  with check (center_id = my_center() and marked_by = auth.uid() and can_mark_subscription(student_id));
drop policy if exists ss_update on public.student_subscriptions;
create policy ss_update on public.student_subscriptions for update to authenticated
  using (center_id = my_center() and can_mark_subscription(student_id))
  with check (center_id = my_center() and marked_by = auth.uid() and can_mark_subscription(student_id));
drop policy if exists ss_delete on public.student_subscriptions;
create policy ss_delete on public.student_subscriptions for delete to authenticated
  using (center_id = my_center() and can_mark_subscription(student_id));
