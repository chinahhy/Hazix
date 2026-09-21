import test from 'node:test';
import assert from 'node:assert/strict';
import { recentProgress, recentHot, categoryItems, escapeHTML, imageURL, playbackURL, previewStartSeconds, ratingOf, isFeedbackKey } from '../public/data.js';
import { apiTarget, tmdbMediaType, tmdbScoreFromHTML } from '../server.mjs';

test('继续观看按节目 ID 和标准化片名去重，排除看完的集数', () => {
  const entry = (vodId, title, updatedAt, position = 40) => ({ vodId, item: { title }, updatedAt, position, duration: 1000 });
  const result = recentProgress({ a: entry(1, '第 一部！', 1), b: entry(1, '第一部', 2), c: entry(2, '第一部', 3), d: entry(3, '第二部', 4), e: entry(4, '看完了', 5, 990) });
  assert.deepEqual(result.map(e => e.vodId), [3, 2]);
});
test('首页推荐保持最近三部电影加三部剧集', () => {
  assert.deepEqual(recentHot({ movies: [1, 2, 3, 4].map(vodId => ({ vodId })), tv: [5, 6, 7, 8].map(vodId => ({ vodId })) }).map(v => v.vodId), [1, 2, 3, 5, 6, 7]);
});
test('评分优先显示豆瓣，无豆瓣时回退到 TMDB', () => {
  assert.deepEqual(ratingOf({ doubanScore: '8.6', tmdbScore: '7.2' }), { source: '豆瓣', score: '8.6' });
  assert.deepEqual(ratingOf({ doubanScore: null, tmdbScore: 7 }), { source: 'TMDB', score: '7.0' });
  assert.equal(ratingOf({ doubanScore: '0', tmdbScore: '11' }), null);
});
test('上游缺分时可从精确 TMDB 页面补充，并严格区分电影与电视', () => {
  assert.equal(tmdbScoreFromHTML('<div class="user_score_chart" data-percent="80"></div>'), '8.0');
  assert.equal(tmdbScoreFromHTML('<div data-percent="90" class="foo user_score_chart"></div>'), '9.0');
  assert.equal(tmdbScoreFromHTML('<div class="user_score_chart" data-percent="0"></div>'), null);
  assert.equal(tmdbMediaType({ parentTypeId: 1 }), 'movie');
  assert.equal(tmdbMediaType({ parentTypeId: 1, typeId: 20 }), 'tv');
  assert.equal(tmdbMediaType({ parentTypeId: 2 }), 'tv');
  assert.equal(tmdbMediaType({ parentTypeId: 54 }), 'tv');
});
test('短剧分类排除站点混入的其他分类', () => {
  assert.deepEqual(categoryItems('short-drama', [{ parentTypeId: 54 }, { parentTypeId: 1 }]), [{ parentTypeId: 54 }]);
});
test('外部文字和图片地址不可执行脚本', () => {
  assert.equal(escapeHTML('<script>"&'), '&lt;script&gt;&quot;&amp;');
  assert.equal(imageURL('javascript:alert(1)'), '');
  assert.equal(imageURL('/hero.jpg', 'original'), '/image?src=%2Ft%2Fp%2Foriginal%2Fhero.jpg');
});
test('播放地址与原生客户端的 base64url 包装兼容，已有包装不重复', () => {
  const input = 'https://example.com/剧集/1.m3u8?a=1&b=2';
  const expected = 'https://stream.hdao.tv/api/proxy/m3u8?url=' + Buffer.from(input).toString('base64url');
  assert.equal(playbackURL(input), expected);
  assert.equal(playbackURL(expected), expected);
});
test('首页静音预览从节目中间开始，未知和极短时长不乱跳', () => {
  assert.equal(previewStartSeconds(2700), 1350);
  assert.equal(previewStartSeconds(10), 0);
  assert.equal(previewStartSeconds(Infinity), 0);
});
test('接口仅允许现有片库路由，不能作为任意网址代理', () => {
  assert.equal(apiTarget(new URL('http://localhost/api/vods/123?url=http://localhost:8000')).href, 'https://hdao.tv/api/vods/123');
  assert.equal(apiTarget(new URL('http://localhost/api/proxy?url=http://localhost')), null);
  assert.equal(apiTarget(new URL('http://localhost/api/admin')), null);
});
test('只有遥控器导航、确认和返回键才触发按键音效', () => {
  for (const key of ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight']) assert.equal(isFeedbackKey(key), true, key);
  for (const key of ['Enter', ' ', 'Spacebar', 'Escape', 'Backspace', 'BrowserBack']) assert.equal(isFeedbackKey(key), true, key);
  // 音量键、电源键、修饰键和组合键不该出声
  for (const key of ['a', 'A', 'AudioVolumeUp', 'AudioVolumeDown', 'Shift', 'Control', 'Meta', 'Tab', 'F5', '']) assert.equal(isFeedbackKey(key), false, key);
  assert.equal(isFeedbackKey('ArrowRight', { ctrlKey: true }), false);
  assert.equal(isFeedbackKey(undefined), false);
});
