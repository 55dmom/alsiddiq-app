-- v3.1: a circle's teacher is now just a NAME (teachers have no accounts of their own any more).
-- circles.teacher_id stays for old rows/clients; v3.1 apps show teacher_name first, then the old teacher_id's profile name.
alter table public.circles add column if not exists teacher_name text;
