import { categories, escapeHTML as esc, imageURL, idOf, ratingOf, categoryItems, recentProgress, playbackURL, isFeedbackKey } from './data.js';
import { playMoveSound, playConfirmSound, playBackSound } from './sound.js';

const content = document.querySelector('#content');
const icons = {
  play: '<path d="m8 5 11 7-11 7z" fill="currentColor" stroke="none"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10v1"/>',
  search: '<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>',
  back: '<path d="m14 5-7 7 7 7M7 12h14"/>',
  next: '<path d="m9 5 7 7-7 7"/>',
  prev: '<path d="m15 5-7 7 7 7"/>',
  home: '<path d="m3 10 9-7 9 7v10H3zM9 20v-7h6v7"/>',
  movie: '<rect x="3" y="5" width="18" height="15" rx="2"/><path d="M3 10h18M6 5l3 5m3-5 3 5m3-5 3 5"/>',
  tv: '<rect x="3" y="7" width="18" height="14" rx="2"/><path d="m8 2 4 5 4-5"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4.5 21a7.5 7.5 0 0 1 15 0"/>',
  hot: '<path d="M13.5 2.5c.4 3-1.6 4.5-3.3 6.2-1.5 1.5-2.4 3.1-1.3 5.1.4-1.8 1.7-2.8 3-3.6-.2 2.1 1.4 3.2 2.1 4.7.7 1.5.3 3.4-.8 4.6 3.7-.6 6.3-3.4 6.3-7.3 0-4.7-2.8-8.5-6-12.7Z"/><path d="M10.3 21c-2.3-.8-3.8-2.8-3.8-5.4 0-1.8.8-3.4 2-4.8"/>',
  update: '<path d="M20 11.5A8 8 0 0 0 6.3 6.3L4 8.5"/><path d="M4 4v4.5h4.5"/><path d="M4 12.5a8 8 0 0 0 13.7 5.2L20 15.5"/><path d="M20 20v-4.5h-4.5"/>',
};
const icon = name => `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${icons[name] || icons.movie}</svg>`;

let epoch = 0, cleanup = () => {}, heroTimer, featuredPromise;
const details = new Map();
let progress;
try { progress = JSON.parse(localStorage.getItem('hdao.web.progress.v1') || '{}'); } catch { progress = {}; }
if (!progress || Array.isArray(progress) || typeof progress !== 'object') progress = {};
let noticeTimer;
function notice(message) {
  document.querySelector('#notice').textContent = message;
  clearTimeout(noticeTimer);
  noticeTimer = setTimeout(() => { document.querySelector('#notice').textContent = ''; }, 4000);
}
async function api(path) {
  const response = await fetch(`/api${path}`, { signal: AbortSignal.timeout(25_000) });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || '内容暂时无法加载');
  return data;
}
function featured() {
  if (!featuredPromise) featuredPromise = api('/vods/featured').catch(error => { featuredPromise = null; throw error; });
  return featuredPromise;
}
async function detail(id) {
  if (!details.has(id)) {
    const value = await api(`/vods/${id}`);
    value.episodes = (value.episodes || []).filter(e => /^https?:\/\//.test(e.originalUrl)).sort((a, b) => a.sortOrder - b.sortOrder);
    details.set(id, value);
  }
  return details.get(id);
}
function nav(selected, nested) {
  document.querySelector('#nav').innerHTML = `<a class="brand" href="#home" aria-label="首页"><img src="/brand.png" alt=""></a>
    <nav class="desktop-nav" aria-label="主导航">${[['home', '首页'], ...categories].map(([key, label]) => `<a href="#${key === 'home' ? 'home' : `category/${key}`}" ${key === selected ? 'aria-current="page"' : ''}>${label}</a>`).join('')}</nav>
    <div class="nav-actions"><button class="update-link" type="button" data-update>${icon('update')}<span>检查更新</span></button><a class="search-link" href="#search" aria-label="搜索">${icon('search')}</a><a class="profile-link" href="#my" aria-label="我的">${icon('user')}</a></div>
    <nav class="mobile-nav" aria-label="手机导航">${[['home', '首页', 'home', '#home'], ['movie', '影视', 'movie', '#category/movie'], ['tv', '剧集', 'tv', '#category/tv']].map(([key, label, glyph, href]) => `<a href="${href}" ${key === selected ? 'aria-current="page"' : ''}>${icon(glyph)}<span>${label}</span></a>`).join('')}</nav>`;
  document.body.classList.toggle('nested', nested);
}
const poster = item => imageURL(item.tmdbPoster || item.coverUrl);
// Hero and detail backdrops fill large, often high-DPI surfaces. TMDB's w1280
// rendition becomes visibly soft after cover-cropping, so keep the original
// backdrop here while poster rails continue to use the lighter w500 images.
const backdrop = item => imageURL(item.tmdbBackdrop, 'original');
const summary = item => String(item.description || item.content || '暂无简介').replace(/<[^>]*>/g, '').trim();
function meta(item) {
  const rating = ratingOf(item);
  return `<div class="metadata">${rating ? `<strong>${rating.score}</strong>` : ''}${[item.year, item.area, item.genres, item.remarks].filter(Boolean).slice(0, 4).map(value => `<span>${esc(value)}</span>`).join('')}</div>`;
}
function card(item, options = {}) {
  const { featured: isFeatured, entry, landscape = false } = options;
  const url = entry ? `#play/${entry.vodId}/${entry.episodeIndex}` : `#detail/${idOf(item)}`;
  const art = landscape ? (backdrop(item) || poster(item)) : (poster(item) || backdrop(item));
  const rating = ratingOf(item);
  const meta = [item.year, item.genres || item.area].filter(Boolean).slice(0, 3).map(esc).join(' · ');
  const tail = entry
    ? `<span class="card-sub">${esc(entry.episodeName)} · 已看 ${Math.floor(entry.position / 60)} 分钟</span>`
    : (landscape ? '' : `<span class="card-title">${esc(item.title)}</span>`);
  return `<a class="poster-card ${landscape ? 'landscape-card' : ''} ${isFeatured ? 'featured-card' : ''} ${entry ? 'continue-card' : ''}" href="${url}" data-id="${idOf(item)}" aria-label="${esc(item.title)}${rating ? `，评分 ${rating.score}` : ''}${entry ? `，最近观看 ${esc(entry.episodeName)}` : '，查看详情'}">
    <div class="poster-art">${art ? `<img src="${esc(art)}" alt="" loading="${isFeatured ? 'eager' : 'lazy'}">` : '<span class="no-poster">暂无图片</span>'}
    ${rating ? `<span class="card-score">${esc(rating.score)}</span>` : ''}
    ${landscape ? `<div class="card-hover"><div class="card-hover-actions"><span class="round-button">${icon('play')}</span><span class="round-button ghost">${icon('info')}</span></div><div class="card-hover-copy"><strong>${esc(item.title)}</strong>${rating || meta ? `<span class="card-hover-meta">${rating ? `<em>${rating.score}</em>` : ''}${meta}</span>` : ''}</div></div>` : ''}
    ${entry ? `<div class="watch-bar"><i style="width:${entry.duration > 0 ? Math.min(100, entry.position / entry.duration * 100) : 0}%"></i></div>` : ''}</div>
    ${tail}</a>`;
}
function rail(title, items, category) {
  return `<section class="catalog-row home-catalog-row"><div class="row-heading"><h2>${title}</h2>${category ? `<a href="#category/${category}">查看全部 ${icon('next')}</a>` : ''}</div><div class="poster-rail landscape-rail">${items.map(item => card(item, { landscape: true })).join('')}</div></section>`;
}
function bindImageErrors() {
  content.querySelectorAll('img').forEach(img => img.addEventListener('error', () => { img.classList.add('image-unavailable'); }, { once: true }));
}
async function home(stamp) {
  const catalog = await featured();
  if (stamp !== epoch) return;
  const heroes = (catalog.hero || []).filter(item => idOf(item));
  if (!heroes.length) { content.innerHTML = '<div class="empty"><h1>片库暂时没有推荐</h1><p>稍后再来看看。</p></div>'; return; }
  const entries = recentProgress(progress);
  const recentRow = entries.length
    ? `<section class="catalog-row continue-row"><div class="row-heading"><h2>最近观看</h2></div><div class="poster-rail landscape-rail">${entries.map(entry => card(entry.item, { entry, landscape: true })).join('')}</div></section>`
    : '';
  content.innerHTML = `<section class="hero"><div class="hero-art" aria-hidden="true"></div><div class="hero-copy"></div></section>
    ${recentRow}
    <section class="catalog-row home-catalog-row featured-shelf"><div class="row-heading"><h2>最近热播</h2><div class="carousel-controls"><span id="hero-count"></span><button data-carousel="prev" aria-label="上一部推荐">${icon('prev')}</button><button data-carousel="next" aria-label="下一部推荐">${icon('next')}</button></div></div>
    <div class="poster-rail landscape-rail featured-rail">${heroes.map(item => card(item, { featured: true, landscape: true })).join('')}</div></section>
    `;
  let selected = 0;
  const hero = content.querySelector('.hero');
  const art = content.querySelector('.hero-art');
  function scene(item) {
    const bg = backdrop(item), still = poster(item);
    if (bg) return `<div class="scene"><img src="${esc(bg)}" alt=""></div>`;
    if (still) return `<div class="scene"><img class="hero-poster" src="${esc(still)}" alt=""></div>`;
    return '<div class="scene"></div>';
  }
  function show(index) {
    selected = (index + heroes.length) % heroes.length;
    const item = heroes[selected];
    art.insertAdjacentHTML('beforeend', scene(item));
    while (art.children.length > 2) art.firstElementChild.remove();
    content.querySelector('.hero-copy').innerHTML = `<h1>${esc(item.title)}</h1>${meta(item)}<p class="hero-description">${esc(summary(item))}</p><div class="actions hero-actions"><a class="button primary" href="#play/${idOf(item)}/0">${icon('play')}播放</a><a class="button secondary" href="#detail/${idOf(item)}">${icon('info')}<span class="desktop-info-label">更多信息</span><span class="mobile-info-label">详情</span></a></div>`;
    content.querySelector('#hero-count').textContent = `${String(selected + 1).padStart(2, '0')} / ${String(heroes.length).padStart(2, '0')}`;
    content.querySelectorAll('.featured-card').forEach((link, i) => link.classList.toggle('selected', i === selected));
    bindImageErrors();
  }
  show(0);
  content.querySelectorAll('.featured-card').forEach((link, index) => {
    link.addEventListener('pointerenter', event => { if (event.pointerType === 'mouse') show(index); });
    link.addEventListener('focus', () => show(index));
  });
  content.querySelectorAll('[data-carousel]').forEach(button => button.addEventListener('click', () => show(selected + (button.dataset.carousel === 'next' ? 1 : -1))));
  const syncNav = () => document.querySelector('#nav')?.classList.toggle('scrolled', window.scrollY > 24);
  syncNav();
  window.addEventListener('scroll', syncNav, { passive: true });
  let touchStart;
  hero.addEventListener('touchstart', event => { if (!event.target.closest('.poster-rail')) touchStart = [event.touches[0].clientX, event.touches[0].clientY]; }, { passive: true });
  hero.addEventListener('touchend', event => {
    if (!touchStart) return;
    const dx = event.changedTouches[0].clientX - touchStart[0], dy = event.changedTouches[0].clientY - touchStart[1];
    if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy)) show(selected + (dx < 0 ? 1 : -1));
    touchStart = null;
  }, { passive: true });
  heroTimer = setInterval(() => {
    if (!document.hidden && !hero.matches(':hover') && !hero.contains(document.activeElement) && !matchMedia('(prefers-reduced-motion: reduce)').matches) show(selected + 1);
  }, 8000);
  cleanup = () => window.removeEventListener('scroll', syncNav);
}
/**
 * Remote-control feedback: move/confirm/back click, hold-to-repeat stays quiet.
 * Key repeat is filtered because event.repeat is true for every held key frame.
 */
function playKeyFeedback(event) {
  if (event.repeat || !isFeedbackKey(event.key, event)) return;
  if (event.key === 'Escape' || event.key === 'Backspace' || event.key === 'BrowserBack') playBackSound();
  else if (event.key === 'Enter' || event.key === ' ' || event.key === 'Spacebar') playConfirmSound();
  else playMoveSound();
}
function closeDialog() {
  document.querySelector('#dialog')?.remove();
}
function dialog(html) {
  closeDialog();
  document.body.insertAdjacentHTML('beforeend', `<div class="dialog-backdrop" id="dialog" role="dialog" aria-modal="true"><div class="dialog-card"><button class="dialog-close" type="button" data-dialog-close aria-label="关闭">✕</button>${html}</div></div>`);
  document.querySelector('#dialog .dialog-card button:not(.dialog-close)')?.focus();
}
async function checkUpdate() {
  dialog(`<h3>检查更新</h3><p>当前版本 <strong>v${esc(APP_VERSION)}</strong>，正在查询最新版本…</p>`);
  let latest = null;
  try {
    const response = await fetch(`https://api.github.com/repos/chinahhy/Hazix/releases/latest`, { signal: AbortSignal.timeout(8000), headers: { Accept: 'application/vnd.github+json' } });
    if (response.ok) latest = (await response.json()).tag_name || null;
  } catch { latest = null; }
  const body = latest
    ? (isNewer(latest, APP_VERSION)
      ? `<h3>发现新版本 v${esc(latest.replace(/^v/i, ''))}</h3><p>当前版本 v${esc(APP_VERSION)}。可以前往发布页下载并覆盖安装。</p><div class="dialog-actions"><a class="button primary" href="${RELEASES_PAGE}" target="_blank" rel="noreferrer">打开发布页</a><button class="button secondary" type="button" data-dialog-close>稍后</button></div>`
      : `<h3>已是最新版本</h3><p>当前版本 v${esc(APP_VERSION)}，无需更新。</p><div class="dialog-actions"><button class="button primary" type="button" data-dialog-close>知道了</button></div>`)
    : `<h3>检查更新</h3><p>网络不可用，暂时查不到最新版本。当前版本 v${esc(APP_VERSION)}，可以稍后重试或直接打开发布页。</p><div class="dialog-actions"><a class="button primary" href="${RELEASES_PAGE}" target="_blank" rel="noreferrer">打开发布页</a><button class="button secondary" type="button" data-dialog-close>知道了</button></div>`;
  const card = document.querySelector('#dialog .dialog-card');
  if (card) card.innerHTML = `<button class="dialog-close" type="button" data-dialog-close aria-label="关闭">✕</button>${body}`;
}
function myPage() {  const entries = recentProgress(progress);
  content.innerHTML = `<section class="browse my-page"><h1>我的</h1><p class="page-intro">你的观看进度会安全保存在这台设备上。</p>${entries.length ? `<div class="poster-grid landscape-grid">${entries.map(entry => card(entry.item, { entry, landscape: true })).join('')}</div>` : '<p class="empty-progress">还没有观看记录。播放一部片子，这里就会记住你看到哪里。</p>'}</section>`;
}
async function categoryPage(key, stamp) {
  const definition = categories.find(c => c[0] === key);
  if (!definition) throw new Error('没有这个分类');
  content.innerHTML = `<section class="browse"><h1>${definition[1]}</h1>
    <div class="poster-grid" id="category-grid"></div>
    <div class="infinite-status" id="infinite-status" role="status" aria-live="polite"></div>
    <div class="infinite-sentinel" id="infinite-sentinel" aria-hidden="true"></div></section>`;
  const grid = content.querySelector('#category-grid');
  const status = content.querySelector('#infinite-status');
  const sentinel = content.querySelector('#infinite-sentinel');
  const loadedIds = new Set();
  let page = 0, totalPages = 1, loading = false, observer;

  async function loadNext() {
    if (loading || page >= totalPages || stamp !== epoch) return;
    loading = true;
    status.textContent = '正在载入更多…';
    const nextPage = page + 1;
    try {
      const data = await api(`/vods?category=${key}&page=${nextPage}&limit=36`);
      if (stamp !== epoch) return;
      const items = categoryItems(key, data.items || []).filter(item => {
        const id = idOf(item);
        if (!id || loadedIds.has(id)) return false;
        loadedIds.add(id);
        return true;
      });
      if (items.length) grid.insertAdjacentHTML('beforeend', items.map(item => card(item)).join(''));
      page = Math.max(nextPage, Number(data.page) || nextPage);
      totalPages = Math.max(page, Number(data.totalPages) || page);
      bindImageErrors();
      if (!grid.children.length && page >= totalPages) {
        status.textContent = '这个分类暂时没有内容。';
      } else {
        status.textContent = page < totalPages ? '继续向下浏览会自动载入' : '';
      }
      sentinel.hidden = page >= totalPages;
      if (!items.length && page < totalPages) queueMicrotask(loadNext);
    } catch (error) {
      if (stamp !== epoch) return;
      if (!page) throw error;
      status.innerHTML = `<span>更多内容暂时没有加载成功</span><button class="text-button" type="button">重试</button>`;
      status.querySelector('button').addEventListener('click', loadNext, { once: true });
    } finally {
      loading = false;
    }
  }

  await loadNext();
  if (stamp !== epoch) return;
  observer = new IntersectionObserver(entries => {
    if (entries.some(entry => entry.isIntersecting)) loadNext();
  }, { rootMargin: '700px 0px' });
  observer.observe(sentinel);
  cleanup = () => observer?.disconnect();
}
async function searchPage(query, stamp) {
  content.innerHTML = `<section class="browse search-page"><h1>搜索</h1><form class="search-form" role="search"><label for="query" class="sr-only">片名、演员或导演</label>${icon('search')}<input id="query" name="q" type="search" autocomplete="off" placeholder="片名、演员或导演" value="${esc(query)}" maxlength="100"><button class="button primary" type="submit">搜索</button></form><div id="results" aria-live="polite">${query ? '<p class="empty-progress">正在搜索…</p>' : '<p class="empty-progress">输入关键词，找到你想看的故事。</p>'}</div></section>`;
  content.querySelector('form').addEventListener('submit', event => {
    event.preventDefault();
    const value = content.querySelector('#query').value.trim();
    const next = `#search/${encodeURIComponent(value)}`;
    if (location.hash === next) render(); else location.hash = next;
  });
  if (!query) { content.querySelector('input').focus(); return; }
  try {
    const data = await api(`/search?q=${encodeURIComponent(query)}`);
    if (stamp !== epoch) return;
    const items = data.items || data.results || [];
    content.querySelector('#results').innerHTML = `<p class="results-count">“${esc(query)}” · ${items.length} 个结果</p>${items.length ? `<div class="poster-grid">${items.map(item => card(item)).join('')}</div>` : '<p class="empty-progress">没有找到相关影片，试试其他关键词。</p>'}`;
    bindImageErrors();
  } catch (error) {
    if (stamp === epoch) content.querySelector('#results').innerHTML = `<p class="empty-progress">${esc(error.message)}，可再次点击搜索。</p>`;
  }
}
const backButton = () => `<button class="back-button" data-back>${icon('back')}返回</button>`;
async function detailPage(id, stamp) {
  const data = await detail(id);
  if (stamp !== epoch) return;
  const item = data.item, bg = backdrop(item);
  content.innerHTML = `<article class="detail-page">${bg ? `<div class="detail-backdrop"><img src="${esc(bg)}" alt=""></div>` : ''}${backButton()}
    <div class="detail-summary"><img class="detail-poster" src="${esc(poster(item))}" alt="${esc(item.title)}海报"><div><h1>${esc(item.title)}</h1>${meta(item)}<p class="synopsis">${esc(summary(item))}</p><p class="credits">导演 · ${esc(item.director || '暂无资料')}</p><p class="credits">主演 · ${esc(item.actors || '暂无资料')}</p><div class="actions">${data.episodes.length ? `<a class="button primary" href="#play/${id}/0">${icon('play')}立即播放</a>` : '<span class="empty-progress">暂时没有可播放的剧集</span>'}</div></div></div>
    <section class="episodes"><div class="row-heading"><h2>选集</h2><span>共 ${data.episodes.length} 集</span></div><div class="episode-grid">${data.episodes.map((episode, index) => `<a href="#play/${id}/${index}" class="episode">${esc(episode.name)}</a>`).join('')}</div></section>
    ${(data.related || []).length ? rail('你可能还喜欢', data.related.slice(0, 12)) : ''}</article>`;
}
function persist(entry) {
  const key = `${entry.vodId}:${entry.episodeId}`;
  if (entry.duration > 0 && entry.position >= entry.duration - 20) delete progress[key];
  else if (entry.position > 0) progress[key] = entry;
  const trimmed = Object.entries(progress).sort((a, b) => b[1].updatedAt - a[1].updatedAt).slice(0, 150);
  progress = Object.fromEntries(trimmed);
  try { localStorage.setItem('hdao.web.progress.v1', JSON.stringify(progress)); } catch { notice('浏览器存储不可用，观看进度暂时无法保存'); }
}
async function playerPage(id, index, stamp) {
  const data = await detail(id);
  if (stamp !== epoch) return;
  const episode = data.episodes[index];
  if (!episode) throw new Error('这个节目暂时没有可播放的剧集');
  const item = data.item;
  content.innerHTML = `<section class="player-page"><div class="player-heading">${backButton()}<div><h1>${esc(item.title)}</h1><span>${esc(episode.name)}</span></div><a href="#detail/${id}" class="text-link">全部剧集</a></div><video id="video" controls playsinline preload="metadata" poster="${esc(backdrop(item) || poster(item))}"></video><div id="player-status" role="status">正在连接播放源…</div><div class="player-actions"><button class="button secondary" id="retry-player">重新加载</button>${index + 1 < data.episodes.length ? `<a class="button primary" href="#play/${id}/${index + 1}">下一集 ${icon('next')}</a>` : ''}</div><div class="episode-grid">${data.episodes.map((ep, i) => `<a class="episode" href="#play/${id}/${i}" ${i === index ? 'aria-current="true"' : ''}>${esc(ep.name)}</a>`).join('')}</div></section>`;
  const video = content.querySelector('video'), status = content.querySelector('#player-status');
  const saved = progress[`${id}:${episode.id}`];
  let lastSaved = 0, hls;
  function save() {
    if (!video.currentTime || !Number.isFinite(video.currentTime)) return;
    persist({ vodId: id, episodeId: episode.id, episodeIndex: index, episodeName: episode.name, item, position: video.currentTime, duration: Number.isFinite(video.duration) ? video.duration : 0, updatedAt: Date.now() });
  }
  video.addEventListener('loadedmetadata', () => {
    if (saved?.position > 0 && saved.position < video.duration - 20) video.currentTime = saved.position;
    status.textContent = '已就绪，点击播放开始观看';
    video.play().catch(() => { status.textContent = '点击播放器中的播放按钮开始观看'; });
  }, { once: true });
  video.addEventListener('playing', () => { status.textContent = '正在播放 · 观看进度自动保存'; });
  video.addEventListener('waiting', () => { status.textContent = '正在缓冲…'; });
  video.addEventListener('timeupdate', () => { if (Date.now() - lastSaved > 5000) { save(); lastSaved = Date.now(); } });
  video.addEventListener('pause', save);
  video.addEventListener('ended', () => { save(); status.textContent = '本集已播放完毕'; });
  const failure = () => { status.textContent = '播放源暂时不可用。可重新加载、切换集数或稍后重试。'; };
  video.addEventListener('error', failure);
  content.querySelector('#retry-player').addEventListener('click', () => render());
  const source = playbackURL(episode.originalUrl);
  if (window.Hls?.isSupported()) {
    video.dataset.player = 'hls.js';
    hls = new window.Hls({ enableWorker: true, backBufferLength: 90 });
    hls.on(window.Hls.Events.MEDIA_ATTACHED, () => hls.loadSource(source));
    hls.on(window.Hls.Events.MANIFEST_PARSED, () => { status.textContent = '已就绪，点击播放开始观看'; });
    hls.on(window.Hls.Events.ERROR, (_event, data) => {
      video.dataset.hlsError = data.details || data.type || 'unknown';
      if (!data.fatal) return;
      if (data.type === window.Hls.ErrorTypes.NETWORK_ERROR) {
        status.textContent = '播放网络中断，正在重连…';
        hls.startLoad();
      } else if (data.type === window.Hls.ErrorTypes.MEDIA_ERROR) {
        status.textContent = '播放解码异常，正在恢复…';
        hls.recoverMediaError();
      } else {
        hls.destroy(); hls = null; failure();
      }
    });
    hls.attachMedia(video);
  } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
    video.dataset.player = 'native-hls';
    video.src = source;
  } else {
    status.textContent = '当前浏览器不支持 HLS 或 MediaSource，请使用新版 Safari、Chrome 或 Edge。';
  }
  const watchdog = setTimeout(() => {
    if (video.getAttribute('src') && video.readyState === 0) status.textContent = '播放源响应超时，可重新加载或返回选集。';
  }, 25_000);
  window.addEventListener('pagehide', save);
  cleanup = () => { clearTimeout(watchdog); save(); window.removeEventListener('pagehide', save); hls?.destroy(); hls = null; video.pause(); video.removeAttribute('src'); video.load(); };
}
function goBack() { if (history.state?.hdao) history.back(); else location.hash = '#home'; }
async function render() {
  const stamp = ++epoch;
  clearInterval(heroTimer); cleanup(); cleanup = () => {};
  const parts = location.hash.slice(1).split('/'), page = parts[0] || 'home';
  nav(page === 'category' ? parts[1] : page, ['detail', 'play'].includes(page));
  content.innerHTML = '<div class="loading" role="status"><span></span>正在准备片库…</div>';
  window.scrollTo(0, 0);
  try {
    if (page === 'home') await home(stamp);
    else if (page === 'category') await categoryPage(parts[1], stamp);
    else if (page === 'search') await searchPage(decodeURIComponent(parts.slice(1).join('/')), stamp);
    else if (page === 'my') myPage();
    else if (page === 'detail') await detailPage(Number(parts[1]), stamp);
    else if (page === 'play') await playerPage(Number(parts[1]), Math.max(0, Number(parts[2]) || 0), stamp);
    else throw new Error('页面不存在');
    if (stamp !== epoch) return;
    bindImageErrors();
    if (page !== 'search') content.focus({ preventScroll: true });
  } catch (error) {
    if (stamp !== epoch) return;
    content.innerHTML = `<section class="empty"><h1>内容暂时没有加载出来</h1><p>${esc(error.message)}</p><div class="actions"><button class="button primary" data-retry>重新加载</button><a class="button secondary" href="#home">回到首页</a></div></section>`;
  }
}
content.addEventListener('click', event => {
  if (event.target.closest('[data-back]')) goBack();
  if (event.target.closest('[data-retry]')) render();
});
document.addEventListener('click', event => {
  if (event.target.closest('[data-dialog-close]')) { closeDialog(); return; }
  if (event.target.closest('[data-update]')) { checkUpdate(); return; }
  const link = event.target.closest('a[href^="#"]');
  if (link?.hash === '#content') { event.preventDefault(); content.focus(); return; }
  if (link && link.hash !== location.hash) {
    // The following hash entry belongs to this app, so its back button can safely use browser history.
    const mark = () => history.replaceState({ hdao: true }, '');
    window.addEventListener('hashchange', mark, { once: true });
  }
});
document.addEventListener('keydown', event => {
  playKeyFeedback(event);
  if (event.key === 'Escape') {
    event.preventDefault();
    if (document.querySelector('#dialog')) closeDialog(); else goBack();
    return;
  }
  if (event.target.matches('input, textarea, select, video')) return;
  if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) return;
  const candidates = [...document.querySelectorAll('a[href],button:not(:disabled),input')].filter(el => el.getClientRects().length && !el.closest('.skip'));
  if (!candidates.length) return;
  const current = document.activeElement;
  if (!candidates.includes(current)) { event.preventDefault(); (content.querySelector('a,button,input') || candidates[0]).focus(); return; }
  const from = current.getBoundingClientRect(), cx = from.x + from.width / 2, cy = from.y + from.height / 2;
  const horizontal = event.key === 'ArrowLeft' || event.key === 'ArrowRight';
  const sign = event.key === 'ArrowLeft' || event.key === 'ArrowUp' ? -1 : 1;
  const options = candidates.filter(el => el !== current).map(el => {
    const box = el.getBoundingClientRect(), dx = box.x + box.width / 2 - cx, dy = box.y + box.height / 2 - cy;
    const forward = (horizontal ? dx : dy) * sign, cross = Math.abs(horizontal ? dy : dx);
    return { el, forward, cost: forward + cross * 4 };
  }).filter(v => v.forward > 5).sort((a, b) => a.cost - b.cost);
  if (options[0]) { event.preventDefault(); options[0].el.focus({ preventScroll: true }); options[0].el.scrollIntoView({ behavior: 'instant', block: 'nearest', inline: 'nearest' }); }
});
window.addEventListener('hashchange', render);
if (!location.hash) history.replaceState({ hdao: true }, '', '#home');
render();
