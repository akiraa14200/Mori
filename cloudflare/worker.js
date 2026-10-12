const CORS={'Access-Control-Allow-Origin':'*','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Allow-Headers':'Content-Type,Authorization,X-Sync-Secret','Access-Control-Max-Age':'86400'};
const json=(data,status=200)=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json; charset=utf-8','Cache-Control':'public, max-age=30, stale-while-revalidate=120',...CORS}});
const arr=v=>Array.isArray(v)?JSON.stringify(v):'[]',flag=v=>v?1:0,safeJson=v=>{try{return JSON.parse(v||'[]')}catch{return []}};
async function fetchAll(env,table,columns){const out=[];for(let offset=0;offset<100000;offset+=500){const url=env.SUPABASE_URL.replace(/\/$/,'')+'/rest/v1/'+table+'?select='+encodeURIComponent(columns)+'&order=id.asc';const res=await fetch(url,{headers:{apikey:env.SUPABASE_KEY,Authorization:'Bearer '+env.SUPABASE_KEY,Range:offset+'-'+(offset+499),'Range-Unit':'items'}});if(!res.ok)throw new Error('Supabase '+table+' read failed: HTTP '+res.status+' '+(await res.text()).slice(0,200));const rows=await res.json();out.push(...rows);if(rows.length<500)return out;}throw new Error('Safety limit reached while syncing '+table);}
async function syncCatalog(env){
 const runId=crypto.randomUUID(),now=new Date().toISOString();
 const [titles,characters]=await Promise.all([fetchAll(env,'titles','id,name,alternative_names,media_type,synopsis,cover_url,banner_url,genres,themes,status,release_year,season,rating,popularity,chapters,episodes,author,artist,studio,country,source_url,anilist_id,trailer_url,is_trending,is_featured,created_at,updated_at'),fetchAll(env,'characters','id,title_id,name,role,description,image_url,created_at')]);
 if(!titles.length)throw new Error('Sync aborted: source catalogue is empty; existing D1 data preserved.');
 // An empty character response is suspicious when the cache already contains character records.
 // Preserve the previous character cache instead of silently deleting every profile on a transient upstream failure.
 if(!characters.length){const existing=await env.DB.prepare('SELECT COUNT(*) AS count FROM characters').first();if(Number(existing&&existing.count||0)>0)throw new Error('Sync aborted: source characters are empty while D1 has cached characters; existing catalogue preserved.');}
 // Populate staging tables first. Any fetch or write failure leaves the live catalogue untouched.
 await env.DB.batch([
  env.DB.prepare('DROP TABLE IF EXISTS titles_stage'),
  env.DB.prepare('DROP TABLE IF EXISTS characters_stage'),
  env.DB.prepare('CREATE TABLE titles_stage AS SELECT * FROM titles WHERE 0'),
  env.DB.prepare('CREATE TABLE characters_stage AS SELECT * FROM characters WHERE 0')
 ]);
 const titleColumns='id,name,alternative_names,media_type,synopsis,cover_url,banner_url,genres,themes,status,release_year,season,rating,popularity,chapters,episodes,author,artist,studio,country,source_url,anilist_id,trailer_url,is_trending,is_featured,created_at,updated_at,synced_at,sync_run_id';
 const characterColumns='id,title_id,name,role,description,image_url,created_at,synced_at,sync_run_id';
 const tq='INSERT INTO titles_stage('+titleColumns+') VALUES('+Array(29).fill('?').join(',')+')';
 const cq='INSERT INTO characters_stage('+characterColumns+') VALUES('+Array(9).fill('?').join(',')+')';
 for(let i=0;i<titles.length;i+=25)await env.DB.batch(titles.slice(i,i+25).map(t=>env.DB.prepare(tq).bind(t.id,t.name,arr(t.alternative_names),t.media_type,t.synopsis||'',t.cover_url||'',t.banner_url||'',arr(t.genres),arr(t.themes),t.status||'Ongoing',t.release_year??null,t.season??null,t.rating??null,t.popularity??0,t.chapters??null,t.episodes??null,t.author??null,t.artist??null,t.studio??null,t.country??null,t.source_url??null,t.anilist_id??null,t.trailer_url??null,flag(t.is_trending),flag(t.is_featured),t.created_at??null,t.updated_at??null,now,runId)));
 for(let i=0;i<characters.length;i+=25)await env.DB.batch(characters.slice(i,i+25).map(c=>env.DB.prepare(cq).bind(c.id,c.title_id,c.name,c.role||'Supporting',c.description||'',c.image_url||'',c.created_at??null,now,runId)));
 // D1 batch is transactional: publish both tables together only after staging is complete.
 await env.DB.batch([
  env.DB.prepare('DELETE FROM titles'),
  env.DB.prepare('INSERT INTO titles ('+titleColumns+') SELECT '+titleColumns+' FROM titles_stage WHERE sync_run_id = ?').bind(runId),
  env.DB.prepare('DELETE FROM characters'),
  env.DB.prepare('INSERT INTO characters ('+characterColumns+') SELECT '+characterColumns+' FROM characters_stage WHERE sync_run_id = ?').bind(runId),
  env.DB.prepare("INSERT INTO sync_state(key,value,updated_at) VALUES('last_success',?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value,updated_at=excluded.updated_at").bind(JSON.stringify({at:now,titles:titles.length,characters:characters.length}),now),
  env.DB.prepare("DELETE FROM sync_state WHERE key='last_error'"),
  env.DB.prepare('DELETE FROM titles_stage'),
  env.DB.prepare('DELETE FROM characters_stage')
 ]);
 return {ok:true,titles:titles.length,characters:characters.length,syncedAt:now};
}
function normalizeTitle(row){row.alternative_names=safeJson(row.alternative_names);row.genres=safeJson(row.genres);row.themes=safeJson(row.themes);row.is_trending=!!row.is_trending;row.is_featured=!!row.is_featured;delete row.sync_run_id;delete row.synced_at;return row}
export default {async fetch(request,env){const url=new URL(request.url);if(request.method==='OPTIONS')return new Response(null,{status:204,headers:CORS});try{
 if(url.pathname==='/health'){const [t,c,s,e]=await Promise.all([env.DB.prepare('SELECT COUNT(*) AS count FROM titles').first(),env.DB.prepare('SELECT COUNT(*) AS count FROM characters').first(),env.DB.prepare("SELECT value,updated_at FROM sync_state WHERE key='last_success'").first(),env.DB.prepare("SELECT value,updated_at FROM sync_state WHERE key='last_error'").first()]);return json({ok:true,service:'mori-hybrid-catalog',titles:t.count,characters:c.count,lastSync:s?safeJson(s.value):null,lastSyncAt:s?s.updated_at:null,lastSyncError:e?safeJson(e.value):null});}
 if(url.pathname==='/sync'&&request.method==='POST'){if(!env.SYNC_SECRET||request.headers.get('X-Sync-Secret')!==env.SYNC_SECRET)return json({error:'Unauthorized'},401);return json(await syncCatalog(env));}
 if(url.pathname==='/api/titles'&&request.method==='GET'){const requestedLimit=Number(url.searchParams.get('limit')||250),requestedOffset=Number(url.searchParams.get('offset')||0);if(!Number.isSafeInteger(requestedLimit)||!Number.isSafeInteger(requestedOffset)||requestedLimit<1||requestedOffset<0)return json({error:'limit must be a positive integer and offset must be a non-negative integer'},400);const limit=Math.min(250,requestedLimit),offset=requestedOffset,q=(url.searchParams.get('q')||'').trim().slice(0,100),type=(url.searchParams.get('media_type')||'').trim().slice(0,40),where=[],values=[];if(q){where.push('(name LIKE ? OR alternative_names LIKE ? OR synopsis LIKE ?)');values.push('%'+q+'%','%'+q+'%','%'+q+'%')}if(type){where.push('media_type = ?');values.push(type)}let sql='SELECT * FROM titles';if(where.length)sql+=' WHERE '+where.join(' AND ');sql+=' ORDER BY created_at DESC, id ASC LIMIT ? OFFSET ?';values.push(limit,offset);const result=await env.DB.prepare(sql).bind(...values).all();return json({data:(result.results||[]).map(normalizeTitle),source:'cloudflare-d1',count:(result.results||[]).length});}
 if(url.pathname==='/api/characters'&&request.method==='GET'){const id=url.searchParams.get('title_id');if(!id)return json({error:'title_id is required'},400);const requestedLimit=Number(url.searchParams.get('limit')||100),requestedOffset=Number(url.searchParams.get('offset')||0);if(!Number.isSafeInteger(requestedLimit)||!Number.isSafeInteger(requestedOffset)||requestedLimit<1||requestedOffset<0)return json({error:'limit must be a positive integer and offset must be a non-negative integer'},400);const limit=Math.min(100,requestedLimit),offset=requestedOffset;const result=await env.DB.prepare('SELECT id,title_id,name,role,description,image_url,created_at FROM characters WHERE title_id=? ORDER BY created_at DESC LIMIT ? OFFSET ?').bind(id,limit,offset).all();return json({data:result.results||[],source:'cloudflare-d1',count:(result.results||[]).length});}
 return json({error:'Not found'},404);
 }catch(error){return json({error:'Hybrid catalogue request failed',message:String(error&&error.message||error)},500)}},async scheduled(controller,env,ctx){ctx.waitUntil(syncCatalog(env).catch(async error=>{console.error('Scheduled catalogue sync failed',error);try{const at=new Date().toISOString();await env.DB.prepare("INSERT INTO sync_state(key,value,updated_at) VALUES('last_error',?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value,updated_at=excluded.updated_at").bind(JSON.stringify({at,message:String(error&&error.message||error)}),at).run()}catch(recordError){console.error('Could not record sync failure',recordError)}}))}};
