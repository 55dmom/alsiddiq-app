-- v3.1: a changed password ends access at once. Supabase access tokens are self-contained and would otherwise keep
-- working for up to an hour after access_revoke() deleted the account's sessions; now every policy helper also
-- requires the token's session to still exist. (Service-role calls bypass RLS and are unaffected.)
create or replace function public.session_alive() returns boolean
language sql stable security definer set search_path = public, auth as
$$ select exists (select 1 from auth.sessions s where s.id = nullif(auth.jwt() ->> 'session_id', '')::uuid) $$;

create or replace function public.my_center() returns uuid
language sql stable security definer set search_path = public as
$$ select center_id from profiles where id = auth.uid() and active and session_alive() $$;

create or replace function public.my_role() returns app_role
language sql stable security definer set search_path = public as
$$ select role from profiles where id = auth.uid() and active and session_alive() $$;
