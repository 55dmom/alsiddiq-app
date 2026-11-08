-- v3.8 (2026-11-08): «وسام الشرف لهذا الأسبوع» — the star student of each circle this week. His words (10:15 PM):
-- «وتجعل عند كل استاذ مكتوب وسام الشرف لهذا الاسبوع من حلقة الاستاذ عصام الطالب أحمد / يعني كل استاذ معه طالب متميز أو نجم الحلقة لهذا الاسبوع».
-- One pick per circle per week (week_start = the Saturday the week starts on). Picked by hand: a teacher for his own circle(s),
-- the admin for any circle. Everyone of the centre reads it (viewer included). Separate from «المتفوقون» (honor_cards).
-- Additive and idempotent; no existing table, row or policy is touched.

create table if not exists public.weekly_honors (
  id          uuid primary key default gen_random_uuid(),
  center_id   uuid not null references public.centers(id) on delete cascade,
  circle_id   uuid not null references public.circles(id) on delete cascade,
  week_start  date not null,
  student_id  uuid not null references public.students(id) on delete cascade,
  chosen_by   uuid default auth.uid() references public.profiles(id) on delete set null,
  created_at  timestamptz not null default now(),
  constraint weekly_honors_circle_week_key unique (circle_id, week_start),
  constraint weekly_honors_saturday_chk check (extract(isodow from week_start) = 6)
);
create index if not exists weekly_honors_center_week_idx on public.weekly_honors (center_id, week_start desc);

alter table public.weekly_honors enable row level security;
revoke all on public.weekly_honors from public, anon, authenticated;
grant select, insert, update, delete on public.weekly_honors to authenticated;
grant all on public.weekly_honors to service_role;

-- the circle is of my centre and I may pick for it; the student belongs to that circle (or at least to my centre)
create or replace function public.can_pick_weekly_honor(cid uuid, sid uuid) returns boolean
language sql stable security definer set search_path = public as
$$ select exists (select 1 from circles c where c.id = cid and c.center_id = my_center())
          and exists (select 1 from students s where s.id = sid and s.center_id = my_center() and s.active and s.circle_id = cid)
          and (is_admin() or teaches_circle(cid)) $$;
revoke execute on function public.can_pick_weekly_honor(uuid, uuid) from public, anon;
grant execute on function public.can_pick_weekly_honor(uuid, uuid) to authenticated, service_role;

drop policy if exists wh_read on public.weekly_honors;
create policy wh_read on public.weekly_honors for select to authenticated
  using (center_id = my_center());
drop policy if exists wh_insert on public.weekly_honors;
create policy wh_insert on public.weekly_honors for insert to authenticated
  with check (center_id = my_center() and chosen_by = auth.uid() and can_pick_weekly_honor(circle_id, student_id));
drop policy if exists wh_update on public.weekly_honors;
create policy wh_update on public.weekly_honors for update to authenticated
  using (center_id = my_center() and (is_admin() or teaches_circle(circle_id)))
  with check (center_id = my_center() and chosen_by = auth.uid() and can_pick_weekly_honor(circle_id, student_id));
drop policy if exists wh_delete on public.weekly_honors;
create policy wh_delete on public.weekly_honors for delete to authenticated
  using (center_id = my_center() and (is_admin() or teaches_circle(circle_id)));

do $$ begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime')
     and not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime'
                     and schemaname = 'public' and tablename = 'weekly_honors') then
    alter publication supabase_realtime add table public.weekly_honors;
  end if;
end $$;
