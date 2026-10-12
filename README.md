# Mori

Mori is a responsive manga, manhwa, manhua, anime and light-novel catalogue with Supabase authentication, reader profiles, a synced library, character pages, reviews, community comments, image/GIF attachments, news and an admin catalogue.

## Live deployment

- Production site: https://mori-z-hub.vercel.app
- Repository: https://github.com/akiraa14200/Mori
- Supabase project: https://supabase.com/dashboard/project/moicrqcddmftjgwjqkvz

Vercel is connected to the GitHub repository and deploys changes pushed to `main`. GitHub Actions runs syntax/configuration checks on each push; Vercel is the deployment target.

## Supabase connection

The browser reads the public project URL and publishable key from `config.js`. A publishable key is expected to be visible in browser code; **never put a Supabase service-role/secret key in this repository or frontend**.

The live Supabase project already contains the catalogue, character, profile, library, review, progress, community and settings tables, RLS policies, and the `community-media` and `avatars` public image buckets.

For a new Supabase project, run `supabase/schema.sql` in the SQL Editor. It is a re-runnable bootstrap schema for the site's tables, row-level security policies, storage buckets and admin storage usage function.

## Make an administrator

1. Create an account on the site and confirm the email if Supabase requires it.
2. Open Supabase → Authentication → Users and copy that account's user UUID.
3. In SQL Editor, run the following with the actual UUID:

   ```sql
   insert into public.admin_users(user_id)
   values ('YOUR_AUTH_USER_UUID')
   on conflict (user_id) do nothing;
   ```

4. Sign in again or open the Admin page and choose **Check access again**.

Admin permissions are checked against `public.admin_users`; adding an email to frontend configuration does not grant administrator privileges.

## Hybrid database: Supabase + Cloudflare D1

Mori now uses Cloudflare D1 as an additional read cache for public titles and character metadata while keeping Supabase Postgres as the source of truth for authentication, profiles, reading progress, saved library, comments, reviews, likes, reports, settings and administrator writes. Public catalogue reads try D1 first and fall back to Supabase if the cache is unavailable.

The live D1 database is `mori-hybrid-cache`; its Worker API is `https://mori-data.akiraa142007.workers.dev`. A scheduled Worker refreshes public catalogue rows every 15 minutes. The Worker stages each refresh before publishing titles and characters together, so failed fetches or staging writes do not partially replace the live cache; the `/health` endpoint reports the last successful sync and recorded scheduled-sync errors. D1 title and character APIs use validated, bounded `limit`/`offset` pagination. The browser fetches pages in stable order and falls back to Supabase ranges if D1 is unavailable. Character descriptions pass through `character-text.js`, with regression tests in `tests/character-text.test.js`. No private user data is copied to D1. See [Cloudflare hybrid setup](cloudflare/README.md), [Worker source](cloudflare/worker.js), [D1 schema](cloudflare/schema.sql), and [Wrangler config](cloudflare/wrangler.toml).

D1 is additional separate storage; it does not increase the quota of the Supabase project itself. Supabase remains canonical so cache lag or a D1 outage does not block sign-in, comments, profiles or admin writes.

## News

The `/api/news` Vercel function fetches RSS feeds server-side. Image extraction supports RSS media thumbnails, media content, enclosures, and lazy-loaded image attributes; broken image URLs get one proxy retry before the image is hidden.

## Troubleshooting

- **Catalogue empty:** check the Supabase URL/key in `config.js`, the browser console, and the `titles` table.
- **Admin access denied:** verify the signed-in user's UUID exists in `public.admin_users`.
- **Profile or comment upload fails:** confirm the `profiles` table, `avatars` / `community-media` buckets, and matching storage policies exist.
- **News unavailable:** open `/api/news` on the live site and check Vercel function logs; upstream RSS providers can temporarily fail.
- **Deployment:** check the latest Vercel deployment for the `main` branch and confirm its state is `READY`.

Never expose service-role keys or bypass row-level security to fix frontend permission errors.
