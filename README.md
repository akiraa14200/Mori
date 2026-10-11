# 7manga

A responsive dark manga/manhwa/manhua/anime catalog with a glassmorphic interface, trending carousel, search, genre/status filters, title details, Supabase auth and an admin-only add-title form.

## Supabase setup
1. Create a Supabase project and run `supabase/schema.sql` in SQL Editor.
2. In GitHub: Settings → Secrets and variables → Actions → Variables, add:
   - `VITE_SUPABASE_URL` — project URL
   - `VITE_SUPABASE_ANON_KEY` — publishable/anon key only
   - `VITE_ADMIN_EMAILS` — comma-separated email addresses allowed to see the admin UI
3. Sign up through the website. Copy the user UUID from Supabase Authentication → Users and grant catalog-write access from SQL Editor:
   `insert into public.admin_users(user_id) values ('YOUR_AUTH_USER_UUID');`
4. Never put a service-role key in the browser or GitHub Pages variables.

Without Supabase variables, the site uses demo content. The GitHub Actions workflow generates `config.js` at deploy time from repository Actions variables. Enable Pages with **GitHub Actions** as the source. The site URL will be `https://akiraa14200.github.io/Mori/`.
