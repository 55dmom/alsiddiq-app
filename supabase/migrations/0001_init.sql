-- مركز الصديق — central database schema and security rules (Supabase / Postgres)
-- Applied once the owner's Supabase project is connected. Every table has Row Level Security ON.

create extension if not exists pgcrypto;

create type public.app_role as enum ('admin', 'teacher', 'student', 'parent');
create type public.record_kind as enum ('hifz', 'tasmee', 'muraja');
create type public.grade as enum ('excellent', 'very_good', 'good', 'pass', 'weak');
create type public.attendance_status as enum ('present', 'late', 'excused', 'absent');
create type public.audience as enum ('all', 'teachers', 'parents', 'students');
create type public.student_status as enum ('active', 'paused', 'graduated');

create table public.centers (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  city text not null default '',
  created_at timestamptz not null default now()
);

-- One row per login (auth.users). Role and center are set only by the admin-only function.
create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  center_id uuid not null references public.centers(id) on delete cascade,
  full_name text not null,
  phone text not null,
  role public.app_role not null,
  active boolean not null default true,
  student_id uuid,
  created_at timestamptz not null default now(),
  unique (center_id, phone)
);

create table public.circles (
  id uuid primary key default gen_random_uuid(),
  center_id uuid not null references public.centers(id) on delete cascade,
  name text not null,
  teacher_id uuid references public.profiles(id) on delete set null,
  level text not null default '',
  schedule text not null default '',
  capacity int not null default 15 check (capacity between 1 and 500),
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.students (
  id uuid primary key default gen_random_uuid(),
  center_id uuid not null references public.centers(id) on delete cascade,
  full_name text not null,
  phone text not null default '',
  birth_year int,
  circle_id uuid references public.circles(id) on delete set null,
  joined_on date not null default current_date,
  goal_juz int not null default 5 check (goal_juz between 1 and 30),
  status public.student_status not null default 'active',
  notes text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.profiles add constraint profiles_student_fk foreign key (student_id) references public.students(id) on delete set null;

create table public.parent_students (
  parent_id uuid not null references public.profiles(id) on delete cascade,
  student_id uuid not null references public.students(id) on delete cascade,
  primary key (parent_id, student_id)
);

-- id is generated on the phone so an offline save retried later is the same row (idempotent upsert).
create table public.recitations (
  id uuid primary key,
  center_id uuid not null references public.centers(id) on delete cascade,
  student_id uuid not null references public.students(id) on delete cascade,
  circle_id uuid references public.circles(id) on delete set null,
  teacher_id uuid not null references public.profiles(id),
  kind public.record_kind not null,
  from_surah int not null check (from_surah between 1 and 114),
  from_ayah int not null check (from_ayah >= 1),
  to_surah int not null check (to_surah between 1 and 114),
  to_ayah int not null check (to_ayah >= 1),
  grade public.grade not null,
  mistakes int not null default 0 check (mistakes >= 0),
  notes text not null default '',
  recorded_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check ((to_surah, to_ayah) >= (from_surah, from_ayah))
);
create index on public.recitations (student_id, recorded_at desc);

create table public.attendance (
  id uuid primary key default gen_random_uuid(),
  center_id uuid not null references public.centers(id) on delete cascade,
  student_id uuid not null references public.students(id) on delete cascade,
  circle_id uuid references public.circles(id) on delete set null,
  day date not null,
  status public.attendance_status not null,
  recorded_by uuid not null references public.profiles(id),
  recorded_at timestamptz not null default now(),
  unique (student_id, day)
);

create table public.rewards (
  id uuid primary key default gen_random_uuid(),
  center_id uuid not null references public.centers(id) on delete cascade,
  student_id uuid not null references public.students(id) on delete cascade,
  points int not null check (points <> 0),
  reason text not null,
  awarded_by uuid not null references public.profiles(id),
  created_at timestamptz not null default now()
);

create table public.announcements (
  id uuid primary key default gen_random_uuid(),
  center_id uuid not null references public.centers(id) on delete cascade,
  title text not null,
  body text not null default '',
  audience public.audience not null default 'all',
  created_by uuid not null references public.profiles(id),
  created_at timestamptz not null default now()
);

create table public.notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  title text not null,
  body text not null default '',
  kind text not null default 'info',
  ref_id uuid,
  read_at timestamptz,
  created_at timestamptz not null default now()
);
create index on public.notifications (user_id, created_at desc);

-- ---------- helper functions (security definer, fixed search_path) ----------
create or replace function public.my_role() returns public.app_role
language sql stable security definer set search_path = public as
$$ select role from profiles where id = auth.uid() and active $$;

create or replace function public.my_center() returns uuid
language sql stable security definer set search_path = public as
$$ select center_id from profiles where id = auth.uid() and active $$;

create or replace function public.is_admin() returns boolean
language sql stable security definer set search_path = public as
$$ select coalesce(my_role() = 'admin', false) $$;

create or replace function public.teaches_student(sid uuid) returns boolean
language sql stable security definer set search_path = public as
$$ select exists (select 1 from students s join circles c on c.id = s.circle_id
                  where s.id = sid and c.teacher_id = auth.uid()) $$;

create or replace function public.can_see_student(sid uuid) returns boolean
language sql stable security definer set search_path = public as
$$ select exists (select 1 from students s where s.id = sid and s.center_id = my_center()) and (
     is_admin()
     or teaches_student(sid)
     or exists (select 1 from parent_students ps where ps.parent_id = auth.uid() and ps.student_id = sid)
     or exists (select 1 from profiles p where p.id = auth.uid() and p.student_id = sid)
   ) $$;

-- ---------- Row Level Security ----------
alter table public.centers enable row level security;
alter table public.profiles enable row level security;
alter table public.circles enable row level security;
alter table public.students enable row level security;
alter table public.parent_students enable row level security;
alter table public.recitations enable row level security;
alter table public.attendance enable row level security;
alter table public.rewards enable row level security;
alter table public.announcements enable row level security;
alter table public.notifications enable row level security;

create policy centers_read on public.centers for select using (id = my_center());
create policy centers_admin on public.centers for update using (id = my_center() and is_admin());

create policy profiles_read on public.profiles for select using (center_id = my_center());
-- profiles are created/changed only by the admin edge function (service role); admins may toggle fields:
create policy profiles_admin_update on public.profiles for update using (center_id = my_center() and is_admin())
  with check (center_id = my_center() and (id <> auth.uid() or role = 'admin'));

create policy circles_read on public.circles for select using (center_id = my_center());
create policy circles_admin on public.circles for all using (center_id = my_center() and is_admin())
  with check (center_id = my_center() and is_admin());

create policy students_read on public.students for select using (can_see_student(id));
create policy students_admin on public.students for all using (center_id = my_center() and is_admin())
  with check (center_id = my_center() and is_admin());

create policy ps_read on public.parent_students for select using (parent_id = auth.uid() or is_admin() or teaches_student(student_id));
create policy ps_admin on public.parent_students for all using (is_admin()) with check (is_admin());

create policy rec_read on public.recitations for select using (can_see_student(student_id));
create policy rec_write on public.recitations for insert with check (
  center_id = my_center() and teacher_id = auth.uid() and (is_admin() or teaches_student(student_id)));
create policy rec_update on public.recitations for update using (is_admin() or teaches_student(student_id));
create policy rec_delete on public.recitations for delete using (is_admin() or teaches_student(student_id));

create policy att_read on public.attendance for select using (can_see_student(student_id));
create policy att_write on public.attendance for insert with check (
  center_id = my_center() and recorded_by = auth.uid() and (is_admin() or teaches_student(student_id)));
create policy att_update on public.attendance for update using (is_admin() or teaches_student(student_id));

create policy rew_read on public.rewards for select using (can_see_student(student_id));
create policy rew_write on public.rewards for insert with check (
  center_id = my_center() and awarded_by = auth.uid() and (is_admin() or teaches_student(student_id)));

create policy ann_read on public.announcements for select using (center_id = my_center() and (
  audience = 'all' or is_admin()
  or (audience = 'teachers' and my_role() = 'teacher')
  or (audience = 'parents' and my_role() = 'parent')
  or (audience = 'students' and my_role() = 'student')));
create policy ann_write on public.announcements for insert with check (
  center_id = my_center() and created_by = auth.uid() and my_role() in ('admin', 'teacher'));
create policy ann_delete on public.announcements for delete using (is_admin() or created_by = auth.uid());

create policy notif_read on public.notifications for select using (user_id = auth.uid());
create policy notif_mark on public.notifications for update using (user_id = auth.uid()) with check (user_id = auth.uid());

-- ---------- automatic notifications (fan-out on the server) ----------
create or replace function public.notify_recitation() returns trigger
language plpgsql security definer set search_path = public as $$
declare sname text;
begin
  select full_name into sname from students where id = new.student_id;
  insert into notifications (user_id, title, body, kind, ref_id)
  select uid, (case new.kind when 'hifz' then 'حفظ جديد' when 'tasmee' then 'تسميع' else 'مراجعة' end) || ': ' || sname,
         'سورة ' || new.from_surah || ' آية ' || new.from_ayah || ' إلى سورة ' || new.to_surah || ' آية ' || new.to_ayah,
         'record', new.id
  from (select parent_id as uid from parent_students where student_id = new.student_id
        union select id from profiles where student_id = new.student_id) t;
  return new;
end $$;
create trigger trg_notify_recitation after insert on public.recitations for each row execute function public.notify_recitation();

create or replace function public.notify_absence() returns trigger
language plpgsql security definer set search_path = public as $$
declare sname text;
begin
  if new.status = 'absent' then
    select full_name into sname from students where id = new.student_id;
    insert into notifications (user_id, title, body, kind, ref_id)
    select parent_id, 'غياب ' || sname, 'سُجّل غياب بتاريخ ' || new.day, 'absence', new.student_id
    from parent_students where student_id = new.student_id;
  end if;
  return new;
end $$;
create trigger trg_notify_absence after insert or update of status on public.attendance for each row execute function public.notify_absence();

create or replace function public.notify_announcement() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into notifications (user_id, title, body, kind, ref_id)
  select id, 'إعلان: ' || new.title, left(new.body, 140), 'announcement', new.id
  from profiles
  where center_id = new.center_id and active and id <> new.created_by and (
    new.audience = 'all' or role = 'admin'
    or (new.audience = 'teachers' and role = 'teacher')
    or (new.audience = 'parents' and role = 'parent')
    or (new.audience = 'students' and role = 'student'));
  return new;
end $$;
create trigger trg_notify_announcement after insert on public.announcements for each row execute function public.notify_announcement();

-- Live sync: phones subscribe to changes on these tables (RLS still applies).
alter publication supabase_realtime add table public.recitations, public.attendance, public.rewards,
  public.announcements, public.notifications, public.students, public.circles;
