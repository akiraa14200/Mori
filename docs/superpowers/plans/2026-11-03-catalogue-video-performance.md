# Catalogue and Video Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bound catalogue and video data loading, normalize related-video storage, preserve current data/providers/admin behavior, and collapse entry soundtracks by default.

**Architecture:** Supabase remains the source of truth. Public catalogue reads use the existing Cloudflare D1 Worker in bounded pages with Supabase fallback; admin reads/writes remain direct. Related videos move to a normalized RLS-protected Supabase table, while the legacy JSON setting remains a fallback during rollout.

**Tech Stack:** Vanilla JavaScript in `index.html`, Supabase Postgres/RLS, Cloudflare Workers + D1, Node.js built-in test runner.

**Spec:** `docs/superpowers/specs/2026-11-03-catalogue-video-performance-design.md`

## Global Constraints
- Preserve the 8 existing title-keyed video groups / 14 URLs and 3 saved soundtrack records.
- Never expose Supabase service-role credentials or Worker sync secrets in the browser.
- Keep metadata provider importer and admin CRUD behavior intact.
- Public video reads are allowed; related-video writes require an `admin_users` row.
- Never instantiate video iframes until the visitor explicitly clicks Play.
- Soundtrack starts collapsed and never autoplays.

## Review Focus
- Worker unavailable or empty: direct Supabase fallback must still load titles.
- More than 250 titles: page boundaries must not skip or duplicate rows.
- Video URL duplicated, invalid, or unsupported: validation must avoid duplicate rows and reject unsafe URLs.
- Legacy JSON still exists or migration is retried: import must be idempotent and not duplicate clips.
- Admin save fails midway: existing video rows must not be lost.
- Entry navigation while soundtrack is expanded: player state must reset/collapse for the new entry.

---

### Task 1: Database normalization and compatibility migration

**Files:**
- Modify: `supabase/migrations/20261031180000_related_videos_feed.sql`
- Modify: `supabase/schema.sql`
- Test: `tests/related-videos-migration.test.js`

**Interfaces:**
- Produces `public.related_videos` with `id`, `title_id`, `video_url`, `platform`, `video_title`, `creator_name`, `creator_avatar_url`, `action_type`, `action_url`, `sort_order`, timestamps.
- RLS: public SELECT; authenticated INSERT/UPDATE/DELETE only when `auth.uid()` exists in `admin_users`.

- [ ] Add a test asserting table columns, RLS policy definitions, supported platform values, and idempotent legacy import.
- [ ] Run the test and verify it fails against the current migration.
- [ ] Update the migration to support YouTube, Instagram, Dailymotion, and TikTok; add a unique `(title_id, video_url)` index; preserve legacy JSON.
- [ ] Update schema snapshot consistently.
- [ ] Run migration test and inspect SQL.
- [ ] Apply the migration to Supabase, then query counts and sample URLs to verify all 14 existing URLs migrated, including 2 orphaned legacy IDs.

### Task 2: Related-video data access and admin editing

**Files:**
- Modify: `index.html`
- Test: `tests/related-videos-data.test.js`

**Interfaces:**
- `getRelatedVideosForTitle(titleId, options)` returns normalized rows ordered by `sort_order`.
- `saveRelatedVideosForTitle(titleId, urls)` validates and safely upserts retained/new URLs, then removes deleted rows only after successful upserts.
- Legacy JSON is read only as a fallback if normalized-table access reports a missing-table deployment state.

- [ ] Add tests for URL validation, deduplication, stable ordering, and save failure preserving existing data.
- [ ] Verify tests fail before implementation.
- [ ] Replace JSON read/modify/write with row queries while keeping the existing admin textarea and status messages.
- [ ] Keep creator/action metadata editable where existing admin form supports it; do not invent fields in the UI.
- [ ] Run tests and verify the 13 migrated URLs are visible through the data access helper.

### Task 3: Paginated Videos feed

**Files:**
- Modify: `index.html`
- Test: `tests/video-feed-pagination.test.js`

**Interfaces:**
- Initial feed page size: 24.
- Subsequent pages use Supabase `range(offset, offset + pageSize - 1)`, ordered by `sort_order` then `created_at`.
- A stable row ID is used for remove actions and deduplication.

- [ ] Add tests for 24-row page bounds, offset progression, duplicate prevention, and end-of-feed behavior.
- [ ] Verify tests fail before implementation.
- [ ] Render only the first 24 videos; add a Load more control that appends the next page.
- [ ] Preserve existing filters, recommendations, creator labels, remove action, and watched-interest signals.
- [ ] Keep click-to-load placeholders and lazy thumbnails.
- [ ] Run tests and inspect mobile layout.

### Task 4: Bounded catalogue reads through existing D1 Worker

**Files:**
- Modify: `index.html`
- Modify: `cloudflare/worker.js`
- Modify: `cloudflare/wrangler.toml`
- Test: `tests/catalogue-pagination.test.js`

**Interfaces:**
- Public Worker request: `GET /api/titles?limit=250&offset=0&q=&media_type=`.
- Frontend initial public page size: 250; next-page requests append without duplicate IDs.
- Admin continues to use direct Supabase reads for current edit visibility.
- If Worker fails, use the existing Supabase range query fallback.

- [ ] Add tests for bounded Worker page size, offset/query filtering, and cache headers.
- [ ] Verify tests fail before implementation.
- [ ] Wire the configured Worker URL into public catalogue loading.
- [ ] Add a Load more action to Browse when the server indicates a full page was returned.
- [ ] Preserve direct Supabase fallback and cached local catalogue recovery.
- [ ] Keep global search useful by querying the Worker when the local loaded index has no match.
- [ ] Run worker tests and frontend syntax checks.

### Task 5: Cache policy and admin freshness

**Files:**
- Modify: `cloudflare/worker.js`
- Test: `tests/catalogue-pagination.test.js`

- [ ] Test public cache headers and query-specific cache keys.
- [ ] Reduce Worker public cache lifetime to 60 seconds at the edge with a bounded stale-while-revalidate window.
- [ ] Ensure admin catalogue mutations remain direct Supabase writes and refresh local state immediately.
- [ ] Verify Worker cache does not mix search queries, media types, offsets, or limits.

### Task 6: Collapsible soundtrack

**Files:**
- Modify: `index.html`
- Test: `tests/entry-music-collapse.test.js`

- [ ] Add tests for collapsed initial markup, accessible expanded state, and no autoplay call during entry navigation.
- [ ] Verify tests fail before implementation.
- [ ] Keep a visible compact soundtrack row with the track name.
- [ ] Expand/collapse the player details on click; preserve the audio controls and Apple Music link.
- [ ] Remove automatic playback from entry navigation; users explicitly press Play in the native audio controls.
- [ ] Reset expanded state when changing entries.
- [ ] Run tests and inspect the entry markup.

### Task 7: End-to-end regression and release

**Files:**
- Modify: `.github/workflows/preview-syntax.yml` (only if needed to run the new tests)
- Test: all existing and new tests

- [ ] Run every Node test, provider smoke workflow checks, and syntax checks.
- [ ] Verify the database row count and confirm all 14 migrated or preserved URLs are present.
- [ ] Verify provider import function names and admin add/edit handlers remain in place.
- [ ] Deploy the branch to Vercel Preview; inspect build logs and test the preview routes.
- [ ] Only promote to production after the preview is verified.
