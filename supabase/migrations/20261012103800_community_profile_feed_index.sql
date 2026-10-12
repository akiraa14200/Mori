-- Speed up public reader profile feeds sorted newest-first.
CREATE INDEX IF NOT EXISTS community_comments_user_created_idx ON public.community_comments(user_id, created_at DESC);
