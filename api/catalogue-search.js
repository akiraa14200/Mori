// Public metadata search for the 7Manga admin importer.
// Search only; saving/updating catalogue records remains protected by Supabase RLS.
const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};
const reply = (data, status = 200) => new Response(JSON.stringify(data), {
  status,
  headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "public, max-age=60, s-maxage=300", ...cors },
});
const clean = (v) => typeof v === "string" ? v.trim() : "";
const statusMap = (v) => {
  const s = String(v || "").toLowerCase();
  if (/finished|completed|ended/.test(s)) return "Completed";
  if (/upcoming|unreleased|tba|not yet/.test(s)) return "Upcoming";
  if (/hiatus|on hold/.test(s)) return "Hiatus";
  if (/cancelled|canceled/.test(s)) return "Cancelled";
  if (/current|releasing|publishing|ongoing|in progress/.test(s)) return "Ongoing";
  return "";
};
const safeFetch = async (url, options = {}) => {
  const res = await fetch(url, { ...options, signal: AbortSignal.timeout(10000) });
  if (!res.ok) throw new Error("Provider returned HTTP " + res.status);
  return res.json();
};
async function searchKitsu(q, type) {
  const kind = type === "Anime" ? "anime" : "manga";
  const url = "https://kitsu.io/api/edge/" + kind + "?filter[text]=" + encodeURIComponent(q) + "&page[limit]=10&sort=-userCount";
  const json = await safeFetch(url, { headers: { Accept: "application/vnd.api+json" } });
  return (json.data || []).map((x) => {
    const a = x.attributes || {};
    const titles = a.titles || {};
    const title = clean(a.canonicalTitle) || clean(titles.en) || clean(titles.en_jp) || clean(titles.ja_jp);
    return {
      provider: "kitsu", provider_id: String(x.id), name: title,
      alternative_names: Object.values(titles).filter((v) => typeof v === "string" && v.trim() && v.trim() !== title).slice(0, 12),
      media_type: kind === "anime" ? "Anime" : "Manga",
      synopsis: "", cover_url: clean(a.posterImage?.large) || clean(a.posterImage?.original),
      banner_url: clean(a.coverImage?.large) || clean(a.coverImage?.original),
      genres: [], themes: [], status: statusMap(a.status),
      release_year: Number(String(a.startDate || "").slice(0, 4)) || null,
      chapters: a.chapterCount == null ? "" : String(a.chapterCount),
      episodes: a.episodeCount == null ? "" : String(a.episodeCount),
      source_url: "https://kitsu.io/" + kind + "/" + x.id,
      rating: a.averageRating ? Math.round(Number(a.averageRating) / 10) / 10 : null,
      source_note: "Kitsu metadata. Synopsis omitted; review artwork rights before publishing."
    };
  }).filter((x) => x.name);
}
async function searchWikidata(q) {
  const url = "https://www.wikidata.org/w/api.php?action=wbsearchentities&search=" + encodeURIComponent(q) + "&language=en&uselang=en&type=item&limit=10&format=json";
  const json = await safeFetch(url, { headers: { "Accept": "application/json", "Api-User-Agent": "7Manga/1.0 (catalogue metadata search)" } });
  return (json.search || []).map((x) => ({
    provider: "wikidata", provider_id: clean(x.id), name: clean(x.label),
    alternative_names: [], media_type: "Manga", synopsis: "", cover_url: "", banner_url: "",
    genres: [], themes: [], status: "", release_year: null, chapters: "", episodes: "",
    source_url: clean(x.concepturi) || (x.id ? "https://www.wikidata.org/wiki/" + x.id : ""),
    rating: null, source_note: "Wikidata structured data is CC0; this search result contains only a label/description, not verified manga fields."
  })).filter((x) => x.name);
}
async function searchMangaUpdates(q) {
  const json = await safeFetch("https://api.mangaupdates.com/v1/series/search", {
    method: "POST",
    headers: { "Content-Type": "application/json", "Accept": "application/json" },
    body: JSON.stringify({ search: { title: q }, page: 1, perpage: 10 })
  });
  return (json.results || []).map((x) => {
    const s = x.record || x;
    const title = clean(s.title) || clean(s.name);
    const image = s.image || {};
    const genres = Array.isArray(s.genres) ? s.genres.map((g) => clean(g.genre || g.name || g)).filter(Boolean) : [];
    const statusText = Array.isArray(s.status) ? s.status.map((v) => v.status || v).join(" ") : s.status;
    return {
      provider: "mangaupdates", provider_id: String(s.series_id || s.id || ""),
      name: title, alternative_names: Array.isArray(s.associated) ? s.associated.map((a) => clean(a.title)).filter(Boolean).slice(0, 12) : [],
      media_type: "Manga", synopsis: "", cover_url: clean(image.url?.original) || clean(image.url?.thumb) || clean(image.url),
      banner_url: "", genres, themes: [], status: statusMap(statusText),
      release_year: Number(s.year) || null, chapters: s.latest_chapter == null ? "" : String(s.latest_chapter),
      episodes: "", source_url: clean(s.url) || (s.series_id ? "https://www.mangaupdates.com/series/" + s.series_id : ""),
      rating: null, source_note: "MangaUpdates metadata. Credit MangaUpdates and review field/artwork rights before publishing."
    };
  }).filter((x) => x.name);
}
export default async function handler(request) {
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
  if (request.method !== "GET") return reply({ error: "Method not allowed" }, 405);
  const url = new URL(request.url);
  const provider = clean(url.searchParams.get("provider")).toLowerCase();
  const q = clean(url.searchParams.get("q")).slice(0, 120);
  const type = url.searchParams.get("type") === "Anime" ? "Anime" : "Manga";
  if (q.length < 2) return reply({ error: "Enter at least two characters." }, 400);
  if (!["kitsu", "wikidata", "mangaupdates"].includes(provider)) return reply({ error: "Unknown provider." }, 400);
  try {
    let items;
    if (provider === "kitsu") items = await searchKitsu(q, type);
    else if (provider === "wikidata") items = await searchWikidata(q);
    else items = await searchMangaUpdates(q);
    return reply({ provider, items });
  } catch (error) {
    return reply({ error: provider + " search failed: " + String(error?.message || error).slice(0, 180), items: [] }, 502);
  }
}
