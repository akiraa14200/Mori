-- Mori hybrid catalogue cache. Supabase remains the canonical database for auth, profiles, comments, reviews and user-specific data.
CREATE TABLE IF NOT EXISTS titles (
 id TEXT PRIMARY KEY, name TEXT NOT NULL, alternative_names TEXT NOT NULL DEFAULT '[]', media_type TEXT NOT NULL,
 synopsis TEXT NOT NULL DEFAULT '', cover_url TEXT NOT NULL DEFAULT '', banner_url TEXT NOT NULL DEFAULT '',
 genres TEXT NOT NULL DEFAULT '[]', themes TEXT NOT NULL DEFAULT '[]', status TEXT NOT NULL DEFAULT 'Ongoing',
 release_year INTEGER, season TEXT, rating REAL, popularity INTEGER NOT NULL DEFAULT 0, chapters TEXT, episodes TEXT,
 author TEXT, artist TEXT, studio TEXT, country TEXT, source_url TEXT, anilist_id INTEGER, trailer_url TEXT,
 is_trending INTEGER NOT NULL DEFAULT 0, is_featured INTEGER NOT NULL DEFAULT 0,
 created_at TEXT, updated_at TEXT, synced_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, sync_run_id TEXT
);
CREATE TABLE IF NOT EXISTS characters (
 id TEXT PRIMARY KEY, title_id TEXT NOT NULL, name TEXT NOT NULL, role TEXT NOT NULL DEFAULT 'Supporting',
 description TEXT NOT NULL DEFAULT '', image_url TEXT NOT NULL DEFAULT '', created_at TEXT,
 synced_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, sync_run_id TEXT
);
CREATE TABLE IF NOT EXISTS sync_state (key TEXT PRIMARY KEY, value TEXT NOT NULL, updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE INDEX IF NOT EXISTS idx_titles_name ON titles(name);
CREATE INDEX IF NOT EXISTS idx_titles_media_type ON titles(media_type);
CREATE INDEX IF NOT EXISTS idx_titles_status ON titles(status);
CREATE INDEX IF NOT EXISTS idx_characters_title_id ON characters(title_id);

-- Keep the catalogue's newest-first pagination and per-title character lookups efficient.
CREATE INDEX IF NOT EXISTS idx_titles_created_at_id ON titles(created_at DESC, id ASC);
CREATE INDEX IF NOT EXISTS idx_characters_title_created_at ON characters(title_id, created_at DESC);
