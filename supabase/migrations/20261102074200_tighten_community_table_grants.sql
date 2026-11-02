revoke all on table public.community_posts from anon, authenticated;
grant select on table public.community_posts to anon;
grant select, insert, update, delete on table public.community_posts to authenticated;

revoke all on table public.community_post_likes from anon, authenticated;
grant select on table public.community_post_likes to anon;
grant select, insert, delete on table public.community_post_likes to authenticated;