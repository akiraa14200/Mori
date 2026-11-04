// Curated official anime video channels for RykoCollection.
// YouTube Atom feeds expose recent public uploads without an API key.
// Each channel feed is capped at 15 recent uploads; the full channel archive
// remains available through its channelUrl.
const SOURCES = [
  { key: "muse-india", name: "Muse India", handle: "museindiachannel", id: "UCYYhAzgWuxPauRXdPpLAX3Q", category: "Muse India", channelUrl: "https://www.youtube.com/@museindiachannel/videos" },
  { key: "mappa", name: "MAPPA", handle: "mappa_official_ig", category: "Animation studios", channelUrl: "https://www.youtube.com/@mappa_official_ig/videos" },
  { key: "kyoto-animation", name: "Kyoto Animation", handle: "kyoani", category: "Animation studios", channelUrl: "https://www.youtube.com/@kyoani/videos" },
  { key: "ufotable", name: "ufotable", handle: "ufotable_inc", category: "Animation studios", channelUrl: "https://www.youtube.com/@ufotable_inc/videos" },
  { key: "wit-studio", name: "WIT STUDIO", handle: "wit_studio", category: "Animation studios", channelUrl: "https://www.youtube.com/@wit_studio/videos" },
  { key: "a1-pictures", name: "A-1 Pictures", handle: "a1pictures", category: "Animation studios", channelUrl: "https://www.youtube.com/@a1pictures/videos" },
  { key: "trigger", name: "TRIGGER", handle: "trigger_inc", category: "Animation studios", channelUrl: "https://www.youtube.com/@trigger_inc/videos" },
  { key: "toei-animation", name: "Toei Animation", handle: "ToeiAnimationjp", id: "UCLhgIX2L5ZCaWdlaxR_oTAg", category: "Animation studios", channelUrl: "https://www.youtube.com/@ToeiAnimationjp/videos" },
  { key: "sunrise", name: "SUNRISE", handle: "SUNRISE_web", category: "Animation studios", channelUrl: "https://www.youtube.com/@SUNRISE_web/videos" },
  { key: "production-ig", name: "Production I.G", handle: "ProductionIG", category: "Animation studios", channelUrl: "https://www.youtube.com/@ProductionIG/videos" },
  { key: "muse-asia", name: "Muse Asia", handle: "MuseAsia", category: "Official anime publishers", channelUrl: "https://www.youtube.com/@MuseAsia/videos" },
  { key: "crunchyroll", name: "Crunchyroll", handle: "Crunchyroll", category: "Official anime publishers", channelUrl: "https://www.youtube.com/@Crunchyroll/videos" },
  { key: "toho-animation", name: "TOHO animation", handle: "TOHOanimation", category: "Official anime publishers", channelUrl: "https://www.youtube.com/@TOHOanimation/videos" },
  { key: "kadokawa-anime", name: "KADOKAWAanime", handle: "KADOKAWAanime", category: "Official anime publishers", channelUrl: "https://www.youtube.com/@KADOKAWAanime/videos" }
];

const channelIdCache = new Map();
const decodeXml = (value = "") => value
  .replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">")
  .replace(/&quot;/g, '"').replace(/&#39;|&apos;/g, "'");
const tag = (source, name) => {
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = source.match(new RegExp("<" + escaped + "(?:\\s[^>]*)?>([\\s\\S]*?)</" + escaped + ">","i"));
  return match ? decodeXml(match[1].replace(/<[^>]+>/g, "").trim()) : "";
};
async function resolveChannelId(source) {
  if (source.id) return source.id;
  if (channelIdCache.has(source.handle)) return channelIdCache.get(source.handle);
  const task = (async () => {
    const response = await fetch("https://www.youtube.com/@" + encodeURIComponent(source.handle), {
      headers: { "User-Agent": "Mozilla/5.0 (compatible; RykoCollection/1.0)", "Accept-Language": "en-US,en;q=0.8" },
      signal: AbortSignal.timeout(6500)
    });
    if (!response.ok) throw new Error("Channel page returned HTTP " + response.status);
    const html = await response.text();
    const id = html.match(/"externalId"\s*:\s*"(UC[A-Za-z0-9_-]{20,})"/)?.[1]
      || html.match(/itemprop="channelId"\s+content="(UC[A-Za-z0-9_-]{20,})"/)?.[1]
      || html.match(/"channelId"\s*:\s*"(UC[A-Za-z0-9_-]{20,})"/)?.[1];
    if (!id) throw new Error("Could not resolve channel ID");
    return id;
  })();
  channelIdCache.set(source.handle, task);
  try { return await task; } catch (error) { channelIdCache.delete(source.handle); throw error; }
}
async function fetchChannel(source) {
  try {
    const channelId = await resolveChannelId(source);
    const response = await fetch("https://www.youtube.com/feeds/videos.xml?channel_id=" + encodeURIComponent(channelId), {
      headers: { "User-Agent": "Mozilla/5.0 (compatible; RykoCollection/1.0)", "Accept": "application/atom+xml,application/xml,text/xml" },
      signal: AbortSignal.timeout(7500)
    });
    if (!response.ok) throw new Error("Feed returned HTTP " + response.status);
    const xml = await response.text();
    const entries = [...xml.matchAll(/<entry>([\s\S]*?)<\/entry>/g)].slice(0, 15);
    const videos = entries.map((match) => {
      const item = match[1];
      const videoId = tag(item, "yt:videoId");
      if (!/^[A-Za-z0-9_-]{11}$/.test(videoId)) return null;
      const title = tag(item, "title");
      const publishedAt = tag(item, "published");
      const thumbnail = item.match(/<media:thumbnail[^>]+url="([^"]+)"/)?.[1] || "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
      const author = tag(item, "name") || source.name;
      const isShort = /#shorts?\b/i.test(title);
      return {
        videoId, title, creator: author, publishedAt,
        url: "https://www.youtube.com/watch?v=" + videoId,
        thumbnail: decodeXml(thumbnail), sourceKey: source.key,
        sourceName: source.name, sourceCategory: source.category,
        channelUrl: source.channelUrl, format: isShort ? "short" : "video"
      };
    }).filter(Boolean);
    return { sourceKey: source.key, sourceName: source.name, sourceCategory: source.category, channelUrl: source.channelUrl, videos, error: null };
  } catch (error) {
    return { sourceKey: source.key, sourceName: source.name, sourceCategory: source.category, channelUrl: source.channelUrl, videos: [], error: String(error?.message || error).slice(0, 180) };
  }
}
export default async function handler(request) {
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Methods": "GET, OPTIONS", "Access-Control-Allow-Headers": "Content-Type" } });
  if (request.method !== "GET") return Response.json({ error: "Method not allowed" }, { status: 405, headers: { Allow: "GET, OPTIONS" } });
  const results = await Promise.all(SOURCES.map(fetchChannel));
  const seen = new Set();
  const channels = results.map((result) => ({
    ...result,
    videos: result.videos.filter(video => {
      if (seen.has(video.videoId)) return false;
      seen.add(video.videoId);
      return true;
    })
  }));
  const videos = channels.flatMap(channel => channel.videos);
  return Response.json({
    updatedAt: new Date().toISOString(),
    count: videos.length,
    channels: channels.map(({ sourceKey, sourceName, sourceCategory, channelUrl, videos: channelVideos, error }) => ({
      sourceKey, sourceName, sourceCategory, channelUrl, count: channelVideos.length, error
    })),
    videos
  }, {
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "public, s-maxage=300, stale-while-revalidate=900",
      "Access-Control-Allow-Origin": "*"
    }
  });
}
