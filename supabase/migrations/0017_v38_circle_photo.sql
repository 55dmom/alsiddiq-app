-- v3.8 (2026-11-08): a photo for the circle itself. His words (10:02 PM): «إمكانيه تسوي صوره للحلقه نفسها».
-- The admin sets/changes/removes the photo of any circle of his centre; a teacher does it for his OWN circle only.
-- Storage: the same private bucket as the student photos ('student-photos'), path '<center_id>/circles/<circle_id>/<file>.jpg'.
--   Reading is already covered by sp_read (every signed-in account of the centre reads its centre's folder).
--   Admin writes are already covered by sp_insert/sp_update/sp_delete (is_admin()); the policies below add the circle's own teacher.
-- Row: new nullable column circles.photo_path, written only through set_circle_photo() (teachers have no UPDATE on circles).
-- Additive and idempotent; no existing row is touched (the new column is NULL for every circle = the current look).

alter table public.circles add column if not exists photo_path text;

create or replace function public.set_circle_photo(cid uuid, path text) returns void
language plpgsql security definer set search_path = public as
$$
begin
  if not exists (select 1 from circles c where c.id = cid and c.center_id = my_center()) then
    raise exception 'الحلقة غير موجودة' using errcode = '42501';
  end if;
  if not (is_admin() or teaches_circle(cid)) then
    raise exception 'ليست لديك صلاحية لهذه العملية' using errcode = '42501';
  end if;
  if path is not null and (path not like (my_center()::text || '/circles/' || cid::text || '/%') or path like '%..%') then
    raise exception 'مسار الصورة غير صحيح' using errcode = '22023';
  end if;
  update circles set photo_path = path, updated_at = now() where id = cid;
end $$;
revoke execute on function public.set_circle_photo(uuid, text) from public, anon;
grant execute on function public.set_circle_photo(uuid, text) to authenticated, service_role;

drop policy if exists cp_insert on storage.objects;
create policy cp_insert on storage.objects for insert to authenticated with check (
  bucket_id = 'student-photos'
  and (storage.foldername(name))[1] = public.my_center()::text
  and (storage.foldername(name))[2] = 'circles'
  and (public.is_admin() or public.teaches_circle(public.try_uuid((storage.foldername(name))[3]))));
drop policy if exists cp_update on storage.objects;
create policy cp_update on storage.objects for update to authenticated using (
  bucket_id = 'student-photos'
  and (storage.foldername(name))[1] = public.my_center()::text
  and (storage.foldername(name))[2] = 'circles'
  and (public.is_admin() or public.teaches_circle(public.try_uuid((storage.foldername(name))[3]))))
with check (
  bucket_id = 'student-photos'
  and (storage.foldername(name))[1] = public.my_center()::text
  and (storage.foldername(name))[2] = 'circles'
  and (public.is_admin() or public.teaches_circle(public.try_uuid((storage.foldername(name))[3]))));
drop policy if exists cp_delete on storage.objects;
create policy cp_delete on storage.objects for delete to authenticated using (
  bucket_id = 'student-photos'
  and (storage.foldername(name))[1] = public.my_center()::text
  and (storage.foldername(name))[2] = 'circles'
  and (public.is_admin() or public.teaches_circle(public.try_uuid((storage.foldername(name))[3]))));
