begin;

insert into public.drivenext_cars (id, brand, model, price, transmission, fuel)
values (7, 'Tesla', 'Model 3 2019', 2500, 'A/T', 'Электро')
on conflict (id) do nothing;

alter table public.drivenext_bookings add column if not exists daily_price integer;
update public.drivenext_bookings as booking
set daily_price = car.price
from public.drivenext_cars as car
where booking.car_id = car.id and booking.daily_price is null;
alter table public.drivenext_bookings alter column daily_price set not null;

create or replace function public.drivenext_set_booking_price()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    select price into new.daily_price from public.drivenext_cars where id = new.car_id;
    return new;
end;
$$;

drop trigger if exists drivenext_set_booking_price on public.drivenext_bookings;
create trigger drivenext_set_booking_price
    before insert on public.drivenext_bookings
    for each row execute function public.drivenext_set_booking_price();

grant delete on public.drivenext_bookings to authenticated;
drop policy if exists "DriveNext cancel own active booking" on public.drivenext_bookings;
create policy "DriveNext cancel own active booking" on public.drivenext_bookings
    for delete to authenticated using (
        (select auth.uid()) = user_id
        and end_date + time '08:00' > (now() at time zone 'Europe/Moscow')
    );

notify pgrst, 'reload schema';
commit;
