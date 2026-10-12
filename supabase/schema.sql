-- Mori / 7MANGA Supabase bootstrap schema.
-- Safe to re-run: creates missing objects and refreshes policies without deleting data.
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
 anilist_id integer,
 trailer_url text,
 is_trending boolean not null default false,
 is_featured boolean not null default false,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now()
);
alter table public.titles add column if not exists anilist_id integer;
alter table public.titles add column if not exists trailer_url text;

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

create table if not exists public.user_library (
 user_id uuid not null references auth.users(id) on delete cascade,
 title_id uuid not null references public.titles(id) on delete cascade,
 created_at timestamptz not null default now(),
 primary key (user_id,title_id)
);

create table if not exists public.profiles (
 id uuid primary key references auth.users(id) on delete cascade,
 username text not null unique check (char_length(username) between 2 and 32),
 display_name text not null default 'Reader' check (char_length(display_name) between 1 and 60),
 avatar_url text not null default '',
 updated_at timestamptz not null default now(),
 bio text not null default '' check (char_length(bio) <= 280),
 banner_url text not null default ''
);

create table if not exists public.site_settings (
 key text primary key,
 value jsonb not null default '{}'::jsonb,
 updated_at timestamptz not null default now()
);

create table if not exists public.reading_progress (
 user_id uuid not null references auth.users(id) on delete cascade,
 title_id uuid not null references public.titles(id) on delete cascade,
 reading_status text not null default 'Plan to Read' check (reading_status in ('Plan to Read','Reading','Completed','On Hold','Dropped')),
 current_chapter numeric not null default 0 check (current_chapter >= 0),
 notes text not null default '',
 updated_at timestamptz not null default now(),
 primary key (user_id,title_id)
);

create table if not exists public.title_reviews (
 id uuid primary key default gen_random_uuid(),
 user_id uuid not null references auth.users(id) on delete cascade,
 title_id uuid not null references public.titles(id) on delete cascade,
 rating integer not null check (rating between 1 and 10),
 review text not null default '' check (char_length(review) <= 3000),
 is_hidden boolean not null default false,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 unique (user_id,title_id)
);

create table if not exists public.community_comments (
 id uuid primary key default gen_random_uuid(),
 title_id uuid not null references public.titles(id) on delete cascade,
 user_id uuid not null references auth.users(id) on delete cascade,
 body text not null default '' check (char_length(body) <= 3000),
 media_url text,
 media_type text check (media_type is null or media_type in ('image','gif')),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 parent_comment_id uuid references public.community_comments(id) on delete cascade,
 constraint community_comments_has_content check (char_length(trim(body)) > 0 or media_url is not null)
);

create table if not exists public.community_comment_likes (
 comment_id uuid not null references public.community_comments(id) on delete cascade,
 user_id uuid not null references auth.users(id) on delete cascade,
 created_at timestamptz not null default now(),
 primary key (comment_id,user_id)
);

create table if not exists public.community_comment_reports (
 id uuid primary key default gen_random_uuid(),
 comment_id uuid not null references public.community_comments(id) on delete cascade,
 reporter_id uuid not null references auth.users(id) on delete cascade,
 reason text not null check (reason in ('Spam','Harassment','Spoilers','Inappropriate content','Other')),
 details text not null default '' check (char_length(details) <= 1000),
 status text not null default 'pending' check (status in ('pending','reviewed','dismissed')),
 created_at timestamptz not null default now(),
 unique (comment_id,reporter_id)
);

create index if not exists titles_name_search_idx on public.titles using gin(to_tsvector('simple',name));
create index if not exists titles_status_idx on public.titles(status);
create index if not exists characters_title_id_idx on public.characters(title_id);
create index if not exists community_comment_reports_reporter_id_idx on public.community_comment_reports(reporter_id);
create index if not exists community_comments_user_id_idx on public.community_comments(user_id);
create index if not exists reading_progress_title_id_idx on public.reading_progress(title_id);
create index if not exists user_library_title_id_idx on public.user_library(title_id);
create index if not exists comments_title_created_idx on public.community_comments(title_id,created_at desc);
create index if not exists comments_parent_id_idx on public.community_comments(parent_comment_id);
create index if not exists reviews_title_created_idx on public.title_reviews(title_id,created_at desc);

create or replace function public.set_7manga_updated_at()
returns trigger language plpgsql set search_path = public
as $$ begin new.updated_at = now(); return new; end; $$;

drop trigger if exists set_titles_updated_at on public.titles;
create trigger set_titles_updated_at before update on public.titles for each row execute function public.set_7manga_updated_at();
drop trigger if exists set_profiles_updated_at on public.profiles;
create trigger set_profiles_updated_at before update on public.profiles for each row execute function public.set_7manga_updated_at();
drop trigger if exists set_comments_updated_at on public.community_comments;
create trigger set_comments_updated_at before update on public.community_comments for each row execute function public.set_7manga_updated_at();
drop trigger if exists set_reviews_updated_at on public.title_reviews;
create trigger set_reviews_updated_at before update on public.title_reviews for each row execute function public.set_7manga_updated_at();
drop trigger if exists set_site_settings_updated_at on public.site_settings;
create trigger set_site_settings_updated_at before update on public.site_settings for each row execute function public.set_7manga_updated_at();

alter table public.titles enable row level security;
alter table public.characters enable row level security;
alter table public.admin_users enable row level security;
alter table public.user_library enable row level security;
alter table public.profiles enable row level security;
alter table public.site_settings enable row level security;
alter table public.reading_progress enable row level security;
alter table public.title_reviews enable row level security;
alter table public.community_comments enable row level security;
alter table public.community_comment_likes enable row level security;
alter table public.community_comment_reports enable row level security;

-- Catalogue and characters
drop policy if exists "Public can read titles" on public.titles;
create policy "Public can read titles" on public.titles for select using (true);
drop policy if exists "Admins can insert titles" on public.titles;
create policy "Admins can insert titles" on public.titles for insert to authenticated with check (exists(select 1 from public.admin_users a where a.user_id=(select auth.uid())));
drop policy if exists "Admins can update titles" on public.titles;
create policy "Admins can update titles" on public.titles for update to authenticated using (exists(select 1 from public.admin_users a where a.user_id=(select auth.uid()))) with check (exists(select 1 from public.admin_users a where a.user_id=(select auth.uid())));
drop policy if exists "Admins can delete titles" on public.titles;
create policy "Admins can delete titles" on public.titles for delete to authenticated using (exists(select 1 from public.admin_users a where a.user_id=(select auth.uid())));
drop policy if exists "Public can read characters" on public.characters;
create policy "Public can read characters" on public.characters for select using (true);
drop policy if exists "Admins can insert characters" on public.characters;
create policy "Admins can insert characters" on public.characters for insert to authenticated with check (exists(select 1 from public.admin_users a where a.user_id=(select auth.uid())));
drop policy if exists "Admins can update characters" on public.characters;
create policy "Admins can update characters" on public.characters for update to authenticated using (exists(select 1 from public.admin_users a where a.user_id=(select auth.uid()))) with check (exists(select 1 from public.admin_users a where a.user_id=(select auth.uid())));
drop policy if exists "Admins can delete characters" on public.characters;
create policy "Admins can delete characters" on public.characters for delete to authenticated using (exists(select 1 from public.admin_users a where a.user_id=(select auth.uid())));

-- Admin membership and user library
drop policy if exists "Users can read own admin membership" on public.admin_users;
create policy "Users can read own admin membership" on public.admin_users for select to authenticated using ((select auth.uid())=user_id);
drop policy if exists "Users read own library" on public.user_library;
create policy "Users read own library" on public.user_library for select to authenticated using (auth.uid()=user_id);
drop policy if exists "Users add to own library" on public.user_library;
create policy "Users add to own library" on public.user_library for insert to authenticated with check (auth.uid()=user_id);
drop policy if exists "Users remove from own library" on public.user_library;
create policy "Users remove from own library" on public.user_library for delete to authenticated using (auth.uid()=user_id);

-- Profiles
drop policy if exists "Public profiles are readable" on public.profiles;
create policy "Public profiles are readable" on public.profiles for select to anon,authenticated using (true);
drop policy if exists "Users create own profile" on public.profiles;
create policy "Users create own profile" on public.profiles for insert to authenticated with check ((select auth.uid())=id);
drop policy if exists "Users update own profile" on public.profiles;
create policy "Users update own profile" on public.profiles for update to authenticated using ((select auth.uid())=id) with check ((select auth.uid())=id);

-- Settings and reading progress
drop policy if exists "Public can read site settings" on public.site_settings;
create policy "Public can read site settings" on public.site_settings for select to anon,authenticated using (true);
drop policy if exists "Admins manage site settings" on public.site_settings;
create policy "Admins manage site settings" on public.site_settings for all to authenticated using (exists(select 1 from public.admin_users a where a.user_id=auth.uid())) with check (exists(select 1 from public.admin_users a where a.user_id=auth.uid()));
drop policy if exists "Users read own progress" on public.reading_progress;
create policy "Users read own progress" on public.reading_progress for select to authenticated using (auth.uid()=user_id);
drop policy if exists "Users insert own progress" on public.reading_progress;
create policy "Users insert own progress" on public.reading_progress for insert to authenticated with check (auth.uid()=user_id);
drop policy if exists "Users update own progress" on public.reading_progress;
create policy "Users update own progress" on public.reading_progress for update to authenticated using (auth.uid()=user_id) with check (auth.uid()=user_id);
drop policy if exists "Users delete own progress" on public.reading_progress;
create policy "Users delete own progress" on public.reading_progress for delete to authenticated using (auth.uid()=user_id);

-- Reviews
drop policy if exists "Public read visible reviews" on public.title_reviews;
create policy "Public read visible reviews" on public.title_reviews for select to anon,authenticated using (is_hidden=false or auth.uid()=user_id or exists(select 1 from public.admin_users a where a.user_id=auth.uid()));
drop policy if exists "Users create own reviews" on public.title_reviews;
create policy "Users create own reviews" on public.title_reviews for insert to authenticated with check (auth.uid()=user_id and is_hidden=false);
drop policy if exists "Users update own reviews" on public.title_reviews;
create policy "Users update own reviews" on public.title_reviews for update to authenticated using (auth.uid()=user_id or exists(select 1 from public.admin_users a where a.user_id=auth.uid())) with check (auth.uid()=user_id or exists(select 1 from public.admin_users a where a.user_id=auth.uid()));
drop policy if exists "Users delete own reviews or admins" on public.title_reviews;
create policy "Users delete own reviews or admins" on public.title_reviews for delete to authenticated using (auth.uid()=user_id or exists(select 1 from public.admin_users a where a.user_id=auth.uid()));

-- Comments, replies, likes and reports
drop policy if exists "Public read community comments" on public.community_comments;
create policy "Public read community comments" on public.community_comments for select to anon,authenticated using (true);
drop policy if exists "Signed-in users post own comments" on public.community_comments;
create policy "Signed-in users post own comments" on public.community_comments for insert to authenticated with check (auth.uid()=user_id);
drop policy if exists "Users update own comments or admins" on public.community_comments;
create policy "Users update own comments or admins" on public.community_comments for update to authenticated using (auth.uid()=user_id or exists(select 1 from public.admin_users a where a.user_id=auth.uid())) with check (auth.uid()=user_id or exists(select 1 from public.admin_users a where a.user_id=auth.uid()));
drop policy if exists "Users delete own comments or admins" on public.community_comments;
create policy "Users delete own comments or admins" on public.community_comments for delete to authenticated using (auth.uid()=user_id or exists(select 1 from public.admin_users a where a.user_id=auth.uid()));
drop policy if exists "community_comment_likes_read_all" on public.community_comment_likes;
create policy "community_comment_likes_read_all" on public.community_comment_likes for select using (true);
drop policy if exists "community_comment_likes_insert_self" on public.community_comment_likes;
create policy "community_comment_likes_insert_self" on public.community_comment_likes for insert to authenticated with check (auth.uid()=user_id);
drop policy if exists "community_comment_likes_delete_self" on public.community_comment_likes;
create policy "community_comment_likes_delete_self" on public.community_comment_likes for delete to authenticated using (auth.uid()=user_id);
drop policy if exists "community_comment_reports_insert_self" on public.community_comment_reports;
create policy "community_comment_reports_insert_self" on public.community_comment_reports for insert to authenticated with check (auth.uid()=reporter_id);
drop policy if exists "community_comment_reports_read_admin" on public.community_comment_reports;
create policy "community_comment_reports_read_admin" on public.community_comment_reports for select to authenticated using (exists(select 1 from public.admin_users a where a.user_id=auth.uid()));
drop policy if exists "community_comment_reports_update_admin" on public.community_comment_reports;
create policy "community_comment_reports_update_admin" on public.community_comment_reports for update to authenticated using (exists(select 1 from public.admin_users a where a.user_id=auth.uid())) with check (exists(select 1 from public.admin_users a where a.user_id=auth.uid()));

-- Public image buckets; uploads remain scoped to each signed-in user's folder.
insert into storage.buckets (id,name,public,file_size_limit,allowed_mime_types)
values ('community-media','community-media',true,8388608,array['image/jpeg','image/png','image/webp','image/gif']),
       ('avatars','avatars',true,8388608,array['image/jpeg','image/png','image/webp','image/gif'])
on conflict (id) do nothing;
drop policy if exists "Public read community media" on storage.objects;
create policy "Public read community media" on storage.objects for select to anon,authenticated using (bucket_id='community-media');
drop policy if exists "Signed-in upload community media" on storage.objects;
create policy "Signed-in upload community media" on storage.objects for insert to authenticated with check (bucket_id='community-media' and (storage.foldername(name))[1]=(auth.uid())::text);
drop policy if exists "Users delete own community media" on storage.objects;
create policy "Users delete own community media" on storage.objects for delete to authenticated using (bucket_id='community-media' and (storage.foldername(name))[1]=(auth.uid())::text);
drop policy if exists "Avatar files are public" on storage.objects;
create policy "Avatar files are public" on storage.objects for select to anon,authenticated using (bucket_id='avatars');
drop policy if exists "Users upload own avatar" on storage.objects;
create policy "Users upload own avatar" on storage.objects for insert to authenticated with check (bucket_id='avatars' and (storage.foldername(name))[1]=(select auth.uid())::text);
drop policy if exists "Users update own avatar" on storage.objects;
create policy "Users update own avatar" on storage.objects for update to authenticated using (bucket_id='avatars' and (storage.foldername(name))[1]=(select auth.uid())::text) with check (bucket_id='avatars' and (storage.foldername(name))[1]=(select auth.uid())::text);
drop policy if exists "Users delete own avatar" on storage.objects;
create policy "Users delete own avatar" on storage.objects for delete to authenticated using (bucket_id='avatars' and (storage.foldername(name))[1]=(select auth.uid())::text);

grant select on public.titles,public.characters,public.site_settings,public.profiles,public.community_comments,public.community_comment_likes to anon,authenticated;
grant select,insert,update,delete on public.titles,public.characters,public.user_library,public.profiles,public.site_settings,public.reading_progress,public.title_reviews,public.community_comments,public.community_comment_likes,public.community_comment_reports to authenticated;
grant select on public.admin_users to authenticated;

-- Admin-only storage report used by the dashboard.
create or replace function public.admin_storage_usage()
returns table(bucket_id text,file_count bigint,bytes_used bigint)
language plpgsql security definer set search_path = public
as $$
begin
 if auth.uid() is null or not exists (select 1 from public.admin_users a where a.user_id=auth.uid()) then
   raise exception using errcode='42501', message='Administrator access required';
 end if;
 return query
 select b.id::text,count(o.id)::bigint,
        coalesce(sum(case when (o.metadata->>'size') ~ '^[0-9]+$' then (o.metadata->>'size')::bigint else 0 end),0)::bigint
 from storage.buckets b left join storage.objects o on o.bucket_id=b.id
 group by b.id order by b.id;
end;
$$;
revoke all on function public.admin_storage_usage() from public;
grant execute on function public.admin_storage_usage() to authenticated;

-- To grant administrator access after signup:
-- insert into public.admin_users(user_id) values ('YOUR_AUTH_USER_UUID') on conflict do nothing;
