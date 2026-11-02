alter table public.profiles
  add column if not exists favorite_genres text[] not null default '{}'::text[];

alter table public.profiles
  alter column public_lists set default true;

drop policy if exists "Public profiles are readable" on public.profiles;
create policy "Public profiles or owner can read"
on public.profiles for select
to anon, authenticated
using (public_lists = true or id = (select auth.uid()));

drop policy if exists "Reader follows are visible" on public.reader_follows;
create policy "Reader follows visible for public profiles or participants"
on public.reader_follows for select
to anon, authenticated
using (
  follower_id = (select auth.uid())
  or followed_id = (select auth.uid())
  or exists (
    select 1 from public.profiles p
    where p.id = followed_id and p.public_lists = true
  )
);

drop policy if exists "Users read own library" on public.user_library;
create policy "Users read own or public libraries"
on public.user_library for select
to authenticated
using (
  user_id = (select auth.uid())
  or exists (
    select 1 from public.profiles p
    where p.id = user_id and p.public_lists = true
  )
);

drop policy if exists "Public read community comments" on public.community_comments;
create policy "Read public profile comments or own"
on public.community_comments for select
to anon, authenticated
using (
  user_id = (select auth.uid())
  or exists (
    select 1 from public.profiles p
    where p.id = user_id and p.public_lists = true
  )
);

drop policy if exists "community_comment_likes_read_all" on public.community_comment_likes;
create policy "Read likes on visible comments"
on public.community_comment_likes for select
to anon, authenticated
using (
  exists (
    select 1
    from public.community_comments c
    where c.id = comment_id
      and (
        c.user_id = (select auth.uid())
        or exists (
          select 1 from public.profiles p
          where p.id = c.user_id and p.public_lists = true
        )
      )
  )
);

create table if not exists public.community_posts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  body text not null default '',
  title_id uuid references public.titles(id) on delete set null,
  spoiler boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint community_posts_body_length check (char_length(body) <= 3000),
  constraint community_posts_body_or_title check (length(trim(body)) > 0 or title_id is not null)
);

create index if not exists community_posts_user_created_idx
  on public.community_posts(user_id, created_at desc);
create index if not exists community_posts_title_idx
  on public.community_posts(title_id) where title_id is not null;

alter table public.community_posts enable row level security;
grant select on public.community_posts to anon, authenticated;
grant insert, update, delete on public.community_posts to authenticated;

create policy "Community posts visible on public profiles"
on public.community_posts for select
to anon, authenticated
using (
  user_id = (select auth.uid())
  or exists (
    select 1 from public.profiles p
    where p.id = user_id and p.public_lists = true
  )
);

create policy "Users create own community posts"
on public.community_posts for insert
to authenticated
with check (user_id = (select auth.uid()));

create policy "Users update own community posts"
on public.community_posts for update
to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

create policy "Users delete own community posts"
on public.community_posts for delete
to authenticated
using (user_id = (select auth.uid()));

create table if not exists public.community_post_likes (
  post_id uuid not null references public.community_posts(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (post_id, user_id)
);

create index if not exists community_post_likes_user_idx
  on public.community_post_likes(user_id, created_at desc);

alter table public.community_post_likes enable row level security;
grant select on public.community_post_likes to anon, authenticated;
grant insert, delete on public.community_post_likes to authenticated;

create policy "Read likes on visible community posts"
on public.community_post_likes for select
to anon, authenticated
using (
  exists (
    select 1 from public.community_posts p
    where p.id = post_id
      and (
        p.user_id = (select auth.uid())
        or exists (
          select 1 from public.profiles pr
          where pr.id = p.user_id and pr.public_lists = true
        )
      )
  )
);

create policy "Users like visible community posts"
on public.community_post_likes for insert
to authenticated
with check (
  user_id = (select auth.uid())
  and exists (
    select 1 from public.community_posts p
    where p.id = post_id
      and p.user_id <> (select auth.uid())
      and (
        p.user_id = (select auth.uid())
        or exists (
          select 1 from public.profiles pr
          where pr.id = p.user_id and pr.public_lists = true
        )
      )
  )
);

create policy "Users remove own community post likes"
on public.community_post_likes for delete
to authenticated
using (user_id = (select auth.uid()));

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create or replace function private.notify_reader_follow()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare actor_name text;
begin
  if new.followed_id = new.follower_id then return new; end if;
  select coalesce(nullif(username,''), 'A reader') into actor_name
  from public.profiles where id = new.follower_id;
  insert into public.reader_notifications(user_id, kind, title, body, target_url, scheduled_at, dedupe_key)
  values (
    new.followed_id, 'follow', 'New follower',
    coalesce(actor_name,'A reader') || ' started following you.',
    '#/profile/' || coalesce(actor_name,'reader'),
    now(), 'follow:' || new.follower_id::text || ':' || new.followed_id::text
  )
  on conflict (user_id, dedupe_key) do nothing;
  return new;
end;
$$;

create or replace function private.notify_comment_reply()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare parent_user uuid; actor_name text;
begin
  if new.parent_comment_id is null then return new; end if;
  select user_id into parent_user from public.community_comments where id = new.parent_comment_id;
  if parent_user is null or parent_user = new.user_id then return new; end if;
  select coalesce(nullif(username,''), 'A reader') into actor_name
  from public.profiles where id = new.user_id;
  insert into public.reader_notifications(user_id, title_id, kind, title, body, target_url, scheduled_at, dedupe_key)
  values (
    parent_user, new.title_id, 'reply', 'New reply to your comment',
    coalesce(actor_name,'A reader') || ' replied to your comment.',
    '#/title/' || new.title_id::text, now(),
    'reply:' || new.parent_comment_id::text || ':' || new.id::text
  )
  on conflict (user_id, dedupe_key) do nothing;
  return new;
end;
$$;

create or replace function private.notify_comment_like()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare owner_id uuid; entry_id uuid; actor_name text;
begin
  select user_id, title_id into owner_id, entry_id
  from public.community_comments where id = new.comment_id;
  if owner_id is null or owner_id = new.user_id then return new; end if;
  select coalesce(nullif(username,''), 'A reader') into actor_name
  from public.profiles where id = new.user_id;
  insert into public.reader_notifications(user_id, title_id, kind, title, body, target_url, scheduled_at, dedupe_key)
  values (
    owner_id, entry_id, 'like', 'Someone liked your comment',
    coalesce(actor_name,'A reader') || ' liked your comment.',
    '#/title/' || entry_id::text, now(),
    'comment-like:' || new.comment_id::text || ':' || new.user_id::text || ':' || gen_random_uuid()::text
  );
  return new;
end;
$$;

create or replace function private.notify_community_post_like()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare owner_id uuid; entry_id uuid; owner_name text; actor_name text;
begin
  select user_id, title_id into owner_id, entry_id
  from public.community_posts where id = new.post_id;
  if owner_id is null or owner_id = new.user_id then return new; end if;
  select coalesce(nullif(username,''), 'reader') into owner_name
  from public.profiles where id = owner_id;
  select coalesce(nullif(username,''), 'A reader') into actor_name
  from public.profiles where id = new.user_id;
  insert into public.reader_notifications(user_id, title_id, kind, title, body, target_url, scheduled_at, dedupe_key)
  values (
    owner_id, entry_id, 'like', 'Someone liked your post',
    coalesce(actor_name,'A reader') || ' liked your community post.',
    case when entry_id is not null then '#/title/' || entry_id::text
         else '#/profile/' || coalesce(owner_name,'reader') end,
    now(), 'post-like:' || new.post_id::text || ':' || new.user_id::text || ':' || gen_random_uuid()::text
  );
  return new;
end;
$$;

revoke all on function private.notify_reader_follow() from public, anon, authenticated;
revoke all on function private.notify_comment_reply() from public, anon, authenticated;
revoke all on function private.notify_comment_like() from public, anon, authenticated;
revoke all on function private.notify_community_post_like() from public, anon, authenticated;

drop trigger if exists notify_reader_follow_insert on public.reader_follows;
create trigger notify_reader_follow_insert
after insert on public.reader_follows
for each row execute function private.notify_reader_follow();

drop trigger if exists notify_comment_reply_insert on public.community_comments;
create trigger notify_comment_reply_insert
after insert on public.community_comments
for each row execute function private.notify_comment_reply();

drop trigger if exists notify_comment_like_insert on public.community_comment_likes;
create trigger notify_comment_like_insert
after insert on public.community_comment_likes
for each row execute function private.notify_comment_like();

drop trigger if exists notify_community_post_like_insert on public.community_post_likes;
create trigger notify_community_post_like_insert
after insert on public.community_post_likes
for each row execute function private.notify_community_post_like();