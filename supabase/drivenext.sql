begin;

create table if not exists public.drivenext_cars (
    id integer primary key,
    brand text not null,
    model text not null,
    price integer not null check (price > 0),
    transmission text not null,
    fuel text not null
);

insert into public.drivenext_cars (id, brand, model, price, transmission, fuel)
select id, 'Mercedes-Benz', 'S 500 Sedan', 2500, 'A/T', 'Бензин'
from generate_series(1, 6) as cars(id)
on conflict (id) do nothing;

alter table public.drivenext_cars enable row level security;
revoke all on public.drivenext_cars from anon, authenticated;
grant select on public.drivenext_cars to anon, authenticated;
drop policy if exists "DriveNext catalog" on public.drivenext_cars;
create policy "DriveNext catalog" on public.drivenext_cars
    for select to anon, authenticated using (true);

create or replace function public.drivenext_find_cars(query_text text)
returns setof public.drivenext_cars
language sql stable
set search_path = ''
as $$
    select * from public.drivenext_cars
    where strpos(lower(brand || ' ' || model), lower(trim(query_text))) > 0
    order by id;
$$;

revoke all on function public.drivenext_find_cars(text) from public;
grant execute on function public.drivenext_find_cars(text) to anon, authenticated;

create table if not exists public.drivenext_profiles (
    user_id uuid primary key references auth.users(id) on delete cascade,
    surname text not null,
    first_name text not null,
    patronymic text not null default '',
    birth text not null,
    gender text not null,
    licence text not null,
    issued text not null
);

alter table public.drivenext_profiles enable row level security;
revoke all on public.drivenext_profiles from anon, authenticated;
grant select, insert on public.drivenext_profiles to authenticated;
drop policy if exists "DriveNext read own profile" on public.drivenext_profiles;
create policy "DriveNext read own profile" on public.drivenext_profiles
    for select to authenticated using ((select auth.uid()) = user_id);
drop policy if exists "DriveNext create own profile" on public.drivenext_profiles;
create policy "DriveNext create own profile" on public.drivenext_profiles
    for insert to authenticated with check ((select auth.uid()) = user_id);

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('drivenext-private', 'drivenext-private', false, 10485760, array['image/jpeg'])
on conflict (id) do update
set public = excluded.public, file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "DriveNext own photos" on storage.objects;
create policy "DriveNext own photos" on storage.objects
    for all to authenticated
    using (bucket_id = 'drivenext-private' and (storage.foldername(name))[1] = (select auth.uid())::text)
    with check (bucket_id = 'drivenext-private' and (storage.foldername(name))[1] = (select auth.uid())::text);

create extension if not exists btree_gist with schema extensions;

create table if not exists public.drivenext_bookings (
    id bigint generated always as identity primary key,
    user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
    car_id integer not null references public.drivenext_cars(id),
    start_date date not null,
    end_date date not null check (end_date > start_date),
    constraint drivenext_no_overlap exclude using gist
        (car_id with =, (daterange(start_date, end_date, '[)')) with &&)
);

alter table public.drivenext_bookings enable row level security;
revoke all on public.drivenext_bookings from anon, authenticated;
grant select on public.drivenext_bookings to authenticated;
grant insert (car_id, start_date, end_date) on public.drivenext_bookings to authenticated;
drop policy if exists "DriveNext read own bookings" on public.drivenext_bookings;
create policy "DriveNext read own bookings" on public.drivenext_bookings
    for select to authenticated using ((select auth.uid()) = user_id);
drop policy if exists "DriveNext create own booking" on public.drivenext_bookings;
create policy "DriveNext create own booking" on public.drivenext_bookings
    for insert to authenticated with check ((select auth.uid()) = user_id);

commit;
