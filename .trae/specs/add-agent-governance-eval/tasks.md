# Tasks

- [x] Task 1: E1 权限与 runtime 可见性
  - [x] SubTask 1.1 `UserContext.hasPermission`
  - [x] SubTask 1.2 `FactStoreService` / `FactRetriever` / `ChatService` 透传 `includeRuntime`
  - [x] SubTask 1.3 `AgentFactController` 越权 403 + 默认过滤
  - [x] SubTask 1.4 auth 菜单种子 id 9/10 + `migration-5.47.sql`
- [x] Task 2: E2 脱敏与查询审计
  - [x] SubTask 2.1 `bluewhale_agent` `audit_log` DDL + `migration-5.47.sql`
  - [x] SubTask 2.2 三个查询接口标注 `@AuditLog(QUERY)`
  - [x] SubTask 2.3 `SensitiveDataMasker` 空值 NPE 修复与验证
- [x] Task 3: E3 黄金集与评测
  - [x] SubTask 3.1 `docs/agent/golden-set.json` 40 题
  - [x] SubTask 3.2 `scripts/agent-eval.ps1`（SSE 采集 + 断言 + 报告 + 退出码）
  - [x] SubTask 3.3 实机执行并归档 `docs/reviews/2026-10-22-wave5.47-agent-eval.md`
- [x] Task 4: E4 文档与提交
  - [x] SubTask 4.1 `docs/ops/agent-index-ops.md` 索引运维手册
  - [x] SubTask 4.2 roadmap / devlog / README 更新
  - [x] SubTask 4.3 构建校验 + commit + push Gitee

# Task Dependencies
- Task 3 依赖 Task 1、Task 2（评测需在权限与审计生效后执行）
- Task 4 依赖 Task 1–3
