-- Preserve existing data while supporting anime airing status and optimizing follower lookups.
ALTER TABLE public.titles
  DROP CONSTRAINT IF EXISTS titles_status_check;

ALTER TABLE public.titles
  ADD CONSTRAINT titles_status_check
  CHECK (status IN ('Airing', 'Ongoing', 'Completed', 'Hiatus', 'Upcoming', 'Cancelled'));

CREATE INDEX IF NOT EXISTS reader_follows_followed_id_idx
  ON public.reader_follows(followed_id);
