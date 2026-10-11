const crypto = require('node:crypto');

const ENDPOINT = process.env.B2_S3_ENDPOINT || '';
const BUCKET = process.env.B2_BUCKET_NAME || '';
const ACCESS_KEY = process.env.B2_KEY_ID || '';
const SECRET_KEY = process.env.B2_APPLICATION_KEY || '';
const REGION = (new URL(ENDPOINT || 'https://s3.us-east-005.backblazeb2.com')).hostname.split('.')[1] || 'us-east-005';
const SUPABASE_URL = process.env.SUPABASE_URL || '';
const SUPABASE_KEY = process.env.SUPABASE_ANON_KEY || '';

function sha256(value) {
  return crypto.createHash('sha256').update(value).digest('hex');
}
function hmac(key, value, encoding) {
  return crypto.createHmac('sha256', key).update(value).digest(encoding);
}
function awsEncode(value) {
  return encodeURIComponent(value).replace(/[!'()*]/g, c => '%' + c.charCodeAt(0).toString(16).toUpperCase());
}
function encodePath(value) {
  return String(value).split('/').map(awsEncode).join('/');
}
function presign(method, key, contentType = '') {
  const endpoint = new URL(ENDPOINT);
  const host = endpoint.host;
  const date = new Date();
  const amzDate = date.toISOString().replace(/[:-]|\.\d{3}/g, '');
  const dateStamp = amzDate.slice(0, 8);
  const scope = dateStamp + '/' + REGION + '/s3/aws4_request';
  const canonicalUri = (endpoint.pathname.replace(/\/$/, '') || '') + '/' + encodePath(BUCKET + '/' + key);
  const headers = contentType ? 'content-type:' + contentType.trim() + '\nhost:' + host : 'host:' + host;
  const signedHeaders = contentType ? 'content-type;host' : 'host';
  const params = {
    'X-Amz-Algorithm': 'AWS4-HMAC-SHA256',
    'X-Amz-Credential': ACCESS_KEY + '/' + scope,
    'X-Amz-Date': amzDate,
    'X-Amz-Expires': '3600',
    'X-Amz-SignedHeaders': signedHeaders
  };
  const canonicalQuery = Object.keys(params).sort().map(k => awsEncode(k) + '=' + awsEncode(params[k])).join('&');
  const canonicalRequest = [method, canonicalUri, canonicalQuery, headers, signedHeaders, 'UNSIGNED-PAYLOAD'].join('\n');
  const stringToSign = ['AWS4-HMAC-SHA256', amzDate, scope, sha256(canonicalRequest)].join('\n');
  const kDate = hmac('AWS4' + SECRET_KEY, dateStamp);
  const kRegion = hmac(kDate, REGION);
  const kService = hmac(kRegion, 's3');
  const kSigning = hmac(kService, 'aws4_request');
  const signature = hmac(kSigning, stringToSign, 'hex');
  return ENDPOINT.replace(/\/$/, '') + canonicalUri + '?' + canonicalQuery + '&X-Amz-Signature=' + signature;
}
function json(res, status, body) {
  res.setHeader('Cache-Control', 'no-store');
  return res.status(status).json(body);
}
async function getUser(req) {
  const auth = req.headers.authorization || '';
  if (!auth.startsWith('Bearer ') || !SUPABASE_URL || !SUPABASE_KEY) return null;
  const response = await fetch(SUPABASE_URL.replace(/\/$/, '') + '/auth/v1/user', {
    headers: { apikey: SUPABASE_KEY, authorization: auth }
  });
  if (!response.ok) return null;
  return response.json();
}
function safeKey(key) {
  return typeof key === 'string' &&
    key.length < 600 &&
    !key.includes('..') &&
    !key.startsWith('/') &&
    /^(avatars|community-media)\/[0-9a-f-]{36}\/[a-zA-Z0-9._-]+\.(jpg|jpeg|png|webp|gif)$/i.test(key);
}
function extFor(type) {
  return ({ 'image/jpeg': 'jpg', 'image/png': 'png', 'image/webp': 'webp', 'image/gif': 'gif' })[type] || '';
}
module.exports = async function handler(req, res) {
  if (!ENDPOINT || !BUCKET || !ACCESS_KEY || !SECRET_KEY) return json(res, 503, { error: 'Media storage is not configured on the server.' });
  if (req.method === 'GET') {
    const key = String(req.query?.key || '');
    if (!safeKey(key)) return json(res, 400, { error: 'Invalid media key.' });
    const url = presign('GET', key);
    res.setHeader('Cache-Control', 'public, max-age=300, s-maxage=300, stale-while-revalidate=600');
    res.setHeader('Location', url);
    return res.status(302).end();
  }
  if (req.method === 'POST') {
    const user = await getUser(req);
    if (!user?.id) return json(res, 401, { error: 'Sign in to upload media.' });
    const body = typeof req.body === 'string' ? (() => { try { return JSON.parse(req.body); } catch { return {}; } })() : (req.body || {});
    const folder = body.folder;
    const type = body.contentType;
    const size = Number(body.size || 0);
    const ext = extFor(type);
    if (!['avatars', 'community-media'].includes(folder) || !ext || !Number.isFinite(size) || size <= 0 || size > 8 * 1024 * 1024 || (type === 'image/gif' && size > 5 * 1024 * 1024)) {
      return json(res, 400, { error: 'Unsupported image or file size. Images must be under 8 MB and GIFs under 5 MB.' });
    }
    const key = folder + '/' + user.id + '/' + Date.now() + '-' + crypto.randomBytes(12).toString('hex') + '.' + ext;
    return json(res, 200, { uploadUrl: presign('PUT', key, type), key, mediaUrl: '/api/media?key=' + encodeURIComponent(key), contentType: type });
  }
  if (req.method === 'DELETE') {
    const user = await getUser(req);
    if (!user?.id) return json(res, 401, { error: 'Sign in to delete media.' });
    const body = typeof req.body === 'string' ? (() => { try { return JSON.parse(req.body); } catch { return {}; } })() : (req.body || {});
    const key = String(body.key || '');
    if (!safeKey(key) || !key.split('/')[1] || key.split('/')[1] !== user.id) return json(res, 403, { error: 'You can only delete your own media.' });
    const response = await fetch(presign('DELETE', key), { method: 'DELETE' });
    if (!response.ok && response.status !== 404) return json(res, 502, { error: 'Could not remove media from storage.' });
    return json(res, 200, { ok: true });
  }
  res.setHeader('Allow', 'GET, POST, DELETE');
  return json(res, 405, { error: 'Method not allowed.' });
};
module.exports.config = { maxDuration: 15 };
