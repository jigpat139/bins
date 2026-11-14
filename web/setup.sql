-- Run this once in Supabase: SQL Editor → New query → paste → Run.

create table if not exists public.bins (
  id          text primary key,
  data        jsonb   not null,
  deleted     boolean not null default false,
  updated_at  bigint  not null,
  updated_by  uuid    default auth.uid()
);

alter table public.bins enable row level security;

-- Only signed-in members of your household can see or change bins.
-- (Turn off public sign-ups in Authentication → Sign In / Providers, and add people yourself.)
drop policy if exists "members read"   on public.bins;
drop policy if exists "members insert" on public.bins;
drop policy if exists "members update" on public.bins;
create policy "members read"   on public.bins for select to authenticated using (true);
create policy "members insert" on public.bins for insert to authenticated with check (true);
create policy "members update" on public.bins for update to authenticated using (true) with check (true);

-- Live updates: other phones see changes within a second or two.
do $$ begin
  alter publication supabase_realtime add table public.bins;
exception when duplicate_object then null; end $$;
