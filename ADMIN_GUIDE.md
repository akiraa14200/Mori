# Mori — admin and troubleshooting guide

Primary site: https://mori-z-hub.vercel.app  
Repository: https://github.com/akiraa14200/Mori  
Supabase dashboard: https://supabase.com/dashboard/project/moicrqcddmftjgwjqkvz

## 1. Create an administrator account

1. Open Mori and choose **Sign in** → **Create account**.
2. Confirm your email if Supabase requires it.
3. In Supabase, open **Authentication → Users** and copy your account's **User UID**.
4. Open **SQL Editor → New query** and run this query after replacing the placeholder:

   ```sql
   insert into public.admin_users (user_id)
   values ('YOUR_USER_UID')
   on conflict (user_id) do nothing;
   ```

5. Return to Mori, sign in, and open the Admin page. If access is not refreshed, tap **Check access again**.

Mori checks admin membership using the authenticated user's UUID in `public.admin_users`. An email address or frontend setting by itself does not grant database permissions.

## 2. Add or update a catalogue entry

1. Sign in with the administrator account.
2. Open **Admin** and choose **Add new entry**.
3. You can fill the fields manually or use **Import from AniList** to search by title or ID.
4. Review the imported title, media type, cover/banner URLs, synopsis, genres, status, year, staff, AniList ID and trailer URL. Correct any details that are missing or inaccurate.
5. Save the entry. Verify it appears in the catalogue and opens correctly.

The live database stores character records separately and links each one to a title. AniList imports may not provide every character detail, so do not assume missing values indicate a database failure.

## 3. Profiles and community uploads

Reader profiles are stored in `public.profiles`. Profile avatars use the public `avatars` bucket; comment attachments use `community-media`. Both buckets accept JPG, PNG, WebP and GIF files up to the configured 8 MB bucket limit; the comment form applies its own GIF size limit.

Uploads are scoped to a signed-in user's folder by Storage row-level security policies. If upload fails, check that the user is signed in and that the bucket and policies exist. Do not disable RLS to work around permission errors.

## 4. News and images

The news page requests `/api/news`, a Vercel serverless function that reads RSS feeds. Some publishers do not expose a thumbnail in their feed, and some remote image hosts block hotlinking. Mori now checks more RSS image attributes and retries a failed image through an image proxy once. Articles without a usable image may still appear without a thumbnail.

To troubleshoot:
- Open https://mori-z-hub.vercel.app/api/news and confirm it returns JSON with a `feeds` array.
- If it returns an error, check the Vercel function logs; upstream feeds can be temporarily unavailable.
- If JSON loads but a card has no image, that RSS item may not include a usable image URL.

## 5. Supabase schema and security

The live project uses RLS for catalogue writes, profiles, personal libraries, reading progress, reviews, comments, likes, reports and storage uploads. Public reads are intentionally enabled for public catalogue and community content.

For a new project, run `supabase/schema.sql` in Supabase SQL Editor. It now includes the application tables, RLS policies, image buckets and admin storage usage function. Re-running it creates missing objects and refreshes named policies without deleting catalogue data.

Never place a Supabase service-role/secret key in frontend code, and never disable RLS to fix a client-side error.

## 6. Deployment

Push changes to the `main` branch. Vercel automatically builds the connected GitHub repository. Check the latest deployment and verify its state is **READY** before testing the production site.
