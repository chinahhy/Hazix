// Optional real-Chrome UI regression suite: pnpm run test:ui.
// No new dependency or persistent server; production preview + isolated Chrome
// both stop at teardown. Deterministic fixtures test UI, not live playback.
import test from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { access, mkdtemp, mkdir, writeFile, rm, realpath } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { once } from 'node:events';
import { server } from '../server.mjs';

const chromePath = process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const screenshotDir = process.env.UI_SCREENSHOT_DIR && path.resolve(process.env.UI_SCREENSHOT_DIR);

class CDP {
  constructor(socket) {
    this.socket = socket;
    this.nextId = 0;
    this.pending = new Map();
    this.listeners = new Map();
    socket.addEventListener('message', event => {
      const message = JSON.parse(event.data);
      if (message.id) {
        const pending = this.pending.get(message.id);
        this.pending.delete(message.id);
        if (!pending) return;
        clearTimeout(pending.timer);
        if (message.error) pending.reject(new Error(JSON.stringify(message.error)));
        else pending.resolve(message.result);
      } else {
        for (const listener of this.listeners.get(message.method) || []) listener(message.params);
      }
    });
  }
  send(method, params = {}, timeout = 15000) {
    return new Promise((resolve, reject) => {
      const id = ++this.nextId;
      const timer = setTimeout(() => { this.pending.delete(id); reject(new Error(`CDP timeout: ${method}`)); }, timeout);
      this.pending.set(id, { resolve, reject, timer });
      this.socket.send(JSON.stringify({ id, method, params }));
    });
  }
  on(method, listener) {
    const listeners = this.listeners.get(method) || [];
    listeners.push(listener);
    this.listeners.set(method, listeners);
  }
  async evaluate(expression, timeout = 15000) {
    const result = await this.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true, userGesture: true }, timeout);
    if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
    return result.result.value;
  }
  async waitFor(expression, timeout = 7000) {
    return this.evaluate(`new Promise((resolve, reject) => {
      const deadline = Date.now() + ${timeout};
      const check = () => {
        if (${expression}) resolve(true);
        else if (Date.now() > deadline) reject(new Error('UI condition timed out: ' + ${JSON.stringify(expression)}));
        else requestAnimationFrame(check);
      }; check();
    })`, timeout + 1000);
  }
}

const fixtureBootstrap = `(() => {
  const description = '这是一段用于验证布局的长简介，检查文字增多时播放按钮和海报仍然完整可见。'.repeat(7).slice(0, 220);
  const missing = new URL(location.href).searchParams.has('missing');
  const heroes = Array.from({ length: 6 }, (_, index) => ({
    vodId: 101 + index, title: index ? '布局验收样片 ' + (index + 1) : '漫长旅途：穿越星海寻找失落记忆的冒险故事',
    description, year: '2026', area: '中国大陆', genres: '剧情 / 冒险', remarks: '全24集',
    tmdbPoster: missing ? '/missing.jpg' : '/fixture-poster.jpg', tmdbBackdrop: '/fixture-backdrop.jpg',
    doubanScore: '8.6', parentTypeId: 54, director: '示例导演', actors: '示例演员甲、示例演员乙'
  }));
  const episodes = [{ id: 501, name: '第1集', sortOrder: 0, originalUrl: 'https://example.invalid/ui-fixture.m3u8' }];
  localStorage.setItem('hdao.web.progress.v1', JSON.stringify({ fixture: {
    vodId: 101, episodeId: 501, episodeIndex: 0, episodeName: '第1集', item: heroes[0],
    position: 180, duration: 1800, updatedAt: Date.now()
  } }));
  if (new URL(location.href).searchParams.has('no-history')) localStorage.removeItem('hdao.web.progress.v1');
  const nativeFetch = window.fetch.bind(window);
  window.__uiRequests = [];
  window.__pendingUpdates = [];
  window.__resolveUpdate = (tag, index = window.__pendingUpdates.length - 1) => window.__pendingUpdates[index]?.(new Response(JSON.stringify({ tag_name: tag }), { status: 200 }));
  window.fetch = async (input, options) => {
    const url = new URL(typeof input === 'string' ? input : input.url, location.href);
    window.__uiRequests.push(url.href);
    if (url.hostname === 'api.github.com') return new Promise(resolve => window.__pendingUpdates.push(resolve));
    if (url.origin === location.origin && url.pathname.startsWith('/api/')) {
      if (url.pathname === '/api/vods/featured') return Response.json({ hero: heroes });
      if (/^\\/api\\/vods\\/\\d+$/.test(url.pathname)) return Response.json({ item: heroes.find(item => item.vodId === Number(url.pathname.split('/').pop())) || heroes[0], episodes, related: heroes.slice(1) });
      return Response.json({ items: heroes, page: 1, totalPages: 1 });
    }
    return nativeFetch(input, options);
  };
})();`;

const fixtureSVG = `<svg xmlns="http://www.w3.org/2000/svg" width="1600" height="1000"><defs><linearGradient id="g" x2="1" y2="1"><stop stop-color="#17232c"/><stop offset=".55" stop-color="#485a60"/><stop offset="1" stop-color="#11161c"/></linearGradient></defs><rect width="1600" height="1000" fill="url(#g)"/><circle cx="1100" cy="330" r="210" fill="#c5d1cc" opacity=".18"/><path d="M0 950 430 420 720 760 1150 350 1600 920V1000H0" fill="#121c24"/><text x="950" y="900" font-family="sans-serif" font-size="40" fill="#aebcbd">UI TEST FIXTURE</text></svg>`;

test('网页 UI：多尺寸布局、信息层级和焦点回归', { timeout: 120000 }, async t => {
  try { await access(chromePath); } catch { t.skip('需要本机 Chrome，或通过 CHROME_PATH 指定 Chromium；未执行视觉验收'); return; }
  const profile = await mkdtemp(path.join(tmpdir(), 'viewing-ui-chrome-'));
  const resolvedProfile = await realpath(profile);
  let browser;
  t.after(async () => {
    if (browser && browser.exitCode === null && browser.signalCode === null) {
      const exited = once(browser, 'exit');
      browser.kill('SIGTERM');
      const timer = setTimeout(() => browser.kill('SIGKILL'), 4000);
      await exited;
      clearTimeout(timer);
    }
    server.closeAllConnections();
    if (server.listening) await new Promise(resolve => server.close(resolve));
    // Delete only this run's mkdtemp directory after verifying its resolved path.
    assert.ok(path.basename(resolvedProfile).startsWith('viewing-ui-chrome-'));
    assert.equal(await realpath(profile), resolvedProfile);
    await rm(resolvedProfile, { recursive: true, force: true });
  });
  const origin = await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => resolve(`http://127.0.0.1:${server.address().port}`));
  });
  browser = spawn(chromePath, ['--headless=new', '--no-sandbox', '--disable-gpu', '--disable-background-networking', '--disable-extensions', '--no-first-run', '--no-default-browser-check', '--mute-audio', '--hide-scrollbars', '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank'], { stdio: ['ignore', 'ignore', 'pipe'] });
  const debugOrigin = await new Promise((resolve, reject) => {
    let output = '';
    const timer = setTimeout(() => reject(new Error(`Chrome startup timed out: ${output.slice(-1200)}`)), 15000);
    browser.once('error', error => { clearTimeout(timer); reject(error); });
    browser.once('exit', code => { clearTimeout(timer); reject(new Error(`Chrome exited ${code}: ${output.slice(-1200)}`)); });
    browser.stderr.on('data', chunk => {
      output += chunk;
      const match = output.match(/DevTools listening on ws:\/\/(127\.0\.0\.1:\d+)/);
      if (match) { clearTimeout(timer); resolve(`http://${match[1]}`); }
    });
  });
  const target = await (await fetch(`${debugOrigin}/json/new?about:blank`, { method: 'PUT' })).json();
  const socket = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, { once: true }); socket.addEventListener('error', reject, { once: true }); });
  t.after(() => socket.close());
  const cdp = new CDP(socket);
  const exceptions = [], interceptionErrors = [];
  cdp.on('Runtime.exceptionThrown', event => exceptions.push(event.exceptionDetails.exception?.description || event.exceptionDetails.text));
  await cdp.send('Runtime.enable');
  await cdp.send('Page.enable');
  const fixtureScript = await cdp.send('Page.addScriptToEvaluateOnNewDocument', { source: fixtureBootstrap });
  await cdp.send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] });
  cdp.on('Fetch.requestPaused', event => {
    const isHls = event.request.url.includes('/vendor/hls.min.js');
    const missing = event.request.url.includes('missing');
    cdp.send('Fetch.fulfillRequest', {
      requestId: event.requestId, responseCode: missing ? 404 : 200,
      responseHeaders: [{ name: 'Content-Type', value: isHls ? 'text/javascript' : 'image/svg+xml' }],
      body: Buffer.from(isHls ? 'window.Hls = { isSupported: () => false };' : missing ? '' : fixtureSVG).toString('base64'),
    }).catch(error => interceptionErrors.push(error.message));
  });
  await cdp.send('Fetch.enable', { patterns: [{ urlPattern: '*/image?*' }, { urlPattern: '*/vendor/hls.min.js' }] });
  const viewport = (width, height) => cdp.send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: width <= 700 });
  let navigationId = 0;
  const navigate = async (route = 'home', query = '', readyTimeout = 7000) => {
    // Changing only the hash is same-document navigation: it would keep fixture
    // request/focus state from the previous case and invalidate isolation.
    const params = new URLSearchParams(query);
    params.set('ui-run', String(++navigationId));
    const loaded = new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('Page load timed out')), 15000);
      const listener = () => {
        clearTimeout(timer);
        cdp.listeners.set('Page.loadEventFired', (cdp.listeners.get('Page.loadEventFired') || []).filter(item => item !== listener));
        resolve();
      };
      cdp.on('Page.loadEventFired', listener);
    });
    await cdp.send('Page.navigate', { url: `${origin}/app.html?${params}#${route}` });
    await loaded;
    await cdp.waitFor(`location.hash === ${JSON.stringify('#' + route)} && !document.querySelector('.loading') && !!document.querySelector('#content')?.children.length`, readyTimeout);
  };
  const key = async (value, modifiers = 0) => {
    await cdp.send('Input.dispatchKeyEvent', { type: 'keyDown', key: value, code: value, modifiers });
    await cdp.send('Input.dispatchKeyEvent', { type: 'keyUp', key: value, code: value, modifiers });
  };
  const screenshot = async name => {
    if (!screenshotDir) return;
    await mkdir(screenshotDir, { recursive: true });
    const { data } = await cdp.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
    await writeFile(path.join(screenshotDir, `${name}.png`), Buffer.from(data, 'base64'));
  };

  for (const [width, height] of [[1920, 1080], [1366, 768], [1024, 768], [320, 568], [390, 844], [414, 896]]) {
    await t.test(`首页 ${width}×${height}：长标题/220字简介不挤压按钮与海报`, async () => {
      await viewport(width, height);
      await navigate();
      await cdp.waitFor("document.querySelectorAll('.featured-card').length === 6");
      const layout = await cdp.evaluate(`(() => {
        const rect = selector => { const r = document.querySelector(selector).getBoundingClientRect(); return { top: r.top, bottom: r.bottom }; };
        const mobile = innerWidth <= 700;
        return { width: innerWidth, height: innerHeight, scrollWidth: document.documentElement.scrollWidth,
          first: rect('.home-first-screen'), recent: rect('.continue-row'), actions: rect('.hero-actions'),
          shelf: rect('.featured-shelf'), title: rect('.featured-card .card-title'), heading: rect('.hero-copy h1'),
          available: innerHeight - (mobile ? document.querySelector('.mobile-nav').getBoundingClientRect().height : 0),
          categoryBottom: mobile ? rect('.mobile-categories').bottom : 0,
          titleSize: parseFloat(getComputedStyle(document.querySelector('.card-title')).fontSize),
          scoreSize: parseFloat(getComputedStyle(document.querySelector('.card-score')).fontSize),
          carouselExposed: (() => { const button = document.querySelector('[data-carousel=next]'), r = button.getBoundingClientRect(); return button.contains(document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2)); })()
        };
      })()`);
      assert.equal(layout.width, width);
      assert.ok(layout.scrollWidth <= width, JSON.stringify(layout));
      assert.ok(Math.abs(layout.first.bottom - layout.available) < 2, JSON.stringify(layout));
      assert.ok(layout.recent.top >= layout.available - 1, JSON.stringify(layout));
      assert.ok(layout.actions.bottom + 8 <= layout.shelf.top, JSON.stringify(layout));
      assert.ok(layout.title.bottom <= layout.available - 4, JSON.stringify(layout));
      assert.ok(layout.heading.top >= layout.categoryBottom, JSON.stringify(layout));
      assert.ok(layout.carouselExposed, '轮播控件必须实际可见可点击，不能被巨幕背景盖住：' + JSON.stringify(layout));
      assert.ok(layout.titleSize >= (width <= 700 ? 14 : 18));
      assert.ok(layout.scoreSize >= (width <= 700 ? 12 : 16));
      await screenshot(`home-${width}x${height}`);
    });
  }

  await t.test('手机详情：全宽播放前置、三行简介可展开/收起', async () => {
    await viewport(390, 844);
    await navigate('detail/101');
    await cdp.waitFor("!!document.querySelector('.detail-actions a') && !document.querySelector('.synopsis-toggle').hidden");
    const collapsed = await cdp.evaluate(`(() => {
      const p = document.querySelector('.synopsis'), action = document.querySelector('.detail-actions a');
      return { height: p.clientHeight, lineHeight: parseFloat(getComputedStyle(p).lineHeight), playBottom: action.getBoundingClientRect().bottom, synopsisTop: p.getBoundingClientRect().top, playWidth: action.getBoundingClientRect().width, width: innerWidth, scrollWidth: document.documentElement.scrollWidth };
    })()`);
    assert.ok(collapsed.height <= collapsed.lineHeight * 3 + 1);
    assert.ok(collapsed.playBottom < collapsed.synopsisTop);
    assert.ok(collapsed.playWidth >= 350);
    assert.ok(collapsed.scrollWidth <= collapsed.width);
    await screenshot('detail-phone-390');
    await cdp.evaluate("document.querySelector('.synopsis-toggle').click()");
    assert.equal(await cdp.evaluate("document.querySelector('.synopsis-toggle').getAttribute('aria-expanded')"), 'true');
    assert.ok(await cdp.evaluate(`document.querySelector('.synopsis').clientHeight > ${collapsed.height}`));
    await cdp.evaluate("document.querySelector('.synopsis-toggle').click()");
    assert.equal(await cdp.evaluate("document.querySelector('.synopsis-toggle').getAttribute('aria-expanded')"), 'false');
    await viewport(1920, 1080);
    await cdp.waitFor("document.querySelector('.synopsis-toggle').hidden");
    assert.equal(await cdp.evaluate("getComputedStyle(document.querySelector('.synopsis')).webkitLineClamp"), 'none');
  });

  await t.test('手机所有分类可达；320px网格两列，无横向溢出', async () => {
    await viewport(320, 568);
    await navigate();
    const links = await cdp.evaluate("[...document.querySelectorAll('.mobile-categories a')].map(a => a.hash)");
    assert.equal(links.length, 6);
    for (const hash of links) {
      await cdp.evaluate(`document.querySelector('.mobile-categories a[href="${hash}"]').click()`);
      await cdp.waitFor(`location.hash === ${JSON.stringify(hash)} && document.querySelectorAll('#category-grid .poster-card').length === 6`);
      assert.ok(await cdp.evaluate('document.documentElement.scrollWidth <= innerWidth'));
      assert.equal(await cdp.evaluate("getComputedStyle(document.querySelector('.poster-grid')).gridTemplateColumns.split(' ').length"), 2);
    }
    await screenshot('category-phone-320');
  });

  await t.test('手机34px底部安全区保留导航与首屏空间', async () => {
    await viewport(390, 844);
    await cdp.send('Emulation.setSafeAreaInsetsOverride', { insets: { top: 0, left: 0, right: 0, bottom: 34 } });
    try {
      await navigate();
      const layout = await cdp.evaluate(`(() => {
        const nav = document.querySelector('.mobile-nav').getBoundingClientRect();
        const title = document.querySelector('.featured-card .card-title').getBoundingClientRect();
        const last = document.querySelector('.mobile-nav a:last-child').getBoundingClientRect();
        return { navHeight: nav.height, navTop: nav.top, titleBottom: title.bottom, lastBottom: last.bottom, height: innerHeight };
      })()`);
      assert.equal(layout.navHeight, 100);
      assert.ok(layout.titleBottom < layout.navTop);
      assert.ok(layout.lastBottom <= layout.height - 34);
    } finally {
      await cdp.send('Emulation.setSafeAreaInsetsOverride', { insets: { top: 0, left: 0, right: 0, bottom: 0 } });
    }
  });

  await t.test('推荐选中标记跟随轮播；手机不请求自动视频预览', async () => {
    await viewport(414, 896);
    await navigate();
    await cdp.evaluate("document.querySelector('[data-carousel=next]').click()");
    assert.equal(await cdp.evaluate("document.querySelector('.featured-card.selected').dataset.id"), '102');
    assert.equal(await cdp.evaluate("document.querySelectorAll('.featured-card[aria-current]').length"), 1);
    assert.match(await cdp.evaluate("document.querySelector('#hero-count').textContent"), /02/);
    assert.ok(await cdp.evaluate("!window.__uiRequests.some(url => /api\\/vods\\/\\d+$/.test(url))"));
    assert.equal(await cdp.evaluate("document.querySelectorAll('.round-button').length"), 0);
  });

  await t.test('TV按下直达续播；片名常显且入口行为明确', async () => {
    await viewport(1920, 1080);
    await navigate();
    await cdp.evaluate("document.querySelector('.featured-card').focus({ preventScroll: true })");
    await key('ArrowDown');
    assert.ok(await cdp.evaluate("document.activeElement.matches('.continue-card')"));
    assert.ok(await cdp.evaluate("document.querySelector('.continue-card .card-title').textContent.length > 0"));
    assert.equal(await cdp.evaluate("document.querySelector('.continue-card').hash"), '#play/101/0');
    await cdp.evaluate("document.querySelector('.continue-card').click()");
    await cdp.waitFor("!!document.querySelector('.player-page')");
    assert.equal(await cdp.evaluate('location.hash'), '#play/101/0');
  });

  await t.test('无观看记录时，下键仍能到达首屏之外的空状态', async () => {
    await viewport(1920, 1080);
    await navigate('home', '?no-history=1');
    await cdp.evaluate("document.querySelector('.featured-card').focus({ preventScroll: true })");
    assert.ok(await cdp.evaluate("document.querySelector('.continue-row').getBoundingClientRect().top >= innerHeight"));
    await key('ArrowDown');
    assert.ok(await cdp.evaluate("document.activeElement.matches('.continue-empty') && scrollY > 0"));
  });

  await t.test('手机主要操作的点击区域至少44×44px', async () => {
    await viewport(390, 844);
    await navigate();
    const small = await cdp.evaluate(`[...document.querySelectorAll('.brand, .nav-actions > *, .mobile-categories a, .mobile-nav a, .hero-actions a, [data-carousel=next]')].filter(el => { const r = el.getBoundingClientRect(); return r.width < 44 || r.height < 44; }).map(el => el.outerHTML)`);
    assert.deepEqual(small, []);
  });

  await t.test('更新弹窗：查询/结果焦点、Tab/方向键封闭、关闭恢复', async () => {
    await viewport(1920, 1080);
    await navigate();
    await cdp.evaluate("document.querySelector('[data-update]').focus(); document.querySelector('[data-update]').click()");
    await cdp.waitFor("!!document.querySelector('#dialog')");
    assert.ok(await cdp.evaluate("document.activeElement.matches('.dialog-close')"));
    assert.ok(await cdp.evaluate("document.querySelector('#content').inert && document.querySelector('#nav').inert"));
    for (const value of ['Tab', 'ArrowLeft', 'ArrowDown', 'ArrowRight', 'ArrowUp']) {
      await key(value);
      assert.ok(await cdp.evaluate("document.querySelector('#dialog').contains(document.activeElement)"));
    }
    await cdp.evaluate("window.__resolveUpdate('v99.0.0')");
    await cdp.waitFor("!!document.querySelector('.dialog-actions a')");
    assert.ok(await cdp.evaluate("document.activeElement.matches('.dialog-actions a')"));
    for (let i = 0; i < 5; i++) {
      await key('Tab', i % 2 ? 8 : 0);
      assert.ok(await cdp.evaluate("document.querySelector('#dialog').contains(document.activeElement)"));
    }
    await screenshot('update-dialog-tv');
    await key('Escape');
    assert.ok(await cdp.evaluate("!document.querySelector('#dialog') && !document.querySelector('#content').inert && document.activeElement.matches('[data-update]')"));
  });

  await t.test('弹窗旧请求不能覆盖新弹窗；已是最新/失败结果均可关闭', async () => {
    await navigate();
    await cdp.evaluate("document.querySelector('[data-update]').focus(); document.querySelector('[data-update]').click()");
    await key('Escape');
    await cdp.evaluate("document.querySelector('[data-update]').click(); window.__resolveUpdate('v99.0.0', 0)");
    assert.match(await cdp.evaluate("document.querySelector('#dialog').textContent"), /正在查询/);
    await cdp.evaluate("window.__resolveUpdate(window.__HDAO_VERSION__, 1)");
    await cdp.waitFor("document.querySelector('#dialog')?.textContent.includes('已是最新版本')");
    assert.ok(await cdp.evaluate("document.activeElement.matches('.dialog-actions button')"));
    await key('Escape');
    await cdp.evaluate("document.querySelector('[data-update]').click(); window.__resolveUpdate(null)");
    await cdp.waitFor("document.querySelector('#dialog')?.textContent.includes('网络不可用')");
    assert.ok(await cdp.evaluate("document.querySelector('#dialog').contains(document.activeElement)"));
    await key('Backspace');
    assert.ok(await cdp.evaluate("!document.querySelector('#dialog')"));
  });

  await t.test('图片失败有占位反馈，且不破坏手机首屏', async () => {
    await viewport(390, 844);
    await navigate('home', '?missing=1');
    await cdp.waitFor("!!document.querySelector('.featured-card .image-unavailable')");
    assert.ok(await cdp.evaluate('document.documentElement.scrollWidth <= innerWidth'));
    assert.match(await cdp.evaluate("getComputedStyle(document.querySelector('.featured-card .poster-art'), '::before').content"), /图片暂不可用/);
  });

  assert.deepEqual(interceptionErrors, []);
  assert.deepEqual(exceptions, [], '浏览器运行时不得出现异常');

  if (process.env.UI_LIVE_SMOKE === '1') {
    await t.test('真实片库首页：TV和手机渲染烟测（依赖外网）', { timeout: 80000 }, async () => {
      await cdp.send('Page.removeScriptToEvaluateOnNewDocument', { identifier: fixtureScript.identifier });
      await cdp.evaluate("localStorage.removeItem('hdao.web.progress.v1')");
      await cdp.send('Fetch.disable');
      for (const [width, height, name] of [[1920, 1080, 'live-home-tv'], [390, 844, 'live-home-phone']]) {
        await viewport(width, height);
        await navigate('home', '', 35000);
        assert.ok(await cdp.evaluate("!!document.querySelector('.featured-card')"), await cdp.evaluate("document.querySelector('#content').textContent"));
        await cdp.evaluate(`Promise.race([
          Promise.all([...document.querySelectorAll('.hero-art img, .featured-card img')].map(img => img.complete ? Promise.resolve() : new Promise(resolve => { img.addEventListener('load', resolve, { once: true }); img.addEventListener('error', resolve, { once: true }); }))),
          new Promise(resolve => setTimeout(resolve, 22000))
        ])`, 24000);
        assert.ok(await cdp.evaluate('document.documentElement.scrollWidth <= innerWidth'));
        await screenshot(name);
      }
      assert.deepEqual(exceptions, [], '真实片库页面不得出现运行时异常');
    });
  }
});
