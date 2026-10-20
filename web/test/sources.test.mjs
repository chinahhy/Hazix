import test from 'node:test';
import assert from 'node:assert/strict';
import { createSourceRegistry, normalizeSourceConfig, parseSourceConfig, publicSourceList } from '../sources.mjs';

test('源配置只接受安全的 HTTP(S) 地址和已知适配器', () => {
  assert.equal(normalizeSourceConfig({ id: 'bad id', name: '坏源', type: 'hdao', baseUrl: 'https://example.com' }), null);
  assert.equal(normalizeSourceConfig({ id: 'bad', name: '坏源', type: 'unknown', baseUrl: 'https://example.com' }), null);
  assert.equal(normalizeSourceConfig({ id: 'bad', name: '坏源', type: 'hdao', baseUrl: 'file:///tmp/source' }), null);
  assert.equal(normalizeSourceConfig({ id: 'bad', name: '坏源', type: 'hdao', baseUrl: 'https://u:p@example.com' }), null);
  assert.equal(normalizeSourceConfig({ id: 'good_1', name: '好源', type: 'apple-cms', baseUrl: 'https://example.com/provide/vod' }).id, 'good_1');
});

test('源配置支持数组和 sources 包装，并去重停用源', () => {
  const config = parseSourceConfig(JSON.stringify({ sources: [
    { id: 'demo', name: '演示源', type: 'hdao', baseUrl: 'https://example.com/api' },
    { id: 'demo', name: '重复源', type: 'hdao', baseUrl: 'https://example.com/api-2' },
    { id: 'off', name: '停用源', type: 'hdao', baseUrl: 'https://example.com/off', enabled: false },
  ] }));
  assert.deepEqual(config.map(source => source.id), ['demo']);
});

test('源注册表只公开非敏感元数据', () => {
  const registry = createSourceRegistry([
    { id: 'demo', name: '演示 CMS', type: 'apple-cms', baseUrl: 'https://example.com/provide/vod', enabled: true },
  ]);
  assert.deepEqual(publicSourceList(registry), [{
    id: 'demo', name: '演示 CMS', type: 'apple-cms', capabilities: ['category', 'search', 'detail'],
  }]);
});

test('苹果 CMS 适配器把列表字段统一到 Web 片库结构', async () => {
  const registry = createSourceRegistry([{ id: 'demo', name: '演示 CMS', type: 'apple-cms', baseUrl: 'https://example.com/provide/vod', enabled: true }]);
  const source = registry.get('demo');
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify({ list: [{
    vod_id: 42,
    vod_name: '演示剧',
    vod_pic: 'https://img.example/42.jpg',
    vod_year: '2026',
    vod_play_url: '第1集$https://media.example/1.m3u8#第2集$https://media.example/2.m3u8',
  }] }), { status: 200, headers: { 'content-type': 'application/json' } });
  try {
    const result = await source.detail({ id: 42 });
    assert.equal(result.item.title, '演示剧');
    assert.equal(result.item.sourceId, 'demo');
    assert.deepEqual(result.episodes.map(episode => episode.name), ['第1集', '第2集']);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
