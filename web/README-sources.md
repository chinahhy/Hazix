# Web 影视源适配器

Web 预览现在通过源注册表访问片库，默认只启用 Hdao。要在本地预览中加入经过授权、可公开访问的源，可以把 `sources.example.json` 复制到项目外的安全位置，再通过 `HDAO_SOURCES_JSON` 传入 JSON 内容后启动预览。

支持的类型：

- `hdao`：Hazix 当前使用的 `/api/vods` 接口。
- `apple-cms`：苹果 CMS V10 常见的 `provide/vod` JSON 接口，支持搜索、分类和详情/选集。

示例：

```bash
HDAO_SOURCES_JSON="$(<web/sources.example.json)" pnpm --dir web start
```

接口仍然默认访问 Hdao；调试某个源时，在请求中增加 `source=<id>`：

```text
/api/sources
/api/search?q=关键词&source=my-cms
/api/vods?category=movie&page=1&source=my-cms
/api/vods/123?source=my-cms
```

源适配器只负责把上游数据转换为统一的影片、剧集和播放地址结构。网页抓取、绕过登录/验证码、防盗链或未获授权的播放地址不属于适配器范围；这类站点不能因为“能打开网页”就视为可稳定接入。
