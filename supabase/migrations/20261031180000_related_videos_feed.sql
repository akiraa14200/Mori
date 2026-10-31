-- Structured related-video records for the global Videos feed.
create table if not exists public.related_videos (
  id uuid primary key default gen_random_uuid(),
  title_id uuid not null references public.titles(id) on delete cascade,
  video_url text not null check (video_url ~* '^https?://'),
  platform text not null check (platform in ('youtube','instagram')),
  video_title text not null default '',
  creator_name text not null default '',
  creator_avatar_url text not null default '',
  action_type text not null default 'watch' check (action_type in ('read','watch')),
  action_url text not null default '',
  sort_order integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists related_videos_feed_idx
  on public.related_videos (sort_order asc, created_at desc);
create index if not exists related_videos_title_idx
  on public.related_videos (title_id, sort_order asc);

alter table public.related_videos enable row level security;

drop policy if exists "Public can read related videos" on public.related_videos;
create policy "Public can read related videos"
  on public.related_videos for select
  to anon, authenticated
  using (true);

drop policy if exists "Admins can insert related videos" on public.related_videos;
create policy "Admins can insert related videos"
  on public.related_videos for insert
  to authenticated
  with check (
    exists (select 1 from public.admin_users a where a.user_id = (select auth.uid()))
  );

drop policy if exists "Admins can update related videos" on public.related_videos;
create policy "Admins can update related videos"
  on public.related_videos for update
  to authenticated
  using (
    exists (select 1 from public.admin_users a where a.user_id = (select auth.uid()))
  )
  with check (
    exists (select 1 from public.admin_users a where a.user_id = (select auth.uid()))
  );

drop policy if exists "Admins can delete related videos" on public.related_videos;
create policy "Admins can delete related videos"
  on public.related_videos for delete
  to authenticated
  using (
    exists (select 1 from public.admin_users a where a.user_id = (select auth.uid()))
  );
