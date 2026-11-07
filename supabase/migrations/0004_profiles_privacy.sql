drop policy profiles_read on public.profiles;
-- Staff see everyone in the centre; parents and students see themselves and the centre's staff only (no other families' phone numbers).
create policy profiles_read on public.profiles for select using (
  center_id = my_center() and (id = auth.uid() or my_role() in ('admin', 'teacher') or role in ('admin', 'teacher')));
