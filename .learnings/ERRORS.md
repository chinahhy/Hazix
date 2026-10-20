# Errors

Command failures, API errors, and unexpected behavior captured during development.

**Areas**: frontend | backend | infra | tests | docs | config
**Statuses**: pending | in_progress | resolved | wont_fix | promoted | promoted_to_skill

---

## [ERR-20261021-001] summon_experts

**Logged**: 2026-10-21T06:50:00+08:00
**Priority**: medium
**Status**: pending
**Area**: infra

### Summary
UI 实施轮次中，两位具名专家执行均返回 error，没有创建可继续工作的成员。

### Error
```text
失败：专家运行以 "error" 结束
```

### Context
- 同一 summon_experts 调用委派 UI 设计师实施网页、UI 视觉验收设计师只读验收。
- list_agents 仅返回 lead，git status --short 无输出；未发现遗留改动。
- 工具没有提供进一步错误原因，不推断额度、模型或网络根因。

### Suggested Fix
先确认成员与仓库状态，不把启动失败称为已完成实施或专家验收；向用户明确失败。本轮未重试专家，由主会话依照用户已确认方案实施并独立执行浏览器检查。

### Metadata
- Reproducible: unknown
- Related Files: web/public/app.js, web/public/app.css, HANDOFF.md

---

## [ERR-20261021-002] cdp_safe_area

**Logged**: 2026-10-21T07:06:00+08:00
**Priority**: low
**Status**: resolved
**Area**: tests

### Summary
Chrome 安全区模拟的 CDP 命令名应为 Emulation.setSafeAreaInsetsOverride。

### Error
```text
{"code":-32601,"message":"'Emulation.setSafeAreaInsets' wasn't found"}
```

### Context
- 浏览器回归尝试模拟34px底部安全区时误用了不存在的命令名。
- 改为 Emulation.setSafeAreaInsetsOverride 后，实测导航高度100px、标签与首屏海报不被安全区遮挡。

### Suggested Fix
复用回归脚本中已验证的 Override 命令；方法不存在不能直接解释为浏览器不支持安全区模拟。

### Metadata
- Reproducible: yes
- Related Files: web/test/ui.browser.mjs

### Resolution
- **Resolved**: 2026-10-21T07:06:00+08:00
- **Notes**: 真正的 Chrome 安全区模拟与布局断言均通过，没有用 CSS 假数据替代。

---
