-- v3.8 (2026-11-08): a circle can have MORE THAN ONE teacher. His words (10:05 PM): «كمان سوي للحلقه خيار اكثر من أستاذ فيها / اظافه أستاذ وكمان أستاذ».
-- New table circle_teachers (circle_id, teacher_profile_id). Every listed teacher has the same edit rights in that circle
-- as the single teacher had (teaches_circle / teaches_student now also look at circle_teachers).
-- Older app builds keep working: circles.teacher_id stays (= the first teacher, kept in step by the new builds) and still
-- grants rights; a change of circles.teacher_id by an older build (or the access function's teacher_save/teacher_remove)
-- moves the matching circle_teachers row by trigger.
-- No backfill (no committed row writes at apply time): a circle's teachers = circles.teacher_id ∪ circle_teachers rows.

create table if not exists public.circle_teachers (
  circle_id          uuid not null references public.circles(id) on delete cascade,
  teacher_profile_id uuid not null references public.profiles(id) on delete cascade,
  created_at         timestamptz not null default now(),
  primary key (circle_id, teacher_profile_id)
);
create index if not exists circle_teachers_teacher_idx on public.circle_teachers (teacher_profile_id);

alter table public.circle_teachers enable row level security;
revoke all on public.circle_teachers from public, anon, authenticated;
grant select, insert, delete on public.circle_teachers to authenticated;
grant all on public.circle_teachers to service_role;

drop policy if exists ct_read on public.circle_teachers;
create policy ct_read on public.circle_teachers for select to authenticated
  using (exists (select 1 from circles c where c.id = circle_id and c.center_id = my_center()));
drop policy if exists ct_admin_insert on public.circle_teachers;
create policy ct_admin_insert on public.circle_teachers for insert to authenticated
  with check (is_admin() and exists (select 1 from circles c where c.id = circle_id and c.center_id = my_center())
              and is_my_center_teacher(teacher_profile_id));
drop policy if exists ct_admin_delete on public.circle_teachers;
create policy ct_admin_delete on public.circle_teachers for delete to authenticated
  using (is_admin() and exists (select 1 from circles c where c.id = circle_id and c.center_id = my_center()));

-- the rights: the old single link OR the new list
create or replace function public.teaches_circle(cid uuid) returns boolean
language sql stable security definer set search_path = public as
$$ select is_circle_teacher()
          and exists (select 1 from circles c where c.id = cid and c.center_id = my_center()
                      and (c.teacher_id = auth.uid()
                           or exists (select 1 from circle_teachers ct where ct.circle_id = c.id and ct.teacher_profile_id = auth.uid()))) $$;

create or replace function public.teaches_student(sid uuid) returns boolean
language sql stable security definer set search_path = public as
$$ select is_circle_teacher()
          and exists (select 1 from students s join circles c on c.id = s.circle_id
                      where s.id = sid and s.center_id = my_center() and c.center_id = my_center()
                        and (c.teacher_id = auth.uid()
                             or exists (select 1 from circle_teachers ct where ct.circle_id = c.id and ct.teacher_profile_id = auth.uid()))) $$;

-- older builds / the access function change circles.teacher_id: move the matching row
create or replace function public.circles_v38_teacher_sync() returns trigger
language plpgsql security definer set search_path = public as
$$
begin
  if tg_op = 'UPDATE' and old.teacher_id is not null and old.teacher_id is distinct from new.teacher_id then
    delete from circle_teachers where circle_id = new.id and teacher_profile_id = old.teacher_id;
  end if;
  if new.teacher_id is not null and (tg_op = 'INSERT' or old.teacher_id is distinct from new.teacher_id) then
    insert into circle_teachers (circle_id, teacher_profile_id) values (new.id, new.teacher_id) on conflict do nothing;
  end if;
  return null;
end $$;
drop trigger if exists circles_v38_teacher_sync on public.circles;
create trigger circles_v38_teacher_sync after insert or update of teacher_id on public.circles
  for each row execute function public.circles_v38_teacher_sync();

do $$ begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime')
     and not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime'
                     and schemaname = 'public' and tablename = 'circle_teachers') then
    alter publication supabase_realtime add table public.circle_teachers;
  end if;
end $$;
