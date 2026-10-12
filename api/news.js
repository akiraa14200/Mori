const FEEDS = [
  { id: 'anime', label: 'Anime announcements', query: 'anime announcement OR anime adaptation OR anime sequel OR anime studio news', category: 'Anime' },
  { id: 'manga', label: 'Manga & manhwa', query: 'manga OR manhwa OR manhua OR webtoon chapter release announcement', category: 'Manga & Manhwa' },
  { id: 'industry', label: 'Creators & industry', query: 'manga author interview OR manga artist OR anime industry publisher', category: 'Creators & Industry' },
  { id: 'anime-publishers', label: 'Anime publisher updates', query: 'site:animenewsnetwork.com anime OR site:crunchyroll.com/news anime', category: 'Anime' },
  { id: 'manga-publishers', label: 'Manga publisher updates', query: 'site:viz.com manga OR site:yenpress.com manhwa OR manga', category: 'Manga & Manhwa' }
];
const CACHE_TTL = 5 * 60 * 1000;
let memoryCache = { expiresAt: 0, value: null };

function decodeXml(value = '') {
  return String(value)
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
    .replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"').replace(/&#39;|&apos;/g, "'")
    .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n)))
    .replace(/&#x([\da-f]+);/gi, (_, n) => String.fromCodePoint(parseInt(n, 16)));
}
function tagValue(xml, tag) {
  const safeTag = tag.replace(/[^a-zA-Z0-9:]/g, '');
  const match = xml.match(new RegExp('<' + safeTag + '(?:\\s[^>]*)?>([\\s\\S]*?)<\\/' + safeTag + '\\s*>', 'i'));
  return match ? decodeXml(match[1]).trim() : '';
}
function cleanText(value) {
  return decodeXml(value)
    .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi, ' ')
    .replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();
}
function getLink(block) {
  const atom = block.match(/<link\b[^>]*\bhref=["']([^"']+)["'][^>]*>/i);
  const raw = atom ? atom[1] : tagValue(block, 'link') || tagValue(block, 'guid');
  try {
    const url = new URL(decodeXml(raw));
    return ['http:', 'https:'].includes(url.protocol) ? url.href : '';
  } catch { return ''; }
}
function parseFeed(xml, feed) {
  const blocks = xml.match(/<(?:item|entry)\b[\s\S]*?<\/(?:item|entry)\s*>/gi) || [];
  const items = [];
  for (const block of blocks) {
    const title = cleanText(tagValue(block, 'title'));
    const link = getLink(block);
    const description = cleanText(tagValue(block, 'description') || tagValue(block, 'content:encoded') || tagValue(block, 'summary') || tagValue(block, 'content'));
    const published = tagValue(block, 'pubDate') || tagValue(block, 'published') || tagValue(block, 'updated') || tagValue(block, 'dc:date');
    const timestamp = Date.parse(published);
    if (!title || !link || (Number.isFinite(timestamp) && Date.now() - timestamp > 14 * 86400000)) continue;
    const source = cleanText(tagValue(block, 'source')) || feed.label;
    items.push({
      title, link, description: description.slice(0, 500),
      pubDate: Number.isFinite(timestamp) ? new Date(timestamp).toISOString() : '',
      source, category: feed.category
    });
  }
  return { label: feed.label, items };
}
async function fetchFeed(feed) {
  const url = 'https://news.google.com/rss/search?q=' + encodeURIComponent(feed.query) + '&hl=en-US&gl=US&ceid=US:en';
  const response = await fetch(url, {
    headers: { 'user-agent': '7MANGA-News/2.0', accept: 'application/rss+xml, application/xml, text/xml' },
    signal: AbortSignal.timeout(6500)
  });
  if (!response.ok) throw new Error(feed.id + ': HTTP ' + response.status);
  const xml = await response.text();
  if (!xml || !/<(?:rss|feed|rdf:RDF)\b/i.test(xml)) throw new Error(feed.id + ': invalid feed response');
  const result = parseFeed(xml, feed);
  if (!result.items.length) throw new Error(feed.id + ': no usable stories');
  return result;
}
module.exports = async function handler(req, res) {
  if (req.method !== 'GET') {
    res.setHeader('Allow', 'GET');
    return res.status(405).json({ error: 'Method not allowed' });
  }
  res.setHeader('Cache-Control', 'public, s-maxage=300, stale-while-revalidate=600');
  if (memoryCache.value && memoryCache.expiresAt > Date.now()) {
    return res.status(200).json({ ...memoryCache.value, cached: true });
  }
  const results = await Promise.allSettled(FEEDS.map(fetchFeed));
  const feeds = results.filter(x => x.status === 'fulfilled').map(x => x.value);
  const errors = results.filter(x => x.status === 'rejected').map(x => String(x.reason?.message || 'Provider unavailable'));
  const unique = new Map();
  feeds.flatMap(feed => feed.items)
    .sort((a, b) => Date.parse(b.pubDate || 0) - Date.parse(a.pubDate || 0))
    .forEach(item => {
      const key = item.title.toLowerCase().replace(/[^a-z0-9]/g, '').slice(0, 120);
      if (!unique.has(key)) unique.set(key, item);
    });
  const items = [...unique.values()].slice(0, 80);
  if (!items.length && memoryCache.value) {
    return res.status(200).json({ ...memoryCache.value, stale: true, providerErrors: errors });
  }
  if (!items.length) {
    console.error('All 7MANGA news providers failed:', errors);
    return res.status(502).json({ error: 'All news providers are temporarily unavailable.', providerErrors: errors });
  }
  const payload = {
    feeds: [{ label: '7MANGA News Desk', items }],
    updatedAt: new Date().toISOString(),
    providerCount: feeds.length,
    unavailableProviders: errors.length,
    providerErrors: errors
  };
  memoryCache = { expiresAt: Date.now() + CACHE_TTL, value: payload };
  return res.status(200).json(payload);
};
module.exports.config = { maxDuration: 15 };
