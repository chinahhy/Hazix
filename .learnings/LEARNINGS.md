# Learnings

Corrections, insights, and knowledge gaps captured during development.

**Categories**: correction | knowledge_gap | best_practice
**Areas**: frontend | backend | infra | tests | docs | config
**Statuses**: pending | in_progress | resolved | wont_fix | promoted | promoted_to_skill

---

## [LRN-20261021-001] best_practice

**Logged**: 2026-10-21T07:06:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: tests

### Summary
CDP 的 Page.navigate 只改变 hash 时不会新建文档；UI 用例隔离要显式更换查询标识并等待页面加载。

### Details
初轮浏览器用例复用了前一页面的取数记录和待响应更新请求，误报手机视频预览与弹窗竞态。每次导航加入唯一 ui-run 查询参数并等待 Page.loadEventFired 后，两项用例均通过；没有为迎合错误断言修改产品逻辑。

### Suggested Action
沿用测试中的唯一导航标识；不能把 Page.navigate 返回直接当作新文档已经加载。

### Metadata
- Source: error
- Related Files: web/test/ui.browser.mjs
- Tags: chrome, cdp, test-isolation

### Resolution
- **Resolved**: 2026-10-21T07:06:00+08:00
- **Notes**: 第二轮本机 Chrome 回归确认上述两项通过。

---

## [LRN-20261021-002] best_practice

**Logged**: 2026-10-21T07:06:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: frontend

### Summary
布局矩形未重叠不等于控件实际可见；应结合截图与中心点命中检查。

### Details
手机巨幕背景曾固定为560px，巨幕缩短后背景仍越过自身区域，盖住下方最近热播标题与轮播按钮。控件矩形仍在正确位置，纯坐标检查会通过。背景改为随巨幕自身填充，并加入 elementFromPoint 检查轮播按钮没有被盖住。

### Suggested Action
首屏布局回归同时检查占位、实际命中和截图；不能只断言 scrollWidth 或元素边界。

### Metadata
- Source: error
- Related Files: web/public/app.css, web/test/ui.browser.mjs
- Tags: stacking-context, screenshot, hit-testing

### Resolution
- **Resolved**: 2026-10-21T07:06:00+08:00
- **Notes**: 六个视口的控件命中检查通过，手机截图已重新查看。

---

## [LRN-20261021-003] correction

**Logged**: 2026-10-21T07:59:12+08:00
**Priority**: medium
**Status**: resolved
**Area**: frontend

### Summary
截图和临时验收服务不等于用户可打开的网页预览，交付时必须明确访问入口和运行状态。

### Details
用户反馈看不到网页。上一轮测试完成即关闭服务，只提供截图；4173端口确实没有监听。经用户批准启动原有预览并保持运行后，页面、资源及真实推荐接口均返回200。

### Suggested Action
交付可交互预览前先取得常驻服务授权，使用托管后台任务启动，验证准确URL，再给可点击链接；仅截图或临时测试时明确说明没有在线预览。不得为解决访问问题擅自绑定公网或改为局域网开放。

### Metadata
- Source: user_feedback
- Related Files: web/server.mjs, HANDOFF.md
- Tags: preview, handoff, service-lifecycle

### Resolution
- **Resolved**: 2026-10-21
- **Notes**: 原有127.0.0.1:4173预览经授权运行，TV/手机入口及6条真实推荐已验证。

---
