# Mori hybrid database (Supabase + Cloudflare D1)

## Design

- **Supabase Postgres remains the source of truth** for authentication, profiles, saved library, reading progress, comments, likes, reports, reviews, settings, and administrator writes.
- **Cloudflare D1 is a read-optimized replica/cache** for public catalogue titles and character metadata. The website reads these two public datasets from the Worker first, then falls back to Supabase if D1 is unavailable or empty.
- The Worker syncs public titles and characters every 15 minutes. Do not write catalogue changes directly to D1; they would be overwritten by the next sync.
- No private account/profile/community data is copied to D1.

## Live resources

- D1 database: `mori-hybrid-cache`
- D1 database ID: `9412f27c-b095-4bba-865b-325080af783f`
- Worker: `mori-data`
- Worker URL: https://mori-data.akiraa142007.workers.dev
- Health endpoint: https://mori-data.akiraa142007.workers.dev/health
- Titles API: https://mori-data.akiraa142007.workers.dev/api/titles
- Characters API: `/api/characters?title_id=UUID`

## Worker secrets

These are configured as Cloudflare Worker secrets and must never be committed to Git:

- `SUPABASE_KEY`: the project's public/publishable Supabase key used for public read-only sync.
- `SYNC_SECRET`: random token required for manual `POST /sync`.

## Deployment

From this folder, run:

```sh
npx wrangler deploy
```

Set secrets when provisioning a new environment:

```sh
npx wrangler secret put SUPABASE_KEY
npx wrangler secret put SYNC_SECRET
```

The existing live Worker already has these secrets configured. Do not replace them with placeholders.

## Data sync and verification

- Scheduled sync: every 15 minutes.
- `GET /health` reports cached row counts and the last successful sync.
- `GET /api/titles?limit=1000` returns catalogue rows with JSON arrays and booleans normalized for the existing frontend.
- `GET /api/characters?title_id=...` returns characters for a title.
- Manual sync uses `POST /sync` with the `X-Sync-Secret` header. Never call it from browser code.
- Sync fetches all source rows before applying the stale-row cleanup. Empty source results abort the run so a temporary upstream problem does not wipe the cache.

## Important

D1 is additional independent storage, not a way to increase the storage quota of Supabase itself. Keep all writes and user-specific data in Supabase unless a deliberate migration is designed. D1's SQLite SQL dialect differs from Postgres; do not run the Supabase schema against D1.
