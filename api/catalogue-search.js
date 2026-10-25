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
const pause = (ms) => new Promise(resolve => setTimeout(resolve, ms));
const safeFetch = async (url, options = {}) => {
  let lastError;
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const res = await fetch(url, { ...options, signal: AbortSignal.timeout(12000) });
      if (res.ok) {
        try { return await res.json(); }
        catch { throw new Error("Upstream returned invalid JSON (HTTP " + res.status + ")"); }
      }
      const detail = (await res.text().catch(() => "")).replace(/\\s+/g, " ").slice(0, 100);
      const error = new Error("Upstream HTTP " + res.status + (detail ? ": " + detail : ""));
      error.status = res.status;
      lastError = error;
      // Retry only transient failures; do not repeat invalid requests.
      if (attempt === 0 && [429, 500, 502, 503, 504].includes(res.status)) {
        const retryAfter = Number(res.headers.get("retry-after"));
        await pause(Number.isFinite(retryAfter) && retryAfter > 0 ? Math.min(retryAfter * 1000, 1800) : 700);
        continue;
      }
      throw error;
    } catch (error) {
      lastError = error;
      if (attempt === 0 && !error.status) {
        await pause(250);
        continue;
      }
      throw error;
    }
  }
  throw lastError || new Error("Upstream request failed");
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
    body:JSON.stringify({search:q,stype:"title",page:1,perpage:10})
  });
  const rows = Array.isArray(json) ? json : (json.results || json.data || []);
  return rows.slice(0,10).map(x => {
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
  const json = await safeFetch(url, {headers:{"Accept":"application/json","Api-User-Agent":"7Manga/1.0 (https://github.com/akiraa14200/Mori)"}});
  return (json.search || []).map(x => result({
    provider:"wikidata", provider_id:x.id, name:x.label, media_type:type,
    source_url:x.concepturi || (x.id ? "https://www.wikidata.org/wiki/" + x.id : ""),
    source_note:clean(x.description) || "Wikidata entity label only; verify facts before importing."
  })).filter(x => x.name);
}
async function searchJikan(q, type) {
  const kind = type === "Anime" ? "anime" : "manga";
  const mapRows = (rows, edge = false) => rows.slice(0,10).map(x => {
    // Tenrai follows the Jikan v4 snake_case schema. Jikan-Edge uses camelCase.
    const title = x.title || x.title_english || x.titleEnglish || x.title_japanese || x.titleJapanese || x.titlePreferred || "";
    const images = x.images || {};
    const imageUrl = images.webp?.large_image_url || images.jpg?.large_image_url || images.medium || x.imageUrl || x.image || "";
    const genres = (x.genres || []).map(g => typeof g === "string" ? g : g?.name).filter(Boolean);
    const themes = (x.themes || []).map(g => typeof g === "string" ? g : g?.name).filter(Boolean);
    const malId = x.mal_id || x.malId || x.id;
    return result({
      provider:"jikan", provider_id:malId, name:title,
      alternative_names:[x.title_english,x.titleEnglish,x.title_japanese,x.titleJapanese,...(x.titles || []).map(t => typeof t === "string" ? t : t.title)].filter(Boolean).filter(t => t !== title),
      media_type:kind === "anime" ? "Anime" : "Manga", synopsis:x.synopsis || x.description,
      cover_url:imageUrl, banner_url:"", genres, themes,
      status:statusMap(x.status), release_year:x.year || x.published?.from?.year || x.published?.from?.slice?.(0,4) || x.aired?.from?.slice?.(0,4),
      chapters:x.chapters, episodes:x.episodes, source_url:x.url || x.siteUrl || (malId ? "https://myanimelist.net/" + kind + "/" + malId : ""), rating:x.score,
      source_note:edge
        ? "Jikan-Edge community API / MyAnimeList-derived data. Review source terms and artwork rights."
        : "Tenrai API (Jikan-compatible MyAnimeList catalogue data). Review source terms and artwork rights."
    });
  }).filter(x => x.name);
  // Jikan's official public API was discontinued. Use the maintained Tenrai v1
  // Jikan-compatible endpoint first, then the community Jikan-Edge endpoint.
  try {
    const json = await safeFetch("https://api.tenrai.org/v1/" + kind + "?q=" + encodeURIComponent(q) + "&limit=10");
    const rows = Array.isArray(json.data) ? json.data : Array.isArray(json.data?.data) ? json.data.data : [];
    if (rows.length) return mapRows(rows);
    // Empty results are legitimate, but try the alternate source in case its cache differs.
  } catch (primaryError) {
    console.warn("[catalogue-search] Tenrai failed; trying Jikan-Edge:", primaryError.message);
  }
  const json = await safeFetch("https://jikan.lucashdo.com/v1/" + kind + "?q=" + encodeURIComponent(q) + "&page=1");
  const rows = Array.isArray(json.data) ? json.data : Array.isArray(json.data?.data) ? json.data.data : [];
  return mapRows(rows, true);
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
async function searchTVMaze(q) {
  // TVmaze's public show-search API is free and does not require an API key.
  const json = await safeFetch("https://api.tvmaze.com/search/shows?q=" + encodeURIComponent(q));
  return (Array.isArray(json) ? json : []).slice(0,10).map(row => {
    const x = row.show || {}, image = x.image || {};
    const premiered = clean(x.premiered);
    const ended = clean(x.ended);
    const genres = Array.isArray(x.genres) ? x.genres : [];
    const rating = x.rating?.average;
    const summary = clean(x.summary).replace(/<[^>]*>/g, " ").replace(/\\s+/g, " ").trim();
    return result({
      provider:"tvmaze", provider_id:x.id, name:x.name,
      alternative_names:Array.isArray(x.aliases) ? x.aliases : [],
      media_type:"TV Series", synopsis:summary,
      cover_url:image.original || image.medium || "",
      banner_url:"",
      genres,
      status:/ended|canceled/i.test(x.status || "") ? "Completed" : (/running|in development/i.test(x.status || "") ? "Ongoing" : statusMap(x.status)),
      release_year:premiered ? Number(premiered.slice(0,4)) || null : null,
      episodes:"", source_url:x.url || (x.id ? "https://www.tvmaze.com/shows/" + x.id : ""),
      rating:rating == null ? null : Number(rating),
      source_note:"TVmaze public API (free, no API key). Credit TVmaze and comply with its CC BY-SA licensing terms. Check image permissions before publishing."
    });
  }).filter(x => x.name);
}

async function searchIndianMovies(q) {
  // Wikidata query restricted to films whose country of origin is India.
  // Unlike a generic title search, this intentionally favours Indian cinema.
  const term = JSON.stringify(q.toLowerCase());
  const sparql = `SELECT DISTINCT ?item ?itemLabel ?date ?image ?imdb WHERE {
    ?item wdt:P31/wdt:P279* wd:Q11424;
          wdt:P495 wd:Q668;
          rdfs:label ?label.
    FILTER(LANG(?label) IN ("en","hi","ta","te","ml","kn","bn","mr"))
    FILTER(CONTAINS(LCASE(STR(?label)), ${term}))
    OPTIONAL { ?item wdt:P577 ?date. }
    OPTIONAL { ?item wdt:P18 ?image. }
    OPTIONAL { ?item wdt:P345 ?imdb. }
    SERVICE wikibase:label { bd:serviceParam wikibase:language "en,hi,ta,te,ml,kn,bn,mr". }
  } LIMIT 20`;
  const endpoint = "https://query.wikidata.org/sparql?query=" + encodeURIComponent(sparql) + "&format=json";
  const json = await safeFetch(endpoint, {
    headers: {
      Accept: "application/sparql-results+json",
      "User-Agent": "SUILibrary/1.0 (Indian movie catalogue search)"
    }
  });
  const seen = new Set();
  return (json.results?.bindings || []).map(row => {
    const id = (row.item?.value || "").split("/").pop();
    const name = clean(row.itemLabel?.value || row.label?.value);
    if (!id || !name || seen.has(id)) return null;
    seen.add(id);
    const date = row.date?.value || "";
    const imdb = row.imdb?.value || "";
    return result({
      provider: "wikidata-india", provider_id: id, name,
      media_type: "Movie",
      cover_url: row.image?.value || "",
      release_year: date ? Number(date.slice(0,4)) || null : null,
      source_url: imdb ? "https://www.imdb.com/title/" + imdb + "/" : "https://www.wikidata.org/wiki/" + id,
      source_note: "Indian film search via Wikidata (country of origin: India). Metadata and poster availability vary; verify details before saving."
    });
  }).filter(Boolean).slice(0,10);
}
async function searchOMDb(q, type) {
  const apiKey = (process.env.OMDB_API_KEY || "").trim();
  // Keep catalogue importing usable when no OMDb key is configured.
  // TVmaze is the keyless TV source; Wikidata is the keyless movie entity fallback.
  if (!apiKey) {
    if (type === "TV Series") {
      const rows = await searchTVMaze(q);
      return rows.map(x => ({ ...x, provider: "tvmaze", source_note: "OMDB_API_KEY is not configured, so this result came from TVmaze (free, no key). Credit TVmaze and comply with CC BY-SA terms." }));
    }
    if (type === "Movie") {
      const rows = await searchWikidata(q, "Movie");
      return rows.map(x => ({ ...x, provider: "wikidata", media_type: "Movie", source_note: "OMDB_API_KEY is not configured, so this fallback came from Wikidata. Results may contain basic entity metadata only; verify the title and details before saving." }));
    }
    throw new Error("Choose Movie or TV Series for the automatic no-key fallback, or configure OMDB_API_KEY for OMDb results.");
  }
  const url = "https://www.omdbapi.com/?apikey=" + encodeURIComponent(apiKey) + "&s=" + encodeURIComponent(q) + "&type=" + (type === "TV Series" ? "series" : "movie") + "&page=1";
  const json = await safeFetch(url, { headers: { Accept: "application/json" } });
  if (json.Response === "False") {
    if (/invalid api key/i.test(json.Error || "")) throw new Error("OMDb rejected OMDB_API_KEY. Check the key in Vercel Environment Variables and redeploy.");
    if (/limit|request/i.test(json.Error || "")) throw new Error("OMDb request limit reached: " + clean(json.Error));
    return [];
  }
  const rows = Array.isArray(json.Search) ? json.Search : [];
  return rows.slice(0,10).map(x => {
    const isTV = x.Type === "series" || type === "TV Series";
    const year = String(x.Year || "").match(/\\d{4}/)?.[0];
    return result({
      provider: "omdb", provider_id: x.imdbID, name: x.Title,
      media_type: isTV ? "TV Series" : "Movie",
      cover_url: x.Poster && x.Poster !== "N/A" ? x.Poster : "",
      release_year: year ? Number(year) : null,
      source_url: x.imdbID ? "https://www.imdb.com/title/" + x.imdbID + "/" : "",
      source_note: "OMDb search result (IMDb ID " + (x.imdbID || "unavailable") + "). Add OMDB_API_KEY in Vercel for full OMDb search; confirm metadata and artwork rights before publishing."
    });
  }).filter(x => x.name);
}
async function handleCatalogueRequest(request) {
  if (request.method === "OPTIONS") return new Response(null,{status:204,headers:cors});
  if (request.method !== "GET") return reply({error:"Method not allowed"},405);
  const url = new URL(request.url), provider = clean(url.searchParams.get("provider")).toLowerCase();
  const q = clean(url.searchParams.get("q")).slice(0,120);
  const requestedType = clean(url.searchParams.get("type"));
  const type = ["Anime","Manga","Manhwa","Manhua","Movie","TV Series"].includes(requestedType) ? requestedType : "Manga";
  if (q.length < 2) return reply({error:"Enter at least two characters."},400);
  if (!["kitsu","wikidata","wikidata-india","mangaupdates","jikan","anilist","omdb","tvmaze"].includes(provider)) return reply({error:"Unknown provider."},400);
  try {
    let items;
    if (provider === "kitsu") items = await searchKitsu(q,type);
    else if (provider === "wikidata") items = await searchWikidata(q,type);
    else if (provider === "wikidata-india") items = await searchIndianMovies(q);
    else if (provider === "mangaupdates") items = await searchMangaUpdates(q);
    else if (provider === "jikan") items = await searchJikan(q,type);
    else if (provider === "omdb") items = await searchOMDb(q,type);
    else if (provider === "tvmaze") {
      if (type !== "TV Series") throw new Error("TVmaze is a free provider for TV series only. Select TV Series as the media type.");
      items = await searchTVMaze(q);
    }
    else items = await searchAniList(q,type);
    return reply({provider,items});
  } catch (error) {
    console.error("[catalogue-search]",provider,error);
    return reply({error:provider+" search failed: "+String(error?.message || error).slice(0,220),items:[]},502);
  }
}


// Vercel's /api/*.js Node runtime expects (req, res). Keep the Fetch API
// implementation above, but explicitly bridge its Response to Node's response
// object so every provider uses the same working serverless route.
export default async function handler(req, res) {
  const headers = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
  };
  try {
    const protocol = req.headers?.["x-forwarded-proto"] || "https";
    const host = req.headers?.host || "localhost";
    const requestUrl = new URL(req.url || "/", protocol + "://" + host);
    const request = new Request(requestUrl, { method: req.method || "GET" });
    const result = await handleCatalogueRequest(request);
    res.statusCode = result.status || 200;
    result.headers.forEach((value, key) => res.setHeader(key, value));
    for (const [key, value] of Object.entries(headers)) {
      if (!res.hasHeader(key)) res.setHeader(key, value);
    }
    const body = await result.text();
    res.end(body);
  } catch (error) {
    console.error("[catalogue-search] serverless handler failure", error);
    res.statusCode = 500;
    res.setHeader("Content-Type", "application/json; charset=utf-8");
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.end(JSON.stringify({ error: "Catalogue API internal error: " + String(error?.message || error).slice(0, 180), items: [] }));
  }
}
