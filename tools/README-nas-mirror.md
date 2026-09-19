# NAS 更新中转站（Hazix release mirror）

电视在国内访问 GitHub 时快时慢。NAS 24 小时开机、又在同一个局域网里，
所以让它做中转：**第一次访问回源 GitHub 并落盘，之后直接吃局域网带宽。**

实测（同一台机器，2.8MB 的 TV 包）：

| | 用时 | 速度 |
| --- | --- | --- |
| 首次（回源 GitHub） | 1.12s | 2.5 MB/s |
| 二次（命中 NAS 缓存） | 0.18s | **15.7 MB/s** |

## 它长什么样

程序是 `tools/nas-release-proxy.mjs`，**只依赖 Node 标准库**（Node 18+ 自带 fetch），
没有任何 npm 依赖，所以在你 NAS 上不用装东西。

它刻意保持和 GitHub **完全一样的 URL 结构**，客户端只换域名：

```
原:  https://github.com/chinahhy/Hazix/releases/latest
新:  http://<NAS地址>:8088/chinahhy/Hazix/releases/latest          → 302 到具体 tag

原:  https://github.com/chinahhy/Hazix/releases/download/v3.6.4/Hazix-TV-v3.6.4.apk
新:  http://<NAS地址>:8088/chinahhy/Hazix/releases/download/v3.6.4/Hazix-TV-v3.6.4.apk
```

好处是 App 里判断"是不是合法 release 链接"、"从 tag 里取版本号"的逻辑一个字都不用改，
也不需要额外约定什么 `version.json` 格式。

安全边界：只镜像 `/<repo>/releases/**` 这一个前缀，其它路径一律 404，不会被当成任意网址代理；
TV 包本身还有 SHA-256 校验（和 GitHub release 里的 `SHA256SUMS.txt` 对照）。

## 部署（有 Docker 的 NAS，推荐）

群晖 / 威联通 / TrueNAS / unRAID 都一样，两种写法任选。

### 写法一：compose（群晖「项目」、威联通 Container Station 直接粘）

```yaml
services:
  hazix-mirror:
    image: node:22-alpine
    container_name: hazix-mirror
    restart: unless-stopped
    command: ["node", "/app/nas-release-proxy.mjs"]
    environment:
      PORT: "8088"
      UPSTREAM: "https://github.com"
      REPOSITORY: "chinahhy/Hazix"
      CACHE_DIR: "/cache"
    volumes:
      - /volume1/docker/hazix-mirror:/cache          # 换成你 NAS 上的实际路径
      - /volume1/docker/hazix-mirror/app/nas-release-proxy.mjs:/app/nas-release-proxy.mjs:ro
    ports:
      - "8088:8088"
```

把 `tools/nas-release-proxy.mjs` 拷到 `/volume1/docker/hazix-mirror/app/` 下即可。

### 写法二：命令行

```bash
# 先建目录并放入脚本
mkdir -p /volume1/docker/hazix-mirror/app
cp nas-release-proxy.mjs /volume1/docker/hazix-mirror/app/

docker run -d --name hazix-mirror --restart unless-stopped \
  -p 8088:8088 \
  -e CACHE_DIR=/cache \
  -v /volume1/docker/hazix-mirror:/cache \
  -v /volume1/docker/hazix-mirror/app/nas-release-proxy.mjs:/app/nas-release-proxy.mjs:ro \
  node:22-alpine node /app/nas-release-proxy.mjs
```

也可以用本目录的 `nas-release-proxy.Dockerfile` 自己 build 一个镜像：

```bash
docker build -f nas-release-proxy.Dockerfile -t hazix-mirror:1 .
```

### 写法三：NAS 上已经有 Node 但不想用 Docker

```bash
CACHE_DIR=/volume1/docker/hazix-mirror PORT=8088 node nas-release-proxy.mjs
```

配合 NAS 自带的任务计划（开机启动 / 定时）即可。

## 部署完怎么验证

```bash
# 1) 健康检查
curl http://<NAS地址>:8088/healthz
# 期望 {"ok":true,"upstream":"https://github.com",...}

# 2) 版本查询，应 302 到最新 tag
curl -sI http://<NAS地址>:8088/chinahhy/Hazix/releases/latest | grep -i location

# 3) 完整下载并核对哈希（第一次会慢，第二次应该飞快）
curl -L -o /tmp/tv.apk http://<NAS地址>:8088/chinahhy/Hazix/releases/download/v3.6.4/Hazix-TV-v3.6.4.apk
shasum -a 256 /tmp/tv.apk     # 与 dist/SHA256SUMS.txt 里的一致
```

## 常见问题

- **电视能不能访问**：电视和 NAS 在同一局域网时，直接用 `192.168.x.x:8088` 或 `nas.local:8088`。
  如果电视要用 `http://`（不是 https），App 端已经通过 `network_security_config.xml`
  放行了 RFC1918 私有网段，不需要额外设置。
- **网关/反代**：如果你给 NAS 配了域名或反向代理，把 8088 反代出去也行；
  用 https + 自签证书的话，客户端需要额外信任链，**建议局域网内直接用 http**。
- **容量**：每个版本约 2.8MB（TV）+ 2.7MB（手机），缓存不清理也没压力；
  想清理直接删缓存目录里的文件，下次访问会重新回源。
- **NAS 关机/不在家**：App 侧设计成先试 NAS、失败自动回退 GitHub，所以不会因此卡住更新。
- **限流**：`/releases/latest` 走的是 github.com 的 302，不消耗 API 配额（不用 `api.github.com`）。
