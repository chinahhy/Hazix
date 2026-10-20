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
