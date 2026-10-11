-- 7manga Supabase schema. Run in SQL Editor.
create extension if not exists pgcrypto;
create table if not exists public.titles (
 id uuid primary key default gen_random_uuid(),
 name text not null,
 alternative_names text[] not null default '{}',
 media_type text not null check (media_type in ('Manga','Manhwa','Manhua','Anime','Light Novel','Web Novel')),
 synopsis text not null default '',
 cover_url text not null default '',
 banner_url text not null default '',
 genres text[] not null default '{}',
 themes text[] not null default '{}',
 status text not null default 'Ongoing' check (status in ('Ongoing','Completed','Hiatus','Upcoming','Cancelled')),
 release_year integer,
 season text,
 rating numeric(3,1) check (rating is null or (rating >= 0 and rating <= 10)),
 popularity integer not null default 0,
 chapters text,
 episodes text,
 author text,
 artist text,
 studio text,
 country text,
 source_url text,
 is_trending boolean not null default false,
 is_featured boolean not null default false,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now()
);
create table if not exists public.characters (
 id uuid primary key default gen_random_uuid(),
 title_id uuid not null references public.titles(id) on delete cascade,
 name text not null,
 role text not null default 'Supporting',
 description text not null default '',
 image_url text not null default '',
 created_at timestamptz not null default now()
);
create table if not exists public.admin_users (
 user_id uuid primary key references auth.users(id) on delete cascade,
 created_at timestamptz not null default now()
);
create index if not exists titles_name_search_idx on public.titles using gin(to_tsvector('simple',name));
create index if not exists titles_type_idx on public.titles(media_type);
create index if not exists titles_status_idx on public.titles(status);
create index if not exists titles_genres_idx on public.titles using gin(genres);
alter table public.titles enable row level security;
alter table public.characters enable row level security;
alter table public.admin_users enable row level security;
drop policy if exists "Public can read titles" on public.titles;
create policy "Public can read titles" on public.titles for select using (true);
drop policy if exists "Public can read characters" on public.characters;
create policy "Public can read characters" on public.characters for select using (true);
drop policy if exists "Users can read own admin membership" on public.admin_users;
create policy "Users can read own admin membership" on public.admin_users for select to authenticated using ((select auth.uid())=user_id);
drop policy if exists "Admins can insert titles" on public.titles;
create policy "Admins can insert titles" on public.titles for insert to authenticated with check (exists(select 1 from public.admin_users a where a.user_id=(select auth.uid())));
drop policy if exists "Admins can update titles" on public.titles;
create policy "Admins can update titles" on public.titles for update to authenticated using (exists(select 1 from public.admin_users a where a.user_id=(select auth.uid()))) with check (exists(select 1 from public.admin_users a where a.user_id=(select auth.uid())));
drop policy if exists "Admins can delete titles" on public.titles;
create policy "Admins can delete titles" on public.titles for delete to authenticated using (exists(select 1 from public.admin_users a where a.user_id=(select auth.uid())));
drop policy if exists "Admins can insert characters" on public.characters;
create policy "Admins can insert characters" on public.characters for insert to authenticated with check (exists(select 1 from public.admin_users a where a.user_id=(select auth.uid())));
drop policy if exists "Admins can update characters" on public.characters;
create policy "Admins can update characters" on public.characters for update to authenticated using (exists(select 1 from public.admin_users a where a.user_id=(select auth.uid()))) with check (exists(select 1 from public.admin_users a where a.user_id=(select auth.uid())));
drop policy if exists "Admins can delete characters" on public.characters;
create policy "Admins can delete characters" on public.characters for delete to authenticated using (exists(select 1 from public.admin_users a where a.user_id=(select auth.uid())));
grant select on public.titles,public.characters to anon,authenticated;
grant insert,update,delete on public.titles,public.characters to authenticated;
grant select on public.admin_users to authenticated;
-- After signing up, grant admin access manually from SQL Editor:
-- insert into public.admin_users(user_id) values ('YOUR_AUTH_USER_UUID');
