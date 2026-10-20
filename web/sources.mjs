import { URL } from 'node:url';

const SOURCE_ID = /^[a-z0-9][a-z0-9-_-]{0,31}$/;
const ALLOWED_TYPES = new Set(['hdao', 'apple-cms']);

export const DEFAULT_SOURCES = Object.freeze([
  Object.freeze({
    id: 'hdao',
    name: 'Hazix 主片库',
    type: 'hdao',
    baseUrl: 'https://hdao.tv/api',
    enabled: true,
  }),
]);

function nonEmpty(value) {
  return typeof value === 'string' && value.trim() ? value.trim() : null;
}

function sourceUrl(value) {
  const parsed = new URL(value);
  if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('影视源地址只支持 HTTP 或 HTTPS');
  if (parsed.username || parsed.password) throw new Error('影视源地址不能包含账号或密码');
  return parsed;
}

export function normalizeSourceConfig(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const id = nonEmpty(value.id)?.toLowerCase();
  const name = nonEmpty(value.name);
  const type = nonEmpty(value.type)?.toLowerCase();
  const baseUrl = nonEmpty(value.baseUrl);
  if (!id || !SOURCE_ID.test(id) || !name || !type || !ALLOWED_TYPES.has(type) || !baseUrl) return null;
  try { sourceUrl(baseUrl); } catch { return null; }
  return {
    id,
    name: name.slice(0, 80),
    type,
    baseUrl: baseUrl.replace(/\/+$/, ''),
    enabled: value.enabled !== false,
    categoryMap: value.categoryMap && typeof value.categoryMap === 'object' ? value.categoryMap : {},
  };
}

export function parseSourceConfig(text) {
  if (!text) return DEFAULT_SOURCES.map(source => ({ ...source, categoryMap: {} }));
  let value;
  try { value = JSON.parse(text); } catch { throw new Error('影视源配置不是有效的 JSON'); }
  const list = Array.isArray(value) ? value : value.sources;
  if (!Array.isArray(list)) throw new Error('影视源配置必须是数组或包含 sources 数组的对象');
  const normalized = list.map(normalizeSourceConfig).filter(Boolean);
  if (!normalized.length) throw new Error('影视源配置没有可用的影视源');
  const ids = new Set();
  return normalized.filter(source => {
    if (ids.has(source.id)) return false;
    ids.add(source.id);
    return source.enabled;
  });
}

export function sourceConfigsFromEnv(value = process.env.HDAO_SOURCES_JSON) {
  return parseSourceConfig(value);
}

function encoded(value) {
  return encodeURIComponent(String(value ?? ''));
}

async function fetchJson(url, { signal } = {}) {
  const response = await fetch(url, {
    headers: { Accept: 'application/json', 'User-Agent': 'Hdao-WebPreview/1.0' },
    signal: signal || AbortSignal.timeout(20_000),
    redirect: 'error',
  });
  if (!response.ok) throw new Error(`影视源返回 ${response.status}`);
  return response.json();
}

function joinUrl(baseUrl, suffix) {
  const base = new URL(baseUrl);
  const path = suffix.replace(/^\/+/, '');
  base.pathname = `${base.pathname.replace(/\/+$/, '')}/${path}`;
  return base;
}

function hdaoAdapter(config) {
  const request = suffix => fetchJson(joinUrl(config.baseUrl, suffix));
  return {
    ...config,
    capabilities: ['featured', 'category', 'search', 'detail'],
    featured: () => request('vods/featured'),
    category: ({ category, page = 1, limit = 40 }) => {
      const url = joinUrl(config.baseUrl, 'vods');
      url.search = new URLSearchParams({ category, page: String(page), limit: String(limit) });
      return fetchJson(url);
    },
    search: ({ query }) => {
      const url = joinUrl(config.baseUrl, 'search');
      url.searchParams.set('q', query);
      return fetchJson(url);
    },
    detail: ({ id }) => request(`vods/${encodeURIComponent(id)}`),
  };
}

function cmsUrl(config, params) {
  const url = new URL(config.baseUrl);
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value));
  }
  return url;
}

function cmsText(value) {
  return nonEmpty(value);
}

function splitParts(value, separator) {
  return String(value || '').split(separator).map(part => part.trim()).filter(Boolean);
}

function normalizeCmsEpisode(raw, index, sourceId, sourceName) {
  const [name, originalUrl] = String(raw).split('$');
  if (!/^https?:\/\//i.test(originalUrl || '')) return null;
  return {
    id: `${sourceId}:episode:${index}`,
    name: name || `第${index + 1}集`,
    originalUrl,
    sortOrder: index,
    sourceId,
    sourceName,
  };
}

function normalizeCmsVod(raw, config) {
  const vodId = Number(raw.vod_id || raw.vodId || raw.id);
  if (!Number.isInteger(vodId) || vodId <= 0) return null;
  const title = cmsText(raw.vod_name || raw.title || raw.vodName) || '未命名';
  return {
    vodId,
    title,
    coverUrl: cmsText(raw.vod_pic || raw.vod_pic_thumb || raw.coverUrl),
    backdropUrl: cmsText(raw.vod_pic_slide || raw.vodBackdrop || raw.backdropUrl),
    description: cmsText(raw.vod_content || raw.vod_blurb || raw.description),
    year: cmsText(raw.vod_year || raw.year),
    area: cmsText(raw.vod_area || raw.area),
    genres: cmsText(raw.vod_class || raw.type_name || raw.genres),
    remarks: cmsText(raw.vod_remarks || raw.remarks),
    score: cmsText(raw.vod_score || raw.score),
    director: cmsText(raw.vod_director || raw.director),
    actors: cmsText(raw.vod_actor || raw.actors),
    typeId: Number(raw.type_id || raw.typeId) || null,
    sourceId: config.id,
    sourceName: config.name,
    mediaType: /剧|番|综艺|纪录/.test(`${raw.type_name || ''}${raw.vod_class || ''}`) ? 'tv' : 'movie',
  };
}

function normalizeCmsDetail(raw, config) {
  const item = normalizeCmsVod(raw, config);
  if (!item) throw new Error('CMS 源详情缺少有效影片 ID');
  const playUrls = splitParts(raw.vod_play_url, '\$\$\$');
  const episodes = playUrls
    .flatMap(group => splitParts(group, '#'))
    .map((episode, index) => normalizeCmsEpisode(episode, index, config.id, config.name))
    .filter(Boolean);
  return { item, episodes, related: [] };
}

function appleCmsAdapter(config) {
  return {
    ...config,
    capabilities: ['category', 'search', 'detail'],
    featured: async () => ({ hero: [], movies: [], tv: [], variety: [], documentary: [], anime: [], shortDrama: [] }),
    category: async ({ category, page = 1, limit = 40 }) => {
      const typeId = config.categoryMap?.[category];
      const root = await fetchJson(cmsUrl(config, { ac: 'detail', t: typeId, pg: page, limit }));
      return {
        items: (root.list || []).map(raw => normalizeCmsVod(raw, config)).filter(Boolean),
        page: Number(root.page || page),
        totalPages: Number(root.pagecount || 1),
      };
    },
    search: async ({ query }) => {
      const root = await fetchJson(cmsUrl(config, { ac: 'detail', wd: query }));
      return { items: (root.list || []).map(raw => normalizeCmsVod(raw, config)).filter(Boolean) };
    },
    detail: async ({ id }) => {
      const root = await fetchJson(cmsUrl(config, { ac: 'detail', ids: id }));
      const raw = root.list?.[0];
      if (!raw) throw new Error('CMS 源没有找到这部影片');
      return normalizeCmsDetail(raw, config);
    },
  };
}

export function createSourceRegistry(configs = sourceConfigsFromEnv()) {
  const adapters = configs.map(config => config.type === 'apple-cms' ? appleCmsAdapter(config) : hdaoAdapter(config));
  return new Map(adapters.map(adapter => [adapter.id, adapter]));
}

export function publicSourceList(registry) {
  return [...registry.values()].map(source => ({
    id: source.id,
    name: source.name,
    type: source.type,
    capabilities: source.capabilities,
  }));
}

export function sourceForRequest(registry, url) {
  const requested = url.searchParams.get('source') || 'hdao';
  return registry.get(requested) || null;
}

export function annotateSource(value, source) {
  if (!value || typeof value !== 'object') return value;
  if (Array.isArray(value)) {
    value.forEach(item => annotateSource(item, source));
    return value;
  }
  if ((value.vodId || value.vod_id || value.id) && (value.title || value.vod_name || value.vodName)) {
    value.sourceId ||= source.id;
    value.sourceName ||= source.name;
  }
  for (const child of Object.values(value)) annotateSource(child, source);
  return value;
}

export { encoded };
