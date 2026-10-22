# 知识 Agent 治理与评测（Wave 5.47 轨 E）Spec

## Why
知识 Agent 已具备索引、结构化事实、问答编排与前端工作台，但缺少权限隔离、查询审计与质量评测基线，无法证明「越权不可见、敏感不外泄、回答可信」。

## What Changes
- 运行时事实（`RUNTIME_SERVICE`）按 `sys:agent:runtime:read` 权限隔离：显式查询越权返回 403，未授权时默认从结果中剔除。
- `bluewhale_agent` 库补 `audit_log` 表，`/agent/search`、`/agent/facts`、`/agent/chat` 标注 `@AuditLog(QUERY)`，只记查询元数据不记回答正文。
- 复用既有的 `SensitiveContentMasker`/`SensitiveDataMasker`，修复其空值 NPE，并以索引产物扫描留证。
- 新增 40 题黄金集（`docs/agent/golden-set.json`，4 类各 10 题）与 `scripts/agent-eval.ps1` 评测脚本，输出 Markdown 报告并按阈值返回退出码。
- 文档补齐：`docs/ops/agent-index-ops.md`、roadmap 轨 E 勾选、devlog 记录、docs README 目录树、auth 菜单种子（id 9/10）。

## Impact
- Affected specs：Wave 5.47 项目规划 E1–E4（[wave5.47-project-agent.md](../../../docs/plans/wave5.47-project-agent.md)、[wave5.47-agent-governance/spec.md](../../../docs/plans/wave5.47-agent-governance/spec.md)）
- Affected code：`whale-agent`（`UserContext`/`FactStoreService`/`FactRetriever`/`ChatService`/三个 Controller/DB 脚本）、`whale-auth` 菜单种子、`whale-common` 脱敏器、`scripts/agent-eval.ps1`、`docs/` 系列文档

## ADDED Requirements
### Requirement: 运行时事实权限隔离
系统 SHALL 按 `sys:agent:runtime:read` 权限码隔离 `RUNTIME_SERVICE` 事实。

#### Scenario: 越权显式查询
- **WHEN** 请求未携带 `sys:agent:runtime:read` 且 `GET /agent/facts?type=RUNTIME_SERVICE`
- **THEN** 返回 HTTP 403

#### Scenario: 未授权默认过滤
- **WHEN** 请求未携带该权限且不带 `type` 查询事实
- **THEN** 结果中不含 `RUNTIME_SERVICE`，问答引用同样不出现运行时事实

### Requirement: 查询审计与敏感脱敏
系统 SHALL 对知识检索、事实查询、知识问答落 `audit_log`（`operate_type=QUERY`），且索引产物与审计参数中不得出现明文密钥。

#### Scenario: 审计落库
- **WHEN** 调用 `/agent/search`、`/agent/facts`、`/agent/chat`
- **THEN** `bluewhale_agent.audit_log` 新增 `QUERY` 记录，响应正文不被记录

### Requirement: 黄金集评测
系统 SHALL 提供 40 题黄金集与可重复执行的评测脚本，输出归档报告。

#### Scenario: 达标
- **WHEN** 执行 `scripts/agent-eval.ps1`
- **THEN** 总体 ≥80%、结构化 ≥95%、引用有效率 100%，脚本退出码 0，报告写入 `docs/reviews/`
