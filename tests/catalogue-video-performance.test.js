'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.join(__dirname, '..');
const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
const worker = fs.readFileSync(path.join(root, 'cloudflare', 'worker.js'), 'utf8');

test('related videos use normalized Supabase rows, not a whole JSON setting', () => {
  assert.match(html, /from\(['"]related_videos['"]\)/);
  assert.doesNotMatch(html, /eq\(['"]key['"],\s*['"]related_videos_by_title['"]\)/);
});

test('Videos feed is bounded and supports incremental pages', () => {
  assert.match(html, /VIDEO_FEED_PAGE_SIZE\s*=\s*24/);
  assert.match(html, /range\(offset,\s*offset\s*\+\s*VIDEO_FEED_PAGE_SIZE\s*-\s*1\)/);
  assert.match(html, /data-video-load-more/);
});

test('public catalogue uses bounded Worker pages with a Supabase fallback', () => {
  assert.match(html, /cloudflareD1Url[^\n]*\/api\/titles/);
  assert.match(html, /CATALOGUE_PAGE_SIZE\s*=\s*250/);
  assert.match(html, /fetchTitlesRestPage\(/);
  assert.match(worker, /limit:\s*Math\.min\(250/);
});

test('entry music starts collapsed with its title visible and does not autoplay', () => {
  assert.match(html, /data-entry-music-expand[^>]*aria-expanded="false"/);
  assert.match(html, /entry-music-title/);
  assert.doesNotMatch(html, /function startEntryMusicForCurrentEntry\(\)[\s\S]{0,1000}?\.play\(\)/);
});

test('video embeds remain click-to-load and thumbnails remain lazy', () => {
  assert.match(html, /data-single-video-play/);
  assert.match(html, /loading="lazy" decoding="async" fetchpriority="low"/);
});
