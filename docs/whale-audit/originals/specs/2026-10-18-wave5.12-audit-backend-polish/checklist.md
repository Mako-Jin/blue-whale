# Checklist：Wave 5.12 审计日志后端补强（REQ-SEC-006 残余收口）

> 关联 spec：[spec.md](./spec.md) ｜ 关联 tasks：[tasks.md](./tasks.md)
> 逐项核验，通过则勾选；未通过需在 tasks.md 新增修复任务后重新核验。

## 一、文档先行

- [x] 1.1 spec.md / tasks.md / checklist.md 已落地，含三项残余的需求、设计与验收口径

## 二、E1 `api_log.module` 回填

- [x] 2.1 `ApiLogProperties` 新增 `module` 字段，默认 `${spring.application.name:unknown}`（无硬编码服务名）
- [x] 2.2 `ApiLogFilter#fillRequest` 设置 `record.setModule(properties.getModule())`
- [x] 2.3 单测断言落库记录 `module` 非空且等于配置值

## 三、E2 网关按路径限流

- [x] 3.1 `RateLimitProperties` 新增 `List<PathRule> pathRules`，`PathRule` 含 `pathPattern`/`limit`/`windowSeconds`
- [x] 3.2 `RateLimitGlobalFilter` 按最长 `pathPattern` 匹配选取规则；未命中沿用全局阈值
- [x] 3.3 Redis 计数键加入规则维度；键前缀仍为 `whale:gateway:rate-limit`
- [x] 3.4 `whale-gateway/application.yml` 追加上报路径（`/audit/log/frontend/**`、`/audit/log/behavior/**`）规则
- [x] 3.5 单测覆盖「规则命中 / 未命中 / 超阈值 429 / 未启用直放行」；`pathRules` 为空时行为与现状一致

## 四、E3 保留期扩多表

- [x] 4.1 新增 `RetentionProperties`（`whale.audit.retention`：`enabled` 默认 false、`days` 默认 30、`cron`）
- [x] 4.2 新增 `LogRetentionScheduler`：`@ConditionalOnProperty(enabled=true)` + `@Scheduled`，删除 4 表 `create_time < now - days`
- [x] 4.3 **不清理 `audit_log`**；删除失败仅 `warn`，不影响业务
- [x] 4.4 `WhaleAuditApplication` 加 `@EnableScheduling`
- [x] 4.5 单测覆盖「enabled=false 不清理 / enabled=true 删除阈值前记录」

## 五、测试与门禁

- [x] 5.1 `mvn -f whale-backend/pom.xml clean verify -T 1C`：BUILD SUCCESS，全模块既有用例不回归
- [x] 5.2 `mvn -f whale-backend/pom.xml checkstyle:check`：全 reactor 0 违规（E16 Javadoc / 行宽 ≤150 / C4 ≤4 参 / 魔法值）
- [x] 5.3 新增单测全部纳入构建（无 `@Disabled` 逃逸）

## 六、留痕与销账

- [x] 6.1 检查报告 `docs/reviews/2026-10-18-wave5.12-audit-backend-polish.md` 逐项对应 [code-review-checklist.md](../../checklists/code-review-checklist.md) 编号
- [x] 6.2 devlog 追加本波条目，四节（需求项 / 完成项 / 待做项 / 已修正问题项）齐备
- [x] 6.3 台账销账：`register.md`（REQ-SEC-006 残余收口）、`next-steps.md`
- [x] 6.4 变更已 commit 并 push 至 Gitee `origin/main`，工作区干净且 `main == origin/main`

## 七、核验结论

- 核验人：BlueWhale ｜ 日期：2026-10-18 ｜ 结论：通过
- 门禁实测：`clean verify -T 1C` BUILD SUCCESS（28 模块全 SUCCESS，292 用例 0 fail·0 error·0 skipped）；`checkstyle:check` 全 reactor **0 违规**。
- 证据：检查报告 [2026-10-18-wave5.12-audit-backend-polish.md](../../reviews/2026-10-18-wave5.12-audit-backend-polish.md)；devlog §32。
