const FEEDS = [
  {
    label: 'Anime announcements',
    url: 'https://news.google.com/rss/search?q=anime+announcement+OR+anime+adaptation+OR+anime+sequel&hl=en-US&gl=US&ceid=US:en'
  },
  {
    label: 'Manga & manhwa',
    url: 'https://news.google.com/rss/search?q=manga+OR+manhwa+release+OR+chapter+announcement&hl=en-US&gl=US&ceid=US:en'
  },
  {
    label: 'Creators & industry',
    url: 'https://news.google.com/rss/search?q=manga+author+interview+OR+manga+artist+OR+anime+studio+news&hl=en-US&gl=US&ceid=US:en'
  }
];

function decodeXml(value = '') {
  return String(value)
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n)))
    .replace(/&#x([\da-f]+);/gi, (_, n) => String.fromCodePoint(parseInt(n, 16)));
}

function tagValue(xml, tag) {
  const match = xml.match(new RegExp('<' + tag + '(?:\\s[^>]*)?>([\\s\\S]*?)<\\/' + tag + '\\s*>', 'i'));
  return match ? decodeXml(match[1]).trim() : '';
}

function cleanText(value) {
  return decodeXml(value).replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();
}

function getLink(block) {
  const atom = block.match(/<link\b[^>]*href=["']([^"']+)["'][^>]*>/i);
  return decodeXml(atom ? atom[1] : tagValue(block, 'link') || tagValue(block, 'guid'));
}

function getImage(block, description) {
  const tags = block.match(/<(?:media:thumbnail|media:content|enclosure)\b[^>]*\/?\s*>/gi) || [];
  const candidates = [];
  const attr = (tag, name) => {
    const match = tag.match(new RegExp(name + "\\s*=\\s*['\"]([^'\"]+)['\"]", 'i'));
    return match ? decodeXml(match[1]).trim() : '';
  };
  for (const tag of tags) {
    const url = attr(tag, 'url') || attr(tag, 'href');
    if (!/^https?:\/\//i.test(url)) continue;
    const type = attr(tag, 'type').toLowerCase();
    const medium = attr(tag, 'medium').toLowerCase();
    const imageLike = type.startsWith('image/') || medium === 'image' || /\.(?:avif|gif|jpe?g|png|webp)(?:[?#]|$)/i.test(url);
    const score = /<media:thumbnail\b/i.test(tag) ? 3 : imageLike ? 2 : /<media:content\b/i.test(tag) ? 1 : 0;
    if (score) candidates.push({ url, score });
  }
  const html = decodeXml(description);
  const imageTags = html.match(/<img\b[^>]*>/gi) || [];
  for (const tag of imageTags) {
    const src = attr(tag, 'src') || attr(tag, 'data-src') || attr(tag, 'data-original') || attr(tag, 'data-lazy-src') || attr(tag, 'data-original-src');
    const srcset = attr(tag, 'srcset').split(',')[0].trim().split(/\s+/)[0];
    const url = src || srcset;
    if (/^https?:\/\//i.test(url)) candidates.push({ url, score: 4 });
  }
  candidates.sort((a, b) => b.score - a.score);
  return candidates[0]?.url || '';
}

async function fetchFeed(feed) {
  const response = await fetch(feed.url, {
    headers: { 'user-agent': 'MoriNews/1.0 (+https://mori-z-hub.vercel.app)', accept: 'application/rss+xml, application/xml, text/xml, */*' },
    signal: AbortSignal.timeout(8500)
  });
  if (!response.ok) throw new Error('Upstream feed returned HTTP ' + response.status);
  const xml = await response.text();
  if (!xml || !/<(?:rss|feed|rdf:RDF)\b/i.test(xml)) throw new Error('Upstream did not return RSS/Atom XML');
  const blocks = xml.match(/<item\b[\s\S]*?<\/item\s*>/gi) || xml.match(/<entry\b[\s\S]*?<\/entry\s*>/gi) || [];
  const items = blocks.map(block => {
    const title = cleanText(tagValue(block, 'title'));
    const link = getLink(block);
    const description = tagValue(block, 'description') || tagValue(block, 'content:encoded') || tagValue(block, 'summary') || tagValue(block, 'content');
    const published = tagValue(block, 'pubDate') || tagValue(block, 'published') || tagValue(block, 'updated') || tagValue(block, 'dc:date');
    const source = cleanText(tagValue(block, 'source') || feed.label);
    const image = getImage(block, description);
    if (!title || !/^https?:\/\//i.test(link)) return null;
    const parsedDate = Date.parse(published);
    if (Number.isFinite(parsedDate) && Date.now() - parsedDate > 14 * 86400000) return null;
    return {
      title,
      link,
      description: cleanText(description).slice(0, 500),
      pubDate: Number.isFinite(parsedDate) ? new Date(parsedDate).toISOString() : '',
      source,
      thumbnail: image
    };
  }).filter(Boolean);
  return { label: feed.label, items };
}

module.exports = async function handler(req, res) {
  if (req.method !== 'GET') {
    res.setHeader('Allow', 'GET');
    return res.status(405).json({ error: 'Method not allowed' });
  }
  res.setHeader('Cache-Control', 'public, s-maxage=300, stale-while-revalidate=600');
  const results = await Promise.allSettled(FEEDS.map(fetchFeed));
  const feeds = results.filter(x => x.status === 'fulfilled').map(x => x.value);
  const failures = results.filter(x => x.status === 'rejected');
  if (!feeds.length) {
    console.error('All Mori news feeds failed:', failures.map(x => x.reason?.message));
    return res.status(502).json({ error: 'News sources are temporarily unavailable. Please refresh in a moment.' });
  }
  return res.status(200).json({
    feeds,
    updatedAt: new Date().toISOString(),
    unavailableFeeds: failures.length
  });
};

module.exports.config = { maxDuration: 15 };
