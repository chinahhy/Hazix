import test from 'node:test';
import assert from 'node:assert/strict';
import { RELEASES_PAGE, RELEASE_API, versionKey, isNewer, updateCheckingHTML, updateDialogHTML } from '../public/update.js';

test('版本号比较不受标签前缀和段数影响', () => {
  assert.deepEqual(versionKey('v3.7.1'), [3, 7, 1]);
  assert.deepEqual(versionKey('3.7.1'), [3, 7, 1]);
  assert.deepEqual(versionKey(undefined), [0]);
  assert.equal(isNewer('v3.7.2', '3.7.1'), true);
  assert.equal(isNewer('3.7.1', 'v3.7.1'), false);
  assert.equal(isNewer('v3.7.0', '3.7.1'), false);
  // 逐段比较：小数点在版本号里不是小数点
  assert.equal(isNewer('3.10.0', '3.9.9'), true);
  assert.equal(isNewer('v3.7', '3.7.0'), false);
  assert.equal(isNewer('3.7.1.1', '3.7.1'), true);
});

test('检查更新的三种结果各自给出正确的结论与版本号', () => {
  const newer = updateDialogHTML('v3.7.2', '3.7.1');
  assert.match(newer, /发现新版本 v3\.7\.2/);
  assert.match(newer, /当前版本 v3\.7\.1/);
  assert.ok(newer.includes(RELEASES_PAGE), '发现新版本必须给出发布页');
  assert.match(newer, /data-dialog-close>稍后</);

  const latest = updateDialogHTML('v3.7.1', '3.7.1');
  assert.match(latest, /已是最新版本/);
  assert.match(latest, /当前版本 v3\.7\.1/);
  assert.doesNotMatch(latest, /发现新版本/);

  // 查不到时不能说「已是最新」——那是在撒谎
  const unreachable = updateDialogHTML(null, '3.7.1');
  assert.match(unreachable, /网络不可用/);
  assert.doesNotMatch(unreachable, /已是最新版本/);
  assert.ok(unreachable.includes(RELEASES_PAGE));
});

test('弹窗内容与版本号都经过转义，查询中也显示当前版本', () => {
  assert.match(updateCheckingHTML('3.7.1'), /当前版本 <strong>v3\.7\.1<\/strong>/);
  // CHANGELOG 或注入值一旦被污染，也不能变成可执行标记
  const injected = updateDialogHTML('<img src=x onerror=alert(1)>', '<script>');
  assert.doesNotMatch(injected, /<img|<script>/);
  assert.match(injected, /&lt;img|&lt;script/);
});

test('更新检查只查询本项目的发布，发布页也指向本项目', () => {
  assert.equal(RELEASE_API, 'https://api.github.com/repos/chinahhy/Hazix/releases/latest');
  assert.equal(RELEASES_PAGE, 'https://github.com/chinahhy/Hazix/releases');
  assert.ok(RELEASE_API.startsWith('https://'), '更新检查必须走 https');
});
