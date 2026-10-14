// Server-side catalogue metadata search for 7Manga admin.
// Providers: Kitsu, MangaUpdates, Wikidata, Jikan, AniList.
const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};
const reply = (data, status = 200) => new Response(JSON.stringify(data), {
  status,
  headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "public, max-age=30, s-maxage=120", ...cors },
});
const clean = (v) => typeof v === "string" ? v.trim() : "";
const statusMap = (v) => {
  const s = String(Array.isArray(v) ? v.map(x => typeof x === "string" ? x : x?.status || x?.name || "").join(" ") : v || "").toLowerCase();
  if (/finished|completed|ended/.test(s)) return "Completed";
  if (/upcoming|unreleased|not yet published|tba|not yet/.test(s)) return "Upcoming";
  if (/hiatus|on hold/.test(s)) return "Hiatus";
  if (/cancelled|canceled/.test(s)) return "Cancelled";
  if (/current|releasing|publishing|ongoing|in progress/.test(s)) return "Ongoing";
  return "";
};
const safeFetch = async (url, options = {}) => {
  const res = await fetch(url, { ...options, signal: AbortSignal.timeout(12000) });
  if (!res.ok) {
    const detail = (await res.text().catch(() => "")).slice(0, 120);
    throw new Error("Upstream HTTP " + res.status + (detail ? ": " + detail : ""));
  }
  return res.json();
};
const result = (v = {}) => ({
  provider: v.provider || "", provider_id: String(v.provider_id || ""), name: clean(v.name),
  alternative_names: Array.isArray(v.alternative_names) ? v.alternative_names.filter(x => typeof x === "string" && x.trim()).slice(0, 15) : [],
  media_type: v.media_type || "Manga", synopsis: clean(v.synopsis), cover_url: clean(v.cover_url),
  banner_url: clean(v.banner_url), genres: Array.isArray(v.genres) ? v.genres.filter(Boolean) : [],
  themes: Array.isArray(v.themes) ? v.themes.filter(Boolean) : [], status: v.status || "",
  release_year: Number(v.release_year) || null, chapters: v.chapters == null ? "" : String(v.chapters),
  episodes: v.episodes == null ? "" : String(v.episodes), source_url: clean(v.source_url),
  rating: v.rating == null || !Number.isFinite(Number(v.rating)) ? null : Number(v.rating),
  source_note: clean(v.source_note)
});
async function searchKitsu(q, type) {
  const kind = type === "Anime" ? "anime" : "manga";
  // Do not sort by userCount: it is not consistently supported by Kitsu's edge API.
  const url = "https://kitsu.io/api/edge/" + kind + "?filter%5Btext%5D=" + encodeURIComponent(q) + "&page%5Blimit%5D=10&include=genres";
  const json = await safeFetch(url, { headers: { Accept: "application/vnd.api+json" } });
  const included = new Map((json.included || []).filter(r => r.type === "genres").map(r => [String(r.id), clean(r.attributes?.name)]));
  return (json.data || []).map(x => {
    const a = x.attributes || {}, titles = a.titles || {};
    const title = clean(a.canonicalTitle) || clean(titles.en) || clean(titles.en_jp) || clean(titles.ja_jp);
    return result({
      provider:"kitsu", provider_id:x.id, name:title,
      alternative_names:Object.values(titles).filter(v => typeof v === "string" && v.trim() && v.trim() !== title),
      media_type:kind === "anime" ? "Anime" : "Manga", cover_url:a.posterImage?.large || a.posterImage?.original,
      banner_url:a.coverImage?.large || a.coverImage?.original,
      genres:(x.relationships?.genres?.data || []).map(g => included.get(String(g.id))).filter(Boolean),
      status:statusMap(a.status), release_year:String(a.startDate || "").slice(0,4),
      chapters:a.chapterCount, episodes:a.episodeCount, source_url:"https://kitsu.io/" + kind + "/" + x.id,
      rating:a.averageRating ? Number(a.averageRating)/10 : null,
      source_note:"Kitsu result. Review all metadata and artwork permissions before publishing."
    });
  }).filter(x => x.name);
}
async function searchMangaUpdates(q) {
  const json = await safeFetch("https://api.mangaupdates.com/v1/series/search", {
    method:"POST", headers:{"Content-Type":"application/json","Accept":"application/json"},
    body:JSON.stringify({search:{title:q},page:1,perpage:10})
  });
  const rows = Array.isArray(json) ? json : (json.results || json.data || []);
  return rows.map(x => {
    const s = x.record || x.series || x, image = s.image || {};
    const title = clean(s.title) || clean(s.name);
    const genres = Array.isArray(s.genres) ? s.genres.map(g => clean(g?.genre || g?.name || g)).filter(Boolean) : [];
    return result({
      provider:"mangaupdates", provider_id:s.series_id || s.id, name:title,
      alternative_names:Array.isArray(s.associated) ? s.associated.map(a => clean(a.title || a)).filter(Boolean) : [],
      media_type:"Manga", cover_url:image.url?.original || image.url?.thumb || image.url,
      genres, status:statusMap(s.status), release_year:s.year, chapters:s.latest_chapter,
      source_url:s.url || (s.series_id ? "https://www.mangaupdates.com/series/" + s.series_id : ""),
      source_note:"MangaUpdates result. Review all metadata and artwork permissions before publishing."
    });
  }).filter(x => x.name);
}
async function searchWikidata(q, type) {
  const url = "https://www.wikidata.org/w/api.php?action=wbsearchentities&search=" + encodeURIComponent(q) + "&language=en&uselang=en&type=item&limit=10&format=json";
  const json = await safeFetch(url, {headers:{"Accept":"application/json","User-Agent":"7Manga metadata importer (public search)"}});
  return (json.search || []).map(x => result({
    provider:"wikidata", provider_id:x.id, name:x.label, media_type:type,
    source_url:x.concepturi || (x.id ? "https://www.wikidata.org/wiki/" + x.id : ""),
    source_note:clean(x.description) || "Wikidata entity label only; verify facts before importing."
  })).filter(x => x.name);
}
async function searchJikan(q, type) {
  const kind = type === "Anime" ? "anime" : "manga";
  const json = await safeFetch("https://jikan.lucashdo.com/v1/" + kind + "?q=" + encodeURIComponent(q) + "&limit=10");
  const rows = Array.isArray(json.data) ? json.data : Array.isArray(json.data?.data) ? json.data.data : [];
  return rows.map(x => {
    const title = x.title || x.titleEnglish || x.title_english || x.titleJapanese || x.title_japanese || x.titlePreferred || "";
    const images = x.images || {};
    const imageUrl = images.webp?.large_image_url || images.jpg?.large_image_url || x.imageUrl || x.image || "";
    const genres = (x.genres || []).map(g => typeof g === "string" ? g : g.name).filter(Boolean);
    const themes = (x.themes || []).map(g => typeof g === "string" ? g : g.name).filter(Boolean);
    return result({
      provider:"jikan", provider_id:x.malId || x.mal_id || x.id, name:title,
      alternative_names:[x.titleEnglish,x.title_english,x.titleJapanese,x.title_japanese,...(x.titles || []).map(t => typeof t === "string" ? t : t.title)].filter(Boolean).filter(t => t !== title),
      media_type:kind === "anime" ? "Anime" : "Manga", synopsis:x.synopsis || x.description,
      cover_url:imageUrl, banner_url:"", genres, themes,
      status:statusMap(x.status), release_year:x.year || x.published?.from?.slice(0,4),
      chapters:x.chapters, episodes:x.episodes, source_url:x.url || x.siteUrl, rating:x.score,
      source_note:"Jikan-compatible community API (Jikan-Edge) / MyAnimeList-derived data. Verify source terms and artwork rights."
    });
  }).filter(x => x.name);
}
async function searchAniList(q, type) {
  const query = `query ($search: String, $type: MediaType) {
    Page(page: 1, perPage: 10) {
      media(search: $search, type: $type, sort: SEARCH_MATCH) {
        id siteUrl type format status startDate { year } averageScore chapters episodes
        title { romaji english native userPreferred } synonyms
        description(asHtml: false) genres coverImage { extraLarge large } bannerImage
      }
    }
  }`;
  const json = await safeFetch("https://graphql.anilist.co", {
    method:"POST", headers:{"Content-Type":"application/json","Accept":"application/json"},
    body:JSON.stringify({query,variables:{search:q,type:type === "Anime" ? "ANIME" : "MANGA"}})
  });
  if (json.errors?.length && !json.data) throw new Error(json.errors[0].message || "AniList GraphQL error");
  return (json.data?.Page?.media || []).map(x => {
    const t = x.title || {};
    return result({
      provider:"anilist", provider_id:x.id, name:t.english || t.romaji || t.native || t.userPreferred,
      alternative_names:[t.romaji,t.english,t.native,...(x.synonyms || [])].filter(Boolean).filter(v => v !== (t.english || t.romaji || t.native || t.userPreferred)),
      media_type:x.type === "ANIME" ? "Anime" : "Manga", synopsis:(x.description || "").replace(/<[^>]*>/g,""),
      cover_url:x.coverImage?.extraLarge || x.coverImage?.large, banner_url:x.bannerImage,
      genres:x.genres || [], status:statusMap(x.status), release_year:x.startDate?.year,
      chapters:x.chapters, episodes:x.episodes, source_url:x.siteUrl,
      rating:x.averageScore == null ? null : Number(x.averageScore)/10,
      source_note:"AniList metadata. Review current API terms and artwork permissions before publishing."
    });
  }).filter(x => x.name);
}
export default async function handler(request) {
  if (request.method === "OPTIONS") return new Response(null,{status:204,headers:cors});
  if (request.method !== "GET") return reply({error:"Method not allowed"},405);
  const url = new URL(request.url), provider = clean(url.searchParams.get("provider")).toLowerCase();
  const q = clean(url.searchParams.get("q")).slice(0,120);
  const type = url.searchParams.get("type") === "Anime" ? "Anime" : "Manga";
  if (q.length < 2) return reply({error:"Enter at least two characters."},400);
  if (!["kitsu","wikidata","mangaupdates","jikan","anilist"].includes(provider)) return reply({error:"Unknown provider."},400);
  try {
    let items;
    if (provider === "kitsu") items = await searchKitsu(q,type);
    else if (provider === "wikidata") items = await searchWikidata(q,type);
    else if (provider === "mangaupdates") items = await searchMangaUpdates(q);
    else if (provider === "jikan") items = await searchJikan(q,type);
    else items = await searchAniList(q,type);
    return reply({provider,items});
  } catch (error) {
    console.error("[catalogue-search]",provider,error);
    return reply({error:provider+" search failed: "+String(error?.message || error).slice(0,220),items:[]},502);
  }
}
