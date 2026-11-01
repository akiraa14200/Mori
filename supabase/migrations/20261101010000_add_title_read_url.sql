-- Keep the live catalogue schema aligned with the Read / Watch destination used by entry cards and admin forms.
ALTER TABLE public.titles
  ADD COLUMN IF NOT EXISTS read_url text;
