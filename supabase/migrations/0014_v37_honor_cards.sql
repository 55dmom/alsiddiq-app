-- v3.7 (2026-11-08): «المتفوقون» — congratulation cards the admin publishes whenever he wants (month-end or any time; optional).
-- His words (4:35 PM): «اريد تكون في قائمة اسمها المتفوقون، وآخر الشهر تطلع بطاقة على الشاشة تهنئة للطالب وشكر له
-- وتظهر للكل، وهذا اختياري ويقدر يسويها الإدارة بأي وقت».
-- A row IS a published card (posting = publishing). The «المتفوقون» list = the students of the newest card.
-- The ranking is chosen on the phone; the card stores a snapshot of the chosen students.
-- Additive and idempotent; no existing table, row or policy is touched.

create table if not exists public.honor_cards (
  id           uuid primary key default gen_random_uuid(),
  center_id    uuid not null references public.centers(id) on delete cascade,
  title        text not null default 'المتفوقون',
  students     jsonb not null default '[]'::jsonb,   -- [{"id","full_name","photo_path","circle_name","score_text"}, ...]
  message      text,
  published_by uuid default auth.uid() references public.profiles(id) on delete set null,
  created_at   timestamptz not null default now(),
  constraint honor_cards_students_array check (jsonb_typeof(students) = 'array' and jsonb_array_length(students) <= 200),
  constraint honor_cards_message_len check (message is null or char_length(message) <= 2000),
  constraint honor_cards_title_len check (char_length(title) between 1 and 120)
);
create index if not exists honor_cards_center_created_idx on public.honor_cards (center_id, created_at desc);

alter table public.honor_cards enable row level security;
revoke all on public.honor_cards from public, anon, authenticated;
grant select, insert, delete on public.honor_cards to authenticated;   -- no update: a card is taken down and re-posted
grant all on public.honor_cards to service_role;

drop policy if exists hc_read on public.honor_cards;
create policy hc_read on public.honor_cards for select to authenticated
  using (center_id = my_center());                       -- viewer, teacher, admin of the centre
drop policy if exists hc_admin_insert on public.honor_cards;
create policy hc_admin_insert on public.honor_cards for insert to authenticated
  with check (center_id = my_center() and is_admin() and published_by = auth.uid());
drop policy if exists hc_admin_delete on public.honor_cards;
create policy hc_admin_delete on public.honor_cards for delete to authenticated
  using (center_id = my_center() and is_admin());

do $$ begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime')
     and not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime'
                     and schemaname = 'public' and tablename = 'honor_cards') then
    alter publication supabase_realtime add table public.honor_cards;
  end if;
end $$;
