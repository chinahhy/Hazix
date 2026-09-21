// 版本比较与「检查更新」弹窗的内容都在这里。app.js 只负责取数和塞进 DOM。
//
// 为什么单独一个模块：这三样东西原本写在 app.js 顶部，被一次脚本化删除
// （见 HANDOFF 的 6b11d6a）连 `icon()` 一起删掉了。`icon()` 被补了回来，
// 版本常量没有，于是「检查更新」在用户点下去的那一刻抛 ReferenceError——
// `node --check` 只看语法，单测又碰不到 DOM，两层门禁都没拦住。
// 放到这个无 DOM 依赖的模块里之后：导出被删会让 app.js 的 import 和
// test/update.test.mjs 同时加载失败，而不是等到用户点按钮才炸。
import { escapeHTML as esc } from './data.js';

export const RELEASES_PAGE = 'https://github.com/chinahhy/Hazix/releases';

// 只查这一个仓库的最新发布。写成常量是为了让「查错仓库」这种笔误能被测试盯住。
export const RELEASE_API = 'https://api.github.com/repos/chinahhy/Hazix/releases/latest';

// `v3.7.1` / `3.7.1` 都归一化成 [3, 7, 1]，这样标签带不带 v 都不影响比较。
export const versionKey = value =>
  String(value ?? '').replace(/^v/i, '').split('.').map(part => Number.parseInt(part, 10) || 0);

/** 候选版本是否比当前版本新。逐段比较，所以 3.10.0 比 3.9.9 新。 */
export function isNewer(candidate, current) {
  const a = versionKey(candidate), b = versionKey(current);
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    if ((a[i] || 0) !== (b[i] || 0)) return (a[i] || 0) > (b[i] || 0);
  }
  return false;
}

const actions = (...buttons) => `<div class="dialog-actions">${buttons.join('')}</div>`;
const releasesButton = `<a class="button primary" href="${RELEASES_PAGE}" target="_blank" rel="noreferrer">打开发布页</a>`;
const laterButton = '<button class="button secondary" type="button" data-dialog-close>稍后</button>';
const okButton = '<button class="button primary" type="button" data-dialog-close>知道了</button>';

/** 查询过程中的占位内容，先让用户看到当前的版本号。 */
export const updateCheckingHTML = current =>
  `<h3>检查更新</h3><p>当前版本 <strong>v${esc(current)}</strong>，正在查询最新版本…</p>`;

/**
 * 三种结果：查到更新、已是最新、查不到（无网或被限流）。
 * [latest] 为 null 表示查不到，此时不给「已是最新」这种可能说谎的结论。
 */
export function updateDialogHTML(latest, current) {
  const currentVersion = esc(current);
  if (!latest) {
    return `<h3>检查更新</h3><p>网络不可用，暂时查不到最新版本。当前版本 v${currentVersion}，可以稍后重试或直接打开发布页。</p>${actions(releasesButton, okButton)}`;
  }
  if (isNewer(latest, current)) {
    return `<h3>发现新版本 v${esc(String(latest).replace(/^v/i, ''))}</h3><p>当前版本 v${currentVersion}。可以前往发布页下载并覆盖安装。</p>${actions(releasesButton, laterButton)}`;
  }
  return `<h3>已是最新版本</h3><p>当前版本 v${currentVersion}，无需更新。</p>${actions(okButton)}`;
}
