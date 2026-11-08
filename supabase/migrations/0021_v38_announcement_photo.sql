-- v3.8 (2026-11-08): a photo on an announcement, by the admin. His words (10:24 PM): «في الإعلانات إضافه صوره كمان من الاداره».
-- Additive: one nullable column + storage policies. Existing rows stay as they are (image_path null). No row is written.
-- The photo lives in the existing private bucket `student-photos` at  <center_id>/announcements/<announcement_id>/<name>.jpg
-- Read: the existing sp_read (anyone signed in to the centre). Write: the admin only.

alter table public.announcements add column if not exists image_path text;
do $$ begin
  if not exists (select 1 from pg_constraint where conname = 'announcements_image_path_chk') then
    alter table public.announcements add constraint announcements_image_path_chk
      check (image_path is null or image_path like (center_id::text || '/announcements/' || id::text || '/%'));
  end if;
end $$;

-- only the admin may post an announcement WITH a photo (teachers keep posting text-only ones as before)
drop policy if exists ann_image_admin on public.announcements;
create policy ann_image_admin on public.announcements as restrictive for insert to authenticated
  with check (image_path is null or is_admin());

drop policy if exists annimg_insert on storage.objects;
create policy annimg_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'student-photos' and (storage.foldername(name))[1] = public.my_center()::text
              and (storage.foldername(name))[2] = 'announcements' and public.is_admin());
drop policy if exists annimg_update on storage.objects;
create policy annimg_update on storage.objects for update to authenticated
  using (bucket_id = 'student-photos' and (storage.foldername(name))[1] = public.my_center()::text
         and (storage.foldername(name))[2] = 'announcements' and public.is_admin())
  with check (bucket_id = 'student-photos' and (storage.foldername(name))[1] = public.my_center()::text
              and (storage.foldername(name))[2] = 'announcements' and public.is_admin());
drop policy if exists annimg_delete on storage.objects;
create policy annimg_delete on storage.objects for delete to authenticated
  using (bucket_id = 'student-photos' and (storage.foldername(name))[1] = public.my_center()::text
         and (storage.foldername(name))[2] = 'announcements' and public.is_admin());
