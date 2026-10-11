# 7manga — beginner admin guide

This guide explains how to add titles to the 7manga catalog. The website reads title records from Supabase. You do not need to edit HTML every time you add a title.

## 1. Open your website

Open: https://akiraa14200.github.io/Mori/

If the page does not load yet, open the repository's **Actions** tab on GitHub and check that the Pages workflow has a green check mark. The website files are in https://github.com/akiraa14200/Mori.

## 2. Create your administrator account (one time)

1. Open the website and tap **Sign in**.
2. Enter your email and a password, then tap **Create account**.
3. If Supabase asks you to confirm your email, open the confirmation email first.
4. Open the Supabase dashboard: https://supabase.com/dashboard/project/moicrqcddmftjgwjqkvz
5. Go to **Authentication → Users** and find your account. Copy its **User UID**.
6. Open **SQL Editor → New query**, paste the SQL below, replace YOUR_USER_UID with your actual UID, and click **Run**:

~~~sql
insert into public.admin_users (user_id)
values ('YOUR_USER_UID')
on conflict (user_id) do nothing;
~~~

Only grant this to people you trust. Never share a Supabase service-role key or put one in the website.

> Important: the current site also checks the adminEmails list in config.js to show the admin controls. If you sign in but do not see **Add title**, the account email may need to be added to that setting too. The database permission above is still required for secure writes.

## 3. Add a manga/manhwa/anime entry

1. Open the website and **Sign in** with your administrator account.
2. Tap **Add title**.
3. Fill in the fields:
   - **Title name** — the main title shown on the card.
   - **Media type** — Manga, Manhwa, Manhua, Anime, Light Novel, or Web Novel.
   - **Genres** — comma-separated, for example: Action, Fantasy, Adventure.
   - **Status** — Ongoing, Completed, Hiatus, Upcoming, or Cancelled.
   - **Cover image URL** — a direct, public link to the cover image.
   - **Banner image URL** — a direct, public link to a wide banner (optional).
   - **Author / studio** — author for manga/manhwa, or studio for anime.
   - **Release year** — year the title began/released.
   - **Rating** — a number from 0 to 10.
   - **Chapters / episodes** — for example: 125 chapters or 24 episodes.
   - **Synopsis** — a short description of the story.
   - **Alternative names** — comma-separated alternate titles.
4. Tap **Save title**. The title should be stored in Supabase and appear in the catalog.

## 4. How to add cover and character pictures

Images are added by URL, not by selecting a picture from your phone in the current form.

1. Upload the image to a trusted image host that provides a **public direct image URL**.
2. Copy the image address. It should point directly to the image file and open as an image when pasted into a browser.
3. Paste that address into **Cover image URL** or **Banner image URL**.

For example, a direct link often ends in .jpg, .png, or .webp, though some hosts use image URLs without an extension. Avoid private or temporary links that require you to log in.

**Character pictures:** the current repository's visible Add title form does not yet include character fields, so character pictures cannot be added through that form at the moment. The database supports characters, but a character editor needs to be added to the website UI first. Do not try to add character pictures by editing random code.

## 5. If something goes wrong

- **No Add title button:** confirm you are signed in, your email is in config.js's adminEmails list, and your account UID was added to public.admin_users.
- **Permission/RLS error:** make sure you ran the SQL in step 2 using the correct User UID.
- **Image is blank:** check that the URL is public and opens directly in a browser.
- **Title does not appear:** open Supabase → **Table Editor → titles** and check whether the record was saved. Refresh the website.

## Fields supported by the database

Title records support name, alternative names, media type, synopsis, cover URL, banner URL, genres, themes, status, release year, season, rating, popularity, chapters, episodes, author, artist, studio, country, source URL, trending and featured flags. Character records support name, role, description and image URL, linked to a title. Some of these fields are in the database but are not yet exposed in the current website form.
