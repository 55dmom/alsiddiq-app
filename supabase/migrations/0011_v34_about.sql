-- v3.4 (2026-11-08): «عن المركز» about page — text, ordered photos and contact buttons, edited by the admin,
-- read by everyone with NO password (the app reads it before login with the anon key).
-- Additive: two new tables, one new public bucket, new policies. No existing row is touched and NO row is seeded:
-- with no row the apps show their bundled text and photo.

create table if not exists public.centre_about (
  center_id  uuid primary key references public.centers(id) on delete cascade,
  body       text not null default '',
  photos     jsonb not null default '[]'::jsonb,      -- ordered array of object paths inside bucket 'center-about'
  updated_at timestamptz not null default now()        -- no updated_at trigger in this schema: the client sends it
);
alter table public.centre_about enable row level security;
drop policy if exists about_read on public.centre_about;
create policy about_read on public.centre_about for select to anon, authenticated using (true);
drop policy if exists about_admin_insert on public.centre_about;
create policy about_admin_insert on public.centre_about for insert to authenticated
  with check (center_id = public.my_center() and public.is_admin());
drop policy if exists about_admin_update on public.centre_about;
create policy about_admin_update on public.centre_about for update to authenticated
  using (center_id = public.my_center() and public.is_admin())
  with check (center_id = public.my_center() and public.is_admin());
drop policy if exists about_admin_delete on public.centre_about;
create policy about_admin_delete on public.centre_about for delete to authenticated
  using (center_id = public.my_center() and public.is_admin());
revoke all on public.centre_about from anon, authenticated;
grant select on public.centre_about to anon, authenticated;
grant insert, update, delete on public.centre_about to authenticated;

create table if not exists public.centre_about_buttons (
  id         uuid primary key default gen_random_uuid(),
  center_id  uuid not null references public.centers(id) on delete cascade,
  label      text not null,
  kind       text not null check (kind in ('call', 'whatsapp', 'link', 'email', 'map')),
  value      text not null,
  sort       int not null default 0,
  created_at timestamptz not null default now()
);
create index if not exists centre_about_buttons_center_sort on public.centre_about_buttons (center_id, sort);
alter table public.centre_about_buttons enable row level security;
drop policy if exists about_buttons_read on public.centre_about_buttons;
create policy about_buttons_read on public.centre_about_buttons for select to anon, authenticated using (true);
drop policy if exists about_buttons_admin_insert on public.centre_about_buttons;
create policy about_buttons_admin_insert on public.centre_about_buttons for insert to authenticated
  with check (center_id = public.my_center() and public.is_admin());
drop policy if exists about_buttons_admin_update on public.centre_about_buttons;
create policy about_buttons_admin_update on public.centre_about_buttons for update to authenticated
  using (center_id = public.my_center() and public.is_admin())
  with check (center_id = public.my_center() and public.is_admin());
drop policy if exists about_buttons_admin_delete on public.centre_about_buttons;
create policy about_buttons_admin_delete on public.centre_about_buttons for delete to authenticated
  using (center_id = public.my_center() and public.is_admin());
revoke all on public.centre_about_buttons from anon, authenticated;
grant select on public.centre_about_buttons to anon, authenticated;
grant insert, update, delete on public.centre_about_buttons to authenticated;

-- Public bucket for the about photos (5 MB, jpeg/png/webp). Public URL:
--   <SUPABASE_URL>/storage/v1/object/public/center-about/<path>
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('center-about', 'center-about', true, 5242880, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do nothing;

-- Writes: the admin only. Read: the bucket is public; ca_read also lets the admin's upsert (x-upsert) and a list work.
drop policy if exists ca_read on storage.objects;
create policy ca_read on storage.objects for select to anon, authenticated using (bucket_id = 'center-about');
drop policy if exists ca_insert on storage.objects;
create policy ca_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'center-about' and public.is_admin());
drop policy if exists ca_update on storage.objects;
create policy ca_update on storage.objects for update to authenticated
  using (bucket_id = 'center-about' and public.is_admin())
  with check (bucket_id = 'center-about' and public.is_admin());
drop policy if exists ca_delete on storage.objects;
create policy ca_delete on storage.objects for delete to authenticated
  using (bucket_id = 'center-about' and public.is_admin());
