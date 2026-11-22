-- What's in your Storage? : run this once in Supabase (SQL Editor → New query → paste → Run).
-- Safe to run again after an update.
--
-- Everyone signs in with their email (a 6-digit code, no password).
-- Each "storage" is a separate set of bins: your own, or one shared with your household.
-- Whoever creates a storage gets a join code to share; anyone with the code can join it.

-- An older version of this app kept every bin in one shared list. Keep that data, out of the way.
do $$ begin
  if exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = 'bins')
     and not exists (select 1 from information_schema.columns where table_schema = 'public' and table_name = 'bins' and column_name = 'space_id') then
    alter table public.bins rename to bins_old_v1;
  end if;
end $$;

-- ---------- storages and who is in them ----------
create table if not exists public.spaces (
  id          uuid primary key default gen_random_uuid(),
  name        text not null,
  join_code   text not null unique,
  created_by  uuid default auth.uid(),
  created_at  timestamptz not null default now()
);

create table if not exists public.space_members (
  space_id   uuid not null references public.spaces on delete cascade,
  user_id    uuid not null references auth.users on delete cascade,
  email      text,
  role       text not null default 'member',   -- 'owner' or 'member'
  joined_at  timestamptz not null default now(),
  primary key (space_id, user_id)
);

-- ---------- bins: one row per bin, inside a storage ----------
create table if not exists public.bins (
  space_id    uuid    not null references public.spaces on delete cascade,
  id          text    not null,
  data        jsonb   not null,
  deleted     boolean not null default false,
  updated_at  bigint  not null,
  updated_by  uuid    default auth.uid(),
  primary key (space_id, id)
);

-- ---------- who can see what ----------
create or replace function public.is_member(s uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.space_members where space_id = s and user_id = auth.uid());
$$;

alter table public.spaces        enable row level security;
alter table public.space_members enable row level security;
alter table public.bins          enable row level security;

drop policy if exists "members read"   on public.spaces;
drop policy if exists "owners rename"  on public.spaces;
create policy "members read"  on public.spaces for select to authenticated using (public.is_member(id));
create policy "owners rename" on public.spaces for update to authenticated
  using (exists (select 1 from public.space_members m where m.space_id = id and m.user_id = auth.uid() and m.role = 'owner'));

drop policy if exists "members see members" on public.space_members;
drop policy if exists "leave"               on public.space_members;
create policy "members see members" on public.space_members for select to authenticated using (public.is_member(space_id));
create policy "leave"               on public.space_members for delete to authenticated using (user_id = auth.uid());

drop policy if exists "members read"   on public.bins;
drop policy if exists "members insert" on public.bins;
drop policy if exists "members update" on public.bins;
create policy "members read"   on public.bins for select to authenticated using (public.is_member(space_id));
create policy "members insert" on public.bins for insert to authenticated with check (public.is_member(space_id));
create policy "members update" on public.bins for update to authenticated using (public.is_member(space_id)) with check (public.is_member(space_id));

-- ---------- create / join a storage ----------
create or replace function public.new_join_code() returns text
language plpgsql as $$
declare
  chars text := 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';   -- no 0/O, 1/I/L
  c text;
begin
  loop
    c := '';
    for i in 1..6 loop c := c || substr(chars, 1 + floor(random() * length(chars))::int, 1); end loop;
    exit when not exists (select 1 from public.spaces where join_code = c);
  end loop;
  return c;
end $$;

create or replace function public.create_space(space_name text) returns public.spaces
language plpgsql security definer set search_path = public as $$
declare s public.spaces;
begin
  if auth.uid() is null then raise exception 'Sign in first'; end if;
  insert into public.spaces (name, join_code, created_by)
    values (coalesce(nullif(trim(space_name), ''), 'My storage'), public.new_join_code(), auth.uid())
    returning * into s;
  insert into public.space_members (space_id, user_id, email, role)
    values (s.id, auth.uid(), auth.jwt() ->> 'email', 'owner');
  return s;
end $$;

create or replace function public.join_space(code text) returns public.spaces
language plpgsql security definer set search_path = public as $$
declare s public.spaces;
begin
  if auth.uid() is null then raise exception 'Sign in first'; end if;
  select * into s from public.spaces where join_code = upper(replace(trim(code), ' ', ''));
  if s.id is null then raise exception 'No storage has that code'; end if;
  insert into public.space_members (space_id, user_id, email, role)
    values (s.id, auth.uid(), auth.jwt() ->> 'email', 'member')
    on conflict (space_id, user_id) do nothing;
  return s;
end $$;

-- Owners can make a fresh code (the old one stops working).
create or replace function public.new_space_code(s uuid) returns text
language plpgsql security definer set search_path = public as $$
declare c text;
begin
  if not exists (select 1 from public.space_members where space_id = s and user_id = auth.uid() and role = 'owner') then
    raise exception 'Only the owner can change the code';
  end if;
  c := public.new_join_code();
  update public.spaces set join_code = c where id = s;
  return c;
end $$;

revoke all on function public.new_join_code() from public, anon, authenticated;
grant execute on function public.create_space(text)  to authenticated;
grant execute on function public.join_space(text)    to authenticated;
grant execute on function public.new_space_code(uuid) to authenticated;

-- Live updates: other phones see changes within a second or two.
do $$ begin
  alter publication supabase_realtime add table public.bins;
exception when duplicate_object then null; end $$;
