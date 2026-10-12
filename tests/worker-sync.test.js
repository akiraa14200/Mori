'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

(async () => {
  const source = fs.readFileSync(path.join(__dirname, '..', 'cloudflare', 'worker.js'), 'utf8');
  const worker = await import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'));
  const statements = [];
  let recordedError = false;
  const db = {
    prepare(sql) {
      statements.push(sql);
      return {
        bind() { return this; },
        async first() {
          if (sql.includes('COUNT(*) AS count FROM characters')) return { count: 3 };
          return null;
        },
        async run() {
          if (sql.includes("key='last_error'") || sql.includes("VALUES('last_error'")) recordedError = true;
          return { success: true };
        }
      };
    },
    async batch(queries) { statements.push('BATCH:' + queries.length); return []; }
  };
  const originalFetch = global.fetch;
  global.fetch = async url => {
    const table = new URL(url).pathname.split('/').pop();
    return new Response(JSON.stringify(table === 'titles' ? [{
      id: 'title-1', name: 'Test title', media_type: 'MANGA'
    }] : []), { status: 200, headers: { 'Content-Type': 'application/json' } });
  };
  try {
    let scheduledWork;
    await worker.default.scheduled({}, { DB: db, SUPABASE_URL: 'https://example.supabase.co', SUPABASE_KEY: 'test' }, {
      waitUntil(promise) { scheduledWork = promise; }
    });
    await scheduledWork;
    assert.equal(statements.some(sql => sql.includes('DELETE FROM titles')), false,
      'an empty upstream character list must not delete the live title cache');
    assert.equal(statements.some(sql => sql.includes('DELETE FROM characters')), false,
      'an empty upstream character list must not delete the live character cache');
    assert.equal(recordedError, true, 'failed sync should record a last_error status');
    console.log('Worker sync safety test passed: empty source characters preserve the live cache.');
  } finally {
    global.fetch = originalFetch;
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
