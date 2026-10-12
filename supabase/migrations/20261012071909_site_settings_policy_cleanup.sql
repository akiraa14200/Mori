-- Avoid duplicate permissive SELECT policies while preserving admin write access.
drop policy if exists "Admins manage site settings" on public.site_settings;
create policy "Admins insert site settings" on public.site_settings
  for insert to authenticated
  with check (exists (select 1 from public.admin_users a where a.user_id = (select auth.uid())));
create policy "Admins update site settings" on public.site_settings
  for update to authenticated
  using (exists (select 1 from public.admin_users a where a.user_id = (select auth.uid())))
  with check (exists (select 1 from public.admin_users a where a.user_id = (select auth.uid())));
create policy "Admins delete site settings" on public.site_settings
  for delete to authenticated
  using (exists (select 1 from public.admin_users a where a.user_id = (select auth.uid())));
