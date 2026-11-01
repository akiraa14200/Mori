-- Security and performance hardening for RykoCollection.
-- Apply only after reviewing this migration against the target Supabase project.

-- Public visitors only need read access to catalogue and public community data.
REVOKE INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER
ON ALL TABLES IN SCHEMA public FROM anon;

GRANT SELECT ON TABLE
  public.titles,
  public.characters,
  public.community_comments,
  public.profiles,
  public.community_comment_likes,
  public.reader_follows,
  public.site_settings,
  public.title_reviews
TO anon;

-- Keep authenticated users governed by the existing row-level policies,
-- but remove table-level capabilities that ordinary app users never need.
REVOKE TRUNCATE, REFERENCES, TRIGGER
ON ALL TABLES IN SCHEMA public FROM authenticated;

-- Trigger functions are invoked by PostgreSQL triggers, not by API clients.
REVOKE ALL ON FUNCTION public.notify_library_chapter_updates()
FROM PUBLIC, anon, authenticated;

-- This RPC is intentionally available to signed-in users because its body
-- performs an explicit auth.uid() + admin_users authorization check.
REVOKE ALL ON FUNCTION public.admin_storage_usage()
FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.admin_storage_usage()
TO authenticated, service_role;

-- Optimize notification RLS predicates so auth.uid() is evaluated once per query.
DROP POLICY IF EXISTS "Readers can create own notifications" ON public.reader_notifications;
CREATE POLICY "Readers can create own notifications"
ON public.reader_notifications
FOR INSERT TO authenticated
WITH CHECK ((SELECT auth.uid()) = user_id);

DROP POLICY IF EXISTS "Readers can mark own notifications read" ON public.reader_notifications;
CREATE POLICY "Readers can mark own notifications read"
ON public.reader_notifications
FOR UPDATE TO authenticated
USING ((SELECT auth.uid()) = user_id)
WITH CHECK ((SELECT auth.uid()) = user_id);

DROP POLICY IF EXISTS "Readers can view own notifications" ON public.reader_notifications;
CREATE POLICY "Readers can view own notifications"
ON public.reader_notifications
FOR SELECT TO authenticated
USING ((SELECT auth.uid()) = user_id);

-- Cover the existing reader_notifications.title_id foreign key.
CREATE INDEX IF NOT EXISTS reader_notifications_title_id_idx
ON public.reader_notifications (title_id);
