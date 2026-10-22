# Wave 5.47 轨 E — 任务清单

> 依据：[spec.md](spec.md)。完成一项即勾选，并在 `docs/devlog/2026-10-22.md` 留记录。

## E1 权限与 runtime 可见性

- [x] E1-1 `UserContext` 新增 `hasPermission(request, code)`
- [x] E1-2 `FactStoreService` 新增 `query(..., boolean includeRuntime)` 重载
- [x] E1-3 `AgentFactController` runtime 越权 403 + 默认过滤
- [x] E1-4 `FactRetriever` 支持 `includeRuntime`
- [x] E1-5 `ChatService` / `AgentChatController` 透传 `includeRuntime`
- [x] E1-6 auth `init.sql` 补 agent 菜单种子（id 9/10）+ `migration-5.47.sql`

## E2 脱敏与查询审计

- [x] E2-1 `bluewhale_agent` `init.sql` 补 `audit_log` DDL
- [x] E2-2 新建 agent `migration-5.47.sql`（`CREATE TABLE IF NOT EXISTS audit_log`）
- [x] E2-3 `/agent/search`、`/agent/facts`、`/agent/chat` 标注 `@AuditLog(QUERY)`
- [x] E2-4 验证：`audit_log` 落库 + 索引产物无明文密钥

## E3 黄金集与评测

- [x] E3-1 `docs/agent/golden-set.json` 40 题（4 类各 10 题）
- [x] E3-2 `scripts/agent-eval.ps1`（登录 → SSE → 断言 → Markdown 报告 → 阈值退出码）
- [x] E3-3 实机执行并归档 `docs/reviews/2026-10-22-wave5.47-agent-eval.md`

## E4 文档与提交

- [x] E4-1 `docs/plans/roadmap.md` 勾选轨 E
- [x] E4-2 `docs/devlog/2026-10-22.md` 追加轨 E 章节
- [x] E4-3 `docs/service-catalog.md` / `docs/README.md` 更新
- [x] E4-4 `docs/ops/agent-index-ops.md` 索引运维手册
- [x] E4-5 后端构建通过 + commit + `git push origin main`
