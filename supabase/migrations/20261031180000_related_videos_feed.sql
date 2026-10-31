-- Structured related-video records for the global Videos feed.
alter table public.titles add column if not exists read_url text;
create table if not exists public.related_videos (
  id uuid primary key default gen_random_uuid(),
  title_id uuid not null references public.titles(id) on delete cascade,
  video_url text not null check (video_url ~* '^https?://'),
  platform text not null check (platform in ('youtube','instagram','dailymotion')),
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

-- Preserve legacy per-title URLs and move them into the normalized feed table.
-- Existing legacy records have no creator metadata, so those fields remain blank until edited.
do $$
declare
  settings_value jsonb;
  title_key text;
  video_value jsonb;
  video_url text;
  platform_value text;
begin
  select value into settings_value from public.site_settings where key = 'related_videos_by_title' limit 1;
  if settings_value is null or jsonb_typeof(settings_value) <> 'object' then return; end if;
  for title_key, video_value in select key, value from jsonb_each(settings_value) loop
    if jsonb_typeof(video_value) <> 'array' then continue; end if;
    for video_url in select jsonb_array_elements_text(video_value) loop
      platform_value := case
        when video_url ~* '(instagram\\.com|instagr\\.am)' then 'instagram'
        when video_url ~* '(dailymotion\\.com|dai\\.ly)' then 'dailymotion'
        else 'youtube'
      end;
      insert into public.related_videos (title_id, video_url, platform, action_type, action_url)
      select t.id, video_url, platform_value, case when coalesce(t.read_url,'') <> '' then 'read' else 'watch' end, coalesce(nullif(t.read_url,''), video_url)
      from public.titles t
      where t.id::text = title_key
        and not exists (select 1 from public.related_videos rv where rv.title_id=t.id and rv.video_url=video_url);
    end loop;
  end loop;
end $$;
