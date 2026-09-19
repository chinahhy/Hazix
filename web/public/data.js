export const categories = [
  ['movie', '电影', 'movies', '热映电影'], ['tv', '剧集', 'tv', '热播剧集'],
  ['variety', '综艺', 'variety', '热门综艺'], ['documentary', '纪录片', 'documentary', '精选纪录片'],
  ['anime', '动漫', 'anime', '人气动漫'], ['short-drama', '短剧', 'shortDrama', '上头短剧'],
];
export const escapeHTML = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
export function imageURL(value, size = 'w500') {
  if (!value || typeof value !== 'string') return '';
  if (value.startsWith('/')) return `/image?src=${encodeURIComponent(`/t/p/${size}/${value.replace(/^\/+/, '')}`)}`;
  try {
    const url = new URL(value);
    if (!['http:', 'https:'].includes(url.protocol)) return '';
    if (url.hostname === 'image.tmdb.org') return `/image?src=${encodeURIComponent(url.pathname)}`;
    return url.href;
  } catch { return ''; }
}
export const idOf = item => Number(item.vodId || item.id);
// Keys that should produce remote-control feedback. Mirrors the Kotlin side,
// which plays SoundEffectConstants.CLICK on every d-pad move and confirm.
const SOUND_IGNORED_KEYS = new Set(['Shift', 'Control', 'Alt', 'Meta', 'CapsLock', 'Tab', 'F5', 'F11', 'F12']);
const SOUND_MOVE_KEYS = new Set(['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight']);
const SOUND_CONFIRM_KEYS = new Set(['Enter', ' ', 'Spacebar']);
export function isFeedbackKey(key, { altKey = false, ctrlKey = false, metaKey = false } = {}) {
  if (typeof key !== 'string' || !key || SOUND_IGNORED_KEYS.has(key)) return false;
  if (altKey || ctrlKey || metaKey) return false;
  return SOUND_MOVE_KEYS.has(key) || SOUND_CONFIRM_KEYS.has(key) || key === 'Escape' || key === 'Backspace' || key === 'BrowserBack';
}
export function ratingOf(item) {
  for (const [source, value] of [['豆瓣', item.doubanScore], ['TMDB', item.tmdbScore]]) {
    const score = Number(value);
    if (score > 0 && score <= 10) return { source, score: score.toFixed(1) };
  }
  return null;
}
export const scoreOf = item => ratingOf(item)?.score || '';
export function recentHot(catalog) {
  const items = [...(catalog.movies || []).slice(0, 3), ...(catalog.tv || []).slice(0, 3)];
  return [...new Map(items.map(item => [idOf(item), item])).values()];
}
export function categoryItems(key, items) {
  return key === 'short-drama' ? items.filter(item => item.parentTypeId === 54) : items;
}
export function recentProgress(entries) {
  const ids = new Set(), titles = new Set();
  return Object.values(entries).filter(e => e && Number.isFinite(e.position) && e.position > 0 && Number.isFinite(e.updatedAt) && !(e.duration > 0 && e.position >= e.duration - 20))
    .sort((a, b) => b.updatedAt - a.updatedAt).filter(entry => {
      const title = String(entry.item?.title || '').toLowerCase().replace(/[^\p{L}\p{N}]/gu, '') || String(entry.vodId);
      if (!entry.item || ids.has(entry.vodId) || titles.has(title)) return false;
      ids.add(entry.vodId); titles.add(title); return true;
    }).slice(0, 8);
}
export function playbackURL(original) {
  const prefix = 'https://stream.hdao.tv/api/proxy/m3u8?url=';
  if (original.startsWith(prefix)) return original;
  if (!/^https?:\/\//.test(original)) throw new Error('播放地址无效');
  const bytes = new TextEncoder().encode(original);
  return prefix + btoa(Array.from(bytes, b => String.fromCharCode(b)).join('')).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
