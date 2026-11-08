-- v3.5 — circle fees (his request, Nov 8 1:04 PM): each circle has a fee in Yemeni rial, set by the admin only.
-- Additive: two nullable columns (null = not set) + a guard trigger. No row is written.
alter table public.circles add column if not exists fee_yer integer check (fee_yer >= 0);
alter table public.circles add column if not exists fee_period text default 'monthly' check (fee_period in ('monthly','term','once'));

-- Writes to circles are already admin-only (policy circles_admin). Defence in depth: any signed-in caller who is not the
-- admin can never set or change the fee, whatever policy may be added later. Server-side code (service role, no
-- auth.uid()) is not affected; the access function never touches the fee.
create or replace function public.circles_fee_guard() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if (tg_op = 'INSERT' and new.fee_yer is not null)
     or (tg_op = 'UPDATE' and (new.fee_yer is distinct from old.fee_yer or new.fee_period is distinct from old.fee_period)) then
    if auth.uid() is not null and not coalesce(public.is_admin(), false) then
      raise exception 'رسوم الحلقة تحددها الإدارة فقط' using errcode = '42501';
    end if;
  end if;
  return new;
end $$;
drop trigger if exists trg_circles_fee_guard on public.circles;
create trigger trg_circles_fee_guard before insert or update on public.circles
  for each row execute function public.circles_fee_guard();
