-- 7Manga phase 2/3: query indexes and stable RLS evaluation.
-- Safe, additive performance changes; no user data is deleted.
create index if not exists community_comment_reports_reporter_id_idx
  on public.community_comment_reports (reporter_id);
create index if not exists community_comments_user_id_idx
  on public.community_comments (user_id);
create index if not exists reading_progress_title_id_idx
  on public.reading_progress (title_id);
create index if not exists user_library_title_id_idx
  on public.user_library (title_id);

-- Remove confirmed exact duplicate indexes after inspecting pg_indexes.
drop index if exists public.titles_genres_idx;
drop index if exists public.titles_type_idx;

-- Cache auth.uid() once per statement instead of evaluating it for each row.
drop policy if exists community_comment_likes_insert_self on public.community_comment_likes;
create policy community_comment_likes_insert_self on public.community_comment_likes
  for insert to authenticated with check ((select auth.uid()) = user_id);
drop policy if exists community_comment_likes_delete_self on public.community_comment_likes;
create policy community_comment_likes_delete_self on public.community_comment_likes
  for delete to authenticated using ((select auth.uid()) = user_id);

drop policy if exists community_comment_reports_insert_self on public.community_comment_reports;
create policy community_comment_reports_insert_self on public.community_comment_reports
  for insert to authenticated with check ((select auth.uid()) = reporter_id);
drop policy if exists community_comment_reports_read_admin on public.community_comment_reports;
create policy community_comment_reports_read_admin on public.community_comment_reports
  for select to authenticated using (exists (select 1 from public.admin_users a where a.user_id = (select auth.uid())));
drop policy if exists community_comment_reports_update_admin on public.community_comment_reports;
create policy community_comment_reports_update_admin on public.community_comment_reports
  for update to authenticated
  using (exists (select 1 from public.admin_users a where a.user_id = (select auth.uid())))
  with check (exists (select 1 from public.admin_users a where a.user_id = (select auth.uid())));

drop policy if exists "Signed-in users post own comments" on public.community_comments;
create policy "Signed-in users post own comments" on public.community_comments
  for insert to authenticated with check ((select auth.uid()) = user_id);
drop policy if exists "Users delete own comments or admins" on public.community_comments;
create policy "Users delete own comments or admins" on public.community_comments
  for delete to authenticated using (
    (select auth.uid()) = user_id
    or exists (select 1 from public.admin_users a where a.user_id = (select auth.uid()))
  );
drop policy if exists "Users update own comments or admins" on public.community_comments;
create policy "Users update own comments or admins" on public.community_comments
  for update to authenticated
  using ((select auth.uid()) = user_id or exists (select 1 from public.admin_users a where a.user_id = (select auth.uid())))
  with check ((select auth.uid()) = user_id or exists (select 1 from public.admin_users a where a.user_id = (select auth.uid())));

drop policy if exists "Users delete own progress" on public.reading_progress;
create policy "Users delete own progress" on public.reading_progress
  for delete to authenticated using ((select auth.uid()) = user_id);
drop policy if exists "Users insert own progress" on public.reading_progress;
create policy "Users insert own progress" on public.reading_progress
  for insert to authenticated with check ((select auth.uid()) = user_id);
drop policy if exists "Users read own progress" on public.reading_progress;
create policy "Users read own progress" on public.reading_progress
  for select to authenticated using ((select auth.uid()) = user_id);
drop policy if exists "Users update own progress" on public.reading_progress;
create policy "Users update own progress" on public.reading_progress
  for update to authenticated using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

drop policy if exists "Admins manage site settings" on public.site_settings;
create policy "Admins manage site settings" on public.site_settings
  for all to authenticated
  using (exists (select 1 from public.admin_users a where a.user_id = (select auth.uid())))
  with check (exists (select 1 from public.admin_users a where a.user_id = (select auth.uid())));

drop policy if exists "Public read visible reviews" on public.title_reviews;
create policy "Public read visible reviews" on public.title_reviews
  for select to anon, authenticated
  using (is_hidden = false or (select auth.uid()) = user_id
    or exists (select 1 from public.admin_users a where a.user_id = (select auth.uid())));
drop policy if exists "Users create own reviews" on public.title_reviews;
create policy "Users create own reviews" on public.title_reviews
  for insert to authenticated with check ((select auth.uid()) = user_id and is_hidden = false);
drop policy if exists "Users delete own reviews or admins" on public.title_reviews;
create policy "Users delete own reviews or admins" on public.title_reviews
  for delete to authenticated using (
    (select auth.uid()) = user_id
    or exists (select 1 from public.admin_users a where a.user_id = (select auth.uid()))
  );
drop policy if exists "Users update own reviews" on public.title_reviews;
create policy "Users update own reviews" on public.title_reviews
  for update to authenticated
  using ((select auth.uid()) = user_id or exists (select 1 from public.admin_users a where a.user_id = (select auth.uid())))
  with check ((select auth.uid()) = user_id or exists (select 1 from public.admin_users a where a.user_id = (select auth.uid())));

drop policy if exists "Users add to own library" on public.user_library;
create policy "Users add to own library" on public.user_library
  for insert to authenticated with check ((select auth.uid()) = user_id);
drop policy if exists "Users read own library" on public.user_library;
create policy "Users read own library" on public.user_library
  for select to authenticated using ((select auth.uid()) = user_id);
drop policy if exists "Users remove from own library" on public.user_library;
create policy "Users remove from own library" on public.user_library
  for delete to authenticated using ((select auth.uid()) = user_id);
