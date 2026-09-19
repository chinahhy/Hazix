#!/usr/bin/env node
/**
 * NAS 上的更新中转站（Hazix release mirror）。
 *
 * 为什么要它：电视在国内网络访问 github.com 时快时慢，而 NAS 24 小时开机、
 * 就在同一个局域网里。这个进程把 GitHub 的 release 路径原样代理一遍，
 * 第一次访问时从 GitHub 拉取并落盘缓存，之后电视就直接吃局域网带宽。
 *
 * 它刻意保持和 GitHub 完全一样的 URL 结构，客户端只换域名即可：
 *
 *   https://github.com/chinahhy/Hazix/releases/latest
 *     → http://<NAS>:8088/chinahhy/Hazix/releases/latest          (302 到具体 tag)
 *   https://github.com/chinahhy/Hazix/releases/download/v3.6.4/Hazix-TV-v3.6.4.apk
 *     → http://<NAS>:8088/chinahhy/Hazix/releases/download/v3.6.4/Hazix-TV-v3.6.4.apk
 *
 * 只依赖 Node 标准库（内建 fetch 需要 Node 18+），支持 Range 断点续传，
 * 带磁盘缓存与 /healthz 健康检查。可以用 Docker 跑（见 Dockerfile），
 * 也可以直接 `node nas-release-proxy.mjs`。
 */

import http from 'node:http';
import { createHash } from 'node:crypto';
import { mkdir, readFile, rename, stat, unlink, writeFile } from 'node:fs/promises';
import { createReadStream, createWriteStream } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';

const PORT = Number(process.env.PORT || 8088);
const HOST = process.env.HOST || '0.0.0.0';
const CACHE_DIR = process.env.CACHE_DIR || '/cache';
const UPSTREAM = (process.env.UPSTREAM || 'https://github.com').replace(/\/+$/, '');
const REPOSITORY = process.env.REPOSITORY || 'chinahhy/Hazix';
const LOG = process.env.LOG !== '0';

/** GitHub 会 302 到 objects.githubusercontent.com，这个域名要放行。 */
const ALLOWED_URL = /^https:\/\/([a-z0-9-]+\.)*(github\.com|githubusercontent\.com)\//i;

const log = (...args) => { if (LOG) console.log(new Date().toISOString(), ...args); };

function send(res, status, headers, body) {
  res.writeHead(status, headers);
  res.end(body);
}

function json(res, status, payload) {
  send(res, status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' }, JSON.stringify(payload));
}

const cachePathFor = (pathname) => path.join(CACHE_DIR, createHash('sha256').update(pathname).digest('hex'));

/**
 * 只允许镜像本仓库的 release 路径，避免被当成任意网址的代理。
 * 返回 null 表示这个路径不该被代理。
 */
export function releaseRoute(pathname) {
  if (!pathname || pathname.includes('..')) return null;
  const prefix = `/${REPOSITORY}/releases/`;
  if (!pathname.startsWith(prefix)) return null;
  const rest = pathname.slice(prefix.length);
  if (rest === 'latest') return { kind: 'latest' };
  const download = rest.match(/^download\/([^/]+)\/([^/]+)$/);
  if (download) return { kind: 'download', tag: download[1], name: download[2] };
  return null;
}

async function resolveLatestTag() {
  const response = await fetch(`${UPSTREAM}/${REPOSITORY}/releases/latest`, {
    redirect: 'manual',
    headers: { 'User-Agent': 'hazix-nas-mirror' },
    signal: AbortSignal.timeout(15_000),
  });
  const location = response.headers.get('location') || '';
  const tag = location.match(/\/releases\/tag\/([^/?#]+)/)?.[1];
  if (!tag) throw new Error(`上游没有给出 release tag（HTTP ${response.status}）`);
  return tag;
}

/**
 * 缓存条目：<hash>.bin 是文件本体，<hash>.json 记录 etag / 长度 / 时间。
 * 命中时先发一个条件请求确认上游没换过文件，换了就重下。
 */
async function cachedEntry(pathname) {
  const file = cachePathFor(pathname);
  try {
    const meta = JSON.parse(await readFile(`${file}.json`, 'utf8'));
    const info = await stat(file);
    if (meta.length !== info.size) throw new Error('缓存长度不一致');
    return { file, meta, size: info.size };
  } catch {
    return null;
  }
}

async function upstreamHead(url) {
  const response = await fetch(url, { method: 'HEAD', redirect: 'follow', signal: AbortSignal.timeout(15_000) });
  if (!response.ok) throw new Error(`上游返回 ${response.status}`);
  return {
    etag: response.headers.get('etag') || '',
    length: Number(response.headers.get('content-length') || 0),
    type: response.headers.get('content-type') || 'application/octet-stream',
  };
}

async function downloadToCache(url, file, type) {
  await mkdir(CACHE_DIR, { recursive: true });
  const partial = `${file}.part`;
  const response = await fetch(url, { redirect: 'follow', signal: AbortSignal.timeout(600_000) });
  if (!response.ok || !response.body) throw new Error(`上游返回 ${response.status}`);
  await pipeline(Readable.fromWeb(response.body), createWriteStream(partial));
  const info = await stat(partial);
  if (!info.size) { await unlink(partial).catch(() => {}); throw new Error('上游返回空文件'); }
  await rename(partial, file);
  const meta = { etag: response.headers.get('etag') || '', length: info.size, type, at: Date.now(), url };
  await writeFile(`${file}.json`, JSON.stringify(meta));
  log('已缓存', path.basename(file), info.size, '字节');
  return { file, meta, size: info.size };
}

async function serveDownload(route, req, res) {
  const pathname = `/${REPOSITORY}/releases/download/${route.tag}/${route.name}`;
  const target = `${UPSTREAM}${pathname}`;
  let entry = await cachedEntry(pathname);
  const range = req.headers.range;

  // Range 请求直接吃缓存（APK 断点续传）；整文件请求先确认上游没变。
  if (entry && !range) {
    try {
      const head = await upstreamHead(target);
      if (head.etag && entry.meta.etag && head.etag !== entry.meta.etag) {
        log('上游已更新，重新缓存', route.name);
        entry = null;
      } else if (head.length && head.length !== entry.size) {
        log('上游长度变化，重新缓存', route.name);
        entry = null;
      }
    } catch (error) {
      log('上游确认失败，直接用缓存：', error.message);
    }
  }

  if (!entry) {
    if (range) {
      // 没有缓存却要续传：直接透传上游，不落盘。
      const upstream = await fetch(target, { headers: { Range: range }, redirect: 'follow', signal: AbortSignal.timeout(600_000) });
      res.writeHead(upstream.status, {
        'Content-Type': upstream.headers.get('content-type') || 'application/octet-stream',
        'Content-Length': upstream.headers.get('content-length') || '',
        'Content-Range': upstream.headers.get('content-range') || '',
        'Accept-Ranges': 'bytes',
      });
      if (!upstream.body) return res.end();
      return pipeline(Readable.fromWeb(upstream.body), res).catch(() => res.destroy());
    }
    entry = await downloadToCache(target, cachePathFor(pathname), 'application/vnd.android.package-archive');
  }

  const info = await stat(entry.file);
  const start = range ? Number(range.match(/bytes=(\d+)-/)?.[1] || 0) : 0;
  const end = range ? Math.min(info.size - 1, Number(range.match(/bytes=\d+-(\d+)/)?.[1] || info.size - 1)) : info.size - 1;
  const headers = {
    'Content-Type': entry.meta.type,
    'Accept-Ranges': 'bytes',
    'Cache-Control': 'public, max-age=86400',
  };
  if (range) {
    headers['Content-Range'] = `bytes ${start}-${end}/${info.size}`;
    headers['Content-Length'] = String(end - start + 1);
    res.writeHead(206, headers);
    if (req.method === 'HEAD') return res.end();
    return pipeline(createReadStream(entry.file, { start, end }), res).catch(() => res.destroy());
  }
  headers['Content-Length'] = String(info.size);
  res.writeHead(200, headers);
  if (req.method === 'HEAD') return res.end();
  return pipeline(createReadStream(entry.file), res).catch(() => res.destroy());
}

export function createServer() {
  return http.createServer(async (req, res) => {
    const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    try {
      if (url.pathname === '/healthz') {
        return json(res, 200, { ok: true, upstream: UPSTREAM, repository: REPOSITORY, cache: CACHE_DIR });
      }
      if (!['GET', 'HEAD'].includes(req.method)) return json(res, 405, { error: '仅支持读取请求' });

      const route = releaseRoute(url.pathname);
      if (!route) return json(res, 404, { error: '这个中转站只镜像本仓库的 release 路径' });

      if (route.kind === 'latest') {
        const tag = await resolveLatestTag();
        return send(res, 302, { Location: `${UPSTREAM}/${REPOSITORY}/releases/tag/${tag}`, 'Cache-Control': 'no-store' }, '');
      }
      return await serveDownload(route, req, res);
    } catch (error) {
      log('失败', url.pathname, error.name, error.message);
      if (res.headersSent) return res.destroy();
      return json(res, 502, { error: '中转站暂时拿不到更新包', detail: error.message });
    }
  });
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  createServer().listen(PORT, HOST, () => log(`Hazix 更新中转站已启动：http://${HOST}:${PORT} （缓存目录 ${CACHE_DIR}）`));
}
