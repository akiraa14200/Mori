-- Keep legacy related-video URLs whose title records were removed from the catalogue.
alter table public.related_videos
  alter column title_id drop not null,
  add column if not exists legacy_title_id text;

create unique index if not exists related_videos_legacy_title_url_uidx
  on public.related_videos (legacy_title_id, video_url)
  where title_id is null and legacy_title_id is not null;

do $$
declare
  settings_value jsonb;
  title_key text;
  video_value jsonb;
  v_url text;
  platform_value text;
begin
  select value into settings_value
  from public.site_settings
  where key = 'related_videos_by_title'
  limit 1;

  if settings_value is null or jsonb_typeof(settings_value) <> 'object' then
    return;
  end if;

  for title_key, video_value in
    select key, value from jsonb_each(settings_value)
  loop
    if jsonb_typeof(video_value) <> 'array' then
      continue;
    end if;

    if exists (select 1 from public.titles t where t.id::text = title_key) then
      continue;
    end if;

    for v_url in select jsonb_array_elements_text(video_value)
    loop
      platform_value := case
        when v_url ~* '(instagram[.]com|instagr[.]am)' then 'instagram'
        when v_url ~* '(dailymotion[.]com|dai[.]ly)' then 'dailymotion'
        when v_url ~* 'tiktok[.]com' then 'tiktok'
        else 'youtube'
      end;

      insert into public.related_videos
        (title_id, legacy_title_id, video_url, platform, action_type, action_url)
      values
        (null, title_key, v_url, platform_value, 'watch', v_url)
      on conflict (legacy_title_id, video_url)
        where title_id is null and legacy_title_id is not null
      do nothing;
    end loop;
  end loop;
end $$;
