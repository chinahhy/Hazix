import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { Readable } from 'node:stream';

const root = path.resolve(fileURLToPath(new URL('./public/', import.meta.url)));
const hlsBundle = fileURLToPath(new URL('./node_modules/hls.js/dist/hls.min.js', import.meta.url));
const port = Number(process.env.PORT || 4173);
const host = process.env.HOST || '127.0.0.1';
// Kept in step with the released TV build; the preview's check-update entry and
// the packaged app must report the same version or the comparison misleads.
const APP_VERSION = process.env.HDAO_VERSION || '3.6.4';
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.png': 'image/png', '.svg': 'image/svg+xml' };
const cache = new Map();
const ratingCache = new Map();

const validScore = value => {
  const score = Number(value);
  return score > 0 && score <= 10;
};

export function tmdbScoreFromHTML(html) {
  const match = String(html).match(/class="[^"]*user_score_chart[^"]*"[^>]*data-percent="(\d{1,3})"/)
    || String(html).match(/data-percent="(\d{1,3})"[^>]*class="[^"]*user_score_chart[^"]*"/);
  const percent = Number(match?.[1]);
  return percent > 0 && percent <= 100 ? (percent / 10).toFixed(1) : null;
}

export function tmdbMediaType(item) {
  const parentTypeId = Number(item?.parentTypeId), typeId = Number(item?.typeId);
  // The source groups documentaries under the movie parent even when their
  // TMDB records are TV series, so that category needs an explicit override.
  return parentTypeId === 1 && typeId !== 20 ? 'movie' : 'tv';
}

function collectVods(value, output = []) {
  if (!value || typeof value !== 'object') return output;
  if (Array.isArray(value)) {
    for (const child of value) collectVods(child, output);
    return output;
  }
  if (Number(value.vodId || value.id) > 0 && (value.title || value.vodName)) output.push(value);
  for (const child of Object.values(value)) collectVods(child, output);
  return output;
}

async function tmdbPageScore(tmdbId, mediaType) {
  const key = `${mediaType}:${tmdbId}`;
  const saved = ratingCache.get(key);
  if (saved && Date.now() - saved.at < (saved.score ? 86_400_000 : 600_000)) return saved.score;
  try {
    const response = await fetch(`https://www.themoviedb.org/${mediaType}/${tmdbId}?language=zh-CN`, {
      headers: { Accept: 'text/html', 'Accept-Language': 'zh-CN,zh;q=0.9', 'User-Agent': 'Hdao-WebPreview/1.0' },
      signal: AbortSignal.timeout(8_000),
      redirect: 'follow',
    });
    const score = response.ok ? tmdbScoreFromHTML(await response.text()) : null;
    if (ratingCache.size > 1_000) ratingCache.delete(ratingCache.keys().next().value);
    ratingCache.set(key, { at: Date.now(), score });
    return score;
  } catch {
    ratingCache.set(key, { at: Date.now(), score: null });
    return null;
  }
}

async function enrichMissingRatings(data) {
  const groups = new Map();
  for (const item of collectVods(data)) {
    if (validScore(item.doubanScore) || validScore(item.tmdbScore)) continue;
    const tmdbId = Number(item.tmdbId);
    if (!Number.isInteger(tmdbId) || tmdbId <= 0) continue;
    const mediaType = tmdbMediaType(item), key = `${mediaType}:${tmdbId}`;
    if (!groups.has(key)) groups.set(key, { tmdbId, mediaType, items: [] });
    groups.get(key).items.push(item);
  }
  await Promise.all([...groups.values()].map(async group => {
    const score = await tmdbPageScore(group.tmdbId, group.mediaType);
    if (score) for (const item of group.items) item.tmdbScore = score;
  }));
  return data;
}

function json(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(body));
}

export function apiTarget(url) {
  if (!/^\/api\/(vods(?:\/featured|\/\d+)?|search)$/.test(url.pathname)) return null;
  const target = new URL(url.pathname, 'https://hdao.tv');
  for (const key of ['category', 'page', 'limit', 'q']) {
    if (url.searchParams.has(key)) target.searchParams.set(key, url.searchParams.get(key).slice(0, 200));
  }
  return target;
}

const server = http.createServer(async (req, res) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'no-referrer');
  if (!['GET', 'HEAD'].includes(req.method)) return json(res, 405, { error: '仅支持读取请求' });
  const url = new URL(req.url, `http://${req.headers.host}`);
  try {
    if (url.pathname.startsWith('/api/')) {
      const target = apiTarget(url);
      if (!target) return json(res, 404, { error: '没有这个接口' });
      const key = target.href;
      const saved = cache.get(key);
      if (saved && Date.now() - saved.at < 60_000) return json(res, 200, saved.data);
      const upstream = await fetch(target, { headers: { Accept: 'application/json', 'User-Agent': 'Hdao-WebPreview/1.0' }, signal: AbortSignal.timeout(20_000), redirect: 'error' });
      if (!upstream.ok) return json(res, 502, { error: `片库服务返回 ${upstream.status}` });
      const data = await enrichMissingRatings(await upstream.json());
      if (cache.size > 150) cache.delete(cache.keys().next().value);
      cache.set(key, { at: Date.now(), data });
      return json(res, 200, data);
    }
    if (url.pathname === '/image') {
      const src = url.searchParams.get('src') || '';
      if (!/^\/t\/p\/(original|w300|w500|w780|w1280)\/[a-zA-Z0-9._-]+$/.test(src)) return json(res, 400, { error: '无效图片路径' });
      const upstream = await fetch(`https://image.tmdb.org${src}`, { signal: AbortSignal.timeout(20_000), redirect: 'error' });
      if (!upstream.ok) { res.writeHead(upstream.status); return res.end(); }
      res.writeHead(200, { 'Content-Type': upstream.headers.get('content-type') || 'image/jpeg', 'Cache-Control': 'public, max-age=86400' });
      if (req.method === 'HEAD') return res.end();
      const stream = Readable.fromWeb(upstream.body);
      stream.on('error', () => res.destroy());
      return stream.pipe(res);
    }
    if (url.pathname === '/vendor/hls.min.js') {
      const body = await readFile(hlsBundle);
      res.writeHead(200, { 'Content-Type': 'text/javascript; charset=utf-8', 'Cache-Control': 'public, max-age=31536000, immutable' });
      return res.end(req.method === 'HEAD' ? undefined : body);
    }
    const requested = decodeURIComponent(url.pathname === '/' ? '/index.html' : url.pathname);
    const file = path.resolve(root, `.${requested}`);
    if (!file.startsWith(root + path.sep) || !types[path.extname(file)]) return json(res, 404, { error: '页面不存在' });
    let body = await readFile(file);
    // The preview mirrors the packaged app's version, so the check-update entry
    // has something real to compare against. Injected here instead of baked into
    // the HTML so a running preview always reports the version in app.yaml.
    if (path.basename(file) === 'app.html') {
      body = Buffer.from(body.toString('utf8').replace('<!-- version -->', `<script>window.__HDAO_VERSION__=${JSON.stringify(APP_VERSION)}</script>`));
    }
    res.writeHead(200, { 'Content-Type': types[path.extname(file)], 'Cache-Control': 'no-cache' });
    res.end(req.method === 'HEAD' ? undefined : body);
  } catch (error) {
    if (res.headersSent) return res.destroy();
    if (error.code === 'ENOENT') return json(res, 404, { error: '页面不存在' });
    console.warn(`[preview] ${url.pathname}: ${error.name}`);
    json(res, 502, { error: '暂时连接不上片库，请检查网络后重试' });
  }
});

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  server.listen(port, host, () => console.log(`网页预览：http://${host}:${port}`));
}
