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
/**
 * 回源地址。默认直连 github.com；如果这台 NAS（或它所在的网络）到 GitHub 不通，
 * 用 UPSTREAM 指一个 GitHub 加速前缀，例如：
 *   UPSTREAM=https://gh-proxy.com/https://github.com
 * 脚本会把请求拼成 <UPSTREAM>/<repo>/releases/... ，并把上游 Location 里的
 * github.com 也改写回同一个前缀，保证客户端不会拿到它自己访问不了的地址。
 */
const UPSTREAM = (process.env.UPSTREAM || 'https://github.com').replace(/\/+$/, '');
/**
 * 回源候选，按顺序尝试，逗号分隔。默认只有 github.com。
 * 有些网络到 GitHub 不通但对加速镜像通，而加速镜像的能力不一样：
 * 例如 gh-proxy.com **只代理资源下载、拒绝网页请求**，所以它下得动 APK 却问不到版本号。
 * 因此每个候选各司其职：谁能解析版本号就用谁解析，谁能下资产就用谁下。
 *
 * 例：UPSTREAM=https://github.com,https://gh-proxy.com/https://github.com
 */
const UPSTREAMS = [UPSTREAM, ...(process.env.UPSTREAM_FALLBACKS || '')
  .split(',')
  .map((value) => value.trim().replace(/\/+$/, ''))
  .filter(Boolean)]
  .filter((value, index, all) => all.indexOf(value) === index);
const GITHUB_URL = /^https:\/\/([a-z0-9-]+\.)*github\.com\//i;
const isProxy = (upstream) => !/^https:\/\/github\.com$/i.test(upstream);
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

/**
 * Builds the URL this process actually fetches.
 *
 * With the default upstream this is github.com itself. When UPSTREAM is an
 * accelerator (`https://gh-proxy.com/https://github.com`), a github.com URL is
 * fed to it as-is - `https://gh-proxy.com/https://github.com/<path>` - which is
 * the form these services expect. The githubusercontent.com hosts that release
 * downloads redirect to are left alone: accelerators resolve those themselves.
 */
function upstreamUrl(upstream, githubUrl) {
  if (!isProxy(upstream)) return githubUrl;
  // Callers hand in a full github.com URL; an accelerator wants the bare path
  // appended to its prefix (`<prefix>/<owner>/<repo>/...`), so drop the host
  // first. Doing it here keeps both call sites from having to know the shape.
  const path = githubUrl.replace(/^https:\/\/github\.com/i, '');
  return `${upstream}${path}`;
}

const TAG_PATTERN = /^v\d+(\.\d+)*$/;

/** Reads the release tag out of a response, however the upstream reported it. */
async function tagFromResponse(response) {
  const direct = response.headers.get('location')
    || response.headers.get('x-hazix-tag')
    || response.url
    || '';
  const fromHeader = direct.match(/\/releases\/tag\/([^/?#]+)/)?.[1];
  if (fromHeader) return fromHeader;
  // Accelerators that refuse API-style requests may still answer a browser with
  // the rendered tag page, so fall back to scanning the body for the tag link.
  const body = await response.text().catch(() => '');
  const linked = body.match(/\/releases\/tag\/(v\d+(?:\.\d+)*)/)?.[1];
  if (linked) return linked;
  const named = body.match(/\b(v\d+(?:\.\d+)*)\b/)?.[1];
  return named && TAG_PATTERN.test(named) ? named : null;
}

async function resolveLatestTag() {
  let lastError = null;
  for (const upstream of UPSTREAMS) {
    try {
      const response = await fetch(upstreamUrl(upstream, `https://github.com/${REPOSITORY}/releases/latest`), {
        // Accelerators may follow the tag redirect themselves and answer 200
        // with the final URL; a direct github.com upstream answers 302.
        redirect: 'follow',
        headers: { 'User-Agent': 'Mozilla/5.0 (compatible; hazix-nas-mirror)' },
        signal: AbortSignal.timeout(20_000),
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const tag = await tagFromResponse(response);
      if (!tag) throw new Error('响应里没有版本号');
      log('版本号来自', upstream);
      return tag;
    } catch (error) {
      lastError = error;
      log('解析版本号失败', upstream, error.message);
    }
  }
  throw new Error(`所有回源都问不到最新版本：${lastError?.message || '未知错误'}`);
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
  const githubUrl = `https://github.com${pathname}`;
  let entry = await cachedEntry(pathname);
  const range = req.headers.range;

  /** The first upstream that answers this asset, or null when none can. */
  async function reachableTarget() {
    let lastError = null;
    for (const upstream of UPSTREAMS) {
      const candidate = upstreamUrl(upstream, githubUrl);
      try {
        const probe = await fetch(candidate, {
          method: 'HEAD',
          redirect: 'follow',
          headers: { 'User-Agent': 'hazix-nas-mirror' },
          signal: AbortSignal.timeout(15_000),
        });
        // An accelerator answers 404 for a path it does not proxy, which is how
        // a "resolves the tag but cannot serve assets" candidate drops out.
        if (probe.ok) return candidate;
        lastError = new Error(`HTTP ${probe.status}`);
      } catch (error) {
        lastError = error;
      }
      log('下载回源不可用', upstream, lastError.message);
    }
    throw new Error(`所有回源都拿不到更新包：${lastError?.message || '未知错误'}`);
  }

  // Range 请求直接吃缓存（APK 断点续传）；整文件请求先确认上游没变。
  if (entry && !range) {
    try {
      const head = await upstreamHead(await reachableTarget());
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
      const upstream = await fetch(await reachableTarget(), { headers: { Range: range }, redirect: 'follow', signal: AbortSignal.timeout(600_000) });
      res.writeHead(upstream.status, {
        'Content-Type': upstream.headers.get('content-type') || 'application/octet-stream',
        'Content-Length': upstream.headers.get('content-length') || '',
        'Content-Range': upstream.headers.get('content-range') || '',
        'Accept-Ranges': 'bytes',
      });
      if (!upstream.body) return res.end();
      return pipeline(Readable.fromWeb(upstream.body), res).catch(() => res.destroy());
    }
    entry = await downloadToCache(
      await reachableTarget(),
      cachePathFor(pathname),
      'application/vnd.android.package-archive',
    );
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
        // Answer with the tag directly instead of a redirect: the client may not
        // be able to reach github.com, and the tag is all it needs.
        return send(
          res,
          302,
          {
            Location: `${UPSTREAM}/${REPOSITORY}/releases/tag/${tag}`,
            'X-Hazix-Tag': tag,
            'Cache-Control': 'no-store',
          },
          '',
        );
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
