# Ryko Collection — Native Android

Native Kotlin + Jetpack Compose Android client for the existing Ryko Collection Supabase project. This is not a WebView wrapper and does not modify the website.

## Included
- Native home, catalogue search, library and profile navigation
- Supabase email/password sign-in and account creation
- Catalogue reads from the existing public.titles table
- Saved library sync through public.user_library
- Title details and community comments
- Admin membership indicator from public.admin_users
- Encrypted local storage for the Supabase auth session

## Build
Open this folder in Android Studio, or run: gradle :app:assembleDebug

The GitHub Actions workflow builds a debug APK on pushes to ryko-native-android and uploads it as Ryko-Collection-debug-APK.

## Backend
Uses the existing public Supabase project URL and publishable key from the website configuration. The key is intentionally public and protected by Supabase RLS. Never put a service-role key in the app.

The app uses the same Supabase tables as the website. Catalogue data and community content remain shared between web and Android.
