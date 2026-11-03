# Catalogue, video delivery, and collapsible soundtrack design

## Intent
Improve RykoCollection's catalogue and video delivery so the public site stays responsive as entries and clips grow, without losing existing catalogue data, provider imports, admin editing, or saved Apple Music previews.

## Goals
- Keep Supabase as the source of truth and the existing Cloudflare D1 Worker as a public read cache.
- Load catalogue data in bounded pages, with a safe direct-Supabase fallback and a browse "Load more" path.
- Move related videos out of the single `site_settings.related_videos_by_title` JSON document into a normalized `related_videos` table.
- Migrate all existing related-video URLs without dropping them; retain the legacy setting as a rollback source until verification is complete.
- Load the Videos feed in pages; keep embeds click-to-load and thumbnails lazy.
- Keep entry trailers and related videos as placeholders until clicked.
- Make entry music collapsed by default, showing the track name; expand on click and never autoplay.
- Use short-lived public cache headers; admin edits continue to use Supabase directly.

## Non-goals
- No manga chapter hosting or video hosting.
- No replacement of AniList, Jikan, Kitsu, MangaUpdates, Wikidata, OMDb, TVmaze, or other metadata providers.
- No removal or rewrite of the existing admin workspace.
- No destructive deletion of the legacy video JSON during rollout.

## Constraints
- Current frontend is a single-file vanilla JavaScript app in `index.html`.
- Supabase project is `moicrqcddmftjgwjqkvz`; current public Worker URL is `https://mori-data.akiraa2007.workers.dev` (verify the configured hostname in `config.js` before wiring it).
- The Worker D1 API already supports `/api/titles?limit=&offset=&q=&media_type=` and `/api/characters`.
- Public video reads are allowed; writes must remain admin-only through RLS.
- Existing saved data includes 8 title keys with 13 related-video URLs and 3 entry soundtrack records. Preserve all.
- Existing soundtrack metadata is stored under `site_settings.entry_music`; do not migrate or rewrite it.

## Approaches considered
1. Keep all catalogue/video records in browser-loaded JSON settings: simplest, but the whole dataset grows on every read/write.
2. Move videos to normalized Supabase rows and use existing D1 for bounded catalogue reads: recommended, because it builds on deployed infrastructure and keeps Supabase authoritative.
3. Rebuild the whole app/backend around a new CMS or database: unnecessary risk and larger migration surface.

## Chosen approach
Use option 2. Keep a compatibility fallback to legacy JSON for reads during rollout, migrate URLs idempotently, and only retire legacy reads after database verification. Public catalogue requests use the Worker with direct Supabase fallback; admin flows continue reading/writing Supabase. Videos feed and per-entry clips use paginated Supabase queries and click-to-load embeds. The music header remains visible while the detailed player is collapsed until expanded.

## Acceptance criteria
- All 13 legacy video URLs appear in the new table exactly once and remain visible.
- Admin can add, edit, reorder, and remove a title's related videos; non-admin writes are rejected by RLS.
- Videos feed initially renders a bounded page and loads subsequent pages without duplicates.
- Public catalogue initial request is bounded; Browse can request more entries; if Worker fails, direct Supabase fallback keeps the site usable.
- Search/provider import, admin CRUD, entry details, and saved soundtrack records remain intact.
- Soundtrack is collapsed on initial entry render, shows its title, expands on click, and does not autoplay.
- Video iframes are still created only after an explicit play click.
