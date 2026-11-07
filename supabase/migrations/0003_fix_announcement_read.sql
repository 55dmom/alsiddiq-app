drop policy ann_read on public.announcements;
create policy ann_read on public.announcements for select using (center_id = my_center() and (
  audience = 'all' or is_admin() or created_by = auth.uid()
  or (audience = 'teachers' and my_role() = 'teacher')
  or (audience = 'parents' and my_role() = 'parent')
  or (audience = 'students' and my_role() = 'student')));
