import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { server, versionFromChangelog } from '../server.mjs';

const read = relative => readFile(fileURLToPath(new URL(relative, import.meta.url)), 'utf8');

// 这一条盯的是「版本注入」这条真实链路：占位符还在不在、注入的版本是不是
// CHANGELOG 里的发布版本。上一版正是断在这里——app.js 已经不认这个变量了，
// 而服务端还照旧注入，两层门禁却都看不见。
test('预览页面注入的版本等于 CHANGELOG 顶部的发布版本', async t => {
  const origin = await new Promise(resolve => {
    server.listen(0, '127.0.0.1', () => resolve(`http://127.0.0.1:${server.address().port}`));
  });
  t.after(() => {
    server.closeAllConnections();
    server.close();
  });

  const expected = versionFromChangelog(await read('../../CHANGELOG.md'));
  assert.ok(expected, 'CHANGELOG.md 顶部必须有一行 `## x.y.z`');

  const html = await (await fetch(`${origin}/app.html`)).text();
  assert.equal(html.includes('<!-- version -->'), false, 'app.html 的版本占位符必须被替换掉');
  assert.ok(html.includes(`window.__HDAO_VERSION__="${expected}"`), `注入的版本不是 CHANGELOG 里的 ${expected}`);

  // app.html 必须保留占位符，否则注入静默失效，弹窗会显示一个空版本号。
  assert.ok((await read('../public/app.html')).includes('<!-- version -->'), 'app.html 缺少 <!-- version --> 占位符');
});
