# Checklist：权限中心残余收口（Wave 5.11）

> 关联 spec：[spec.md](./spec.md) ｜ 关联 tasks：[tasks.md](./tasks.md)
> 逐项核验，通过则勾选；未通过需在 tasks.md 新增修复任务后重新核验。

## 一、文档先行

- [x] 1.1 spec.md / tasks.md / checklist.md 已落地，含 4 项残余的需求、Scenario、风险与验收口径
- [x] 1.2 `docs/design/audit-log.md` 已更新：`audit_log.result_body` 列、`recordResult()` 语义、读审计元审计（QUERY）
- [x] 1.3 `docs/design/distributed.md` §3.6 已补黑名单降级告警口径（ERROR + `SECURITY_DEGRADATION` + 计数）

## 二、AUTH-01 网关占位密钥校验

- [x] 2.1 `WhaleGatewayProperties.Jwt` 暴露 `DEFAULT_PLACEHOLDER_SECRET` 常量，`secret` 默认值引用该常量（无字面量重复）
- [x] 2.2 `GatewaySecretPlaceholderChecker` 为 `@Component` + `InitializingBean`，用 `Environment` 判 profile（`dev` 放行，大小写不敏感）
- [x] 2.3 非 dev 且密钥为占位值 → 抛 `IllegalStateException`（启动失败并告警）；非 dev 且已覆盖 / dev → 放行
- [x] 2.4 单测覆盖「非 dev 占位拒绝 / dev 放行 / 非 dev 已覆盖放行」

## 三、AUTH-05 黑名单降级告警

- [x] 3.1 `whale-security` 新增 `SecurityDegradationAlerter`：ERROR 级 + 固定标记 `SECURITY_DEGRADATION` + 计数（`AtomicLong`），并提供测试用读取/重置
- [x] 3.2 whale-auth `RedisTokenBlacklist` 的 `add` / `contains` 降级分支接入告警；`add` 不抛、`contains` 返回 `false`（既有语义不变）
- [x] 3.3 网关 `TokenBlacklistChecker` 的 `onErrorResume` 降级分支接入告警，仍返回 `Mono.just(Boolean.FALSE)` 放行
- [x] 3.4 单测断言降级后计数 +1 且既有返回值语义不变

## 四、AUTH-13 `recordResult()` 落库

- [x] 4.1 `whale-auth/db/init.sql` 与 `whale-audit/db/init.sql` 的 `audit_log` 均新增 `result_body TEXT NULL`（注释对齐，其余字段/索引不变）
- [x] 4.2 `AuditLogRecord` 新增 `resultBody` 字段（`@TableName("audit_log")` 映射到 `result_body`）
- [x] 4.3 `AuditLogAspect#buildRecord` 在 `recordResult()=true` 且返回体非空时写入 `resultBody`：JSON 序列化 → `maskJson` 脱敏 → 上限截断
- [x] 4.4 未开启 `recordResult`（含默认）时 `result_body` 为 `NULL`；序列化失败/超大仅告警或截断，不冒泡
- [x] 4.5 `AuditLogVO` 暴露 `resultBody`，查询接口可读出
- [x] 4.6 单测覆盖「开启 / 默认不落 / 超大截断 / 脱敏生效 / 序列化失败仅告警」

## 五、AUTH-07 审计读取元审计

- [x] 5.1 `AuditLogController` 的 `/page`、`/{id}`、`/trace/{traceId}` 均加 `@AuditLog(type = OperateType.QUERY)`（`@RequirePermission` 保持不变）
- [x] 5.2 元审计经既有切面落同库 `audit_log`（`operate_type=QUERY`），并可由查询接口反查
- [x] 5.3 单测断言三方法注解存在且 `type=QUERY`；无导出接口故不做 `EXPORT`

## 六、收尾归置

- [x] 6.1 `docs/whale-auth/originals/` 按 `design/plans/requirements/specs/reviews/devlog` 存放**副本**，覆盖权限中心全部相关文档
- [x] 6.2 原位置文档路径与内容未改动、未删除（`git status` 无 `docs/**` 删除）
- [x] 6.3 `docs/whale-auth/final/` 输出整合最终版：能力矩阵 / 接口与权限点清单 / 关键配置项 / 表结构 / 已闭环与残余阻塞项 / 原文索引

## 七、测试与门禁

- [x] 7.1 `mvn -f whale-backend/pom.xml clean verify -T 1C`：BUILD SUCCESS，全模块既有用例不回归
- [x] 7.2 `mvn -f whale-backend/pom.xml checkstyle:check`：全 reactor 0 违规（E16 Javadoc / 行宽 ≤150 / C4 ≤4 参 / 魔法值）
- [x] 7.3 新增单测全部纳入构建（无 `@Disabled` 逃逸）

## 八、留痕与销账

- [x] 8.1 检查报告 `docs/reviews/2026-10-18-wave5.11-auth-residual.md` 逐项对应 [code-review-checklist.md](../../checklists/code-review-checklist.md) 编号
- [x] 8.2 `docs/devlog/2026-10-18.md` 追加本波条目，四节（需求项 / 完成项 / 待做项 / 已修正问题项）齐备
- [x] 8.3 台账销账：`whale-auth-backlog.md`（AUTH-01/05/07/13 删除线 + ✅）、`register.md`、`next-steps.md`
- [x] 8.4 变更已 commit 并 push 至 Gitee `origin/main`，工作区干净且 `main == origin/main`

## 九、核验结论

- 核验人：BlueWhale ｜ 日期：2026-10-18 ｜ 结论：**通过**（§一~§八 全部核验项通过；`clean verify -T 1C` 28 模块 SUCCESS、263 用例全绿，`checkstyle:check` 28 模块 0 违规；`docs/whale-auth/{originals,final}` 文档归置齐备）
