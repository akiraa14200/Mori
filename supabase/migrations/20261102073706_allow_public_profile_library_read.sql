drop policy if exists "Users read own or public libraries" on public.user_library;
create policy "Users read own or public libraries"
on public.user_library for select
to anon, authenticated
using (
  user_id = (select auth.uid())
  or exists (
    select 1 from public.profiles p
    where p.id = user_id and p.public_lists = true
  )
);
revoke all on table public.user_library from anon;
grant select on table public.user_library to anon;