create or replace function public.ping() returns timestamptz language sql security definer set search_path = public as $$ select now() $$;
grant execute on function public.ping() to anon, authenticated;
