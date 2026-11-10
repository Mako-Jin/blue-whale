# Checklist：Wave 5.13 AUTH-39 refreshToken 一次性轮换实施

> 关联 spec：[spec.md](./spec.md) ｜ 关联 tasks：[tasks.md](./tasks.md)
> 逐项核验，通过则勾选；未通过需在 tasks.md 新增修复任务后重新核验。

## 一、文档先行

- [x] 1.1 spec.md / tasks.md / checklist.md 已落地，含轮换需求、处理顺序、风险与验收口径

## 二、键约定与 JWT

- [x] 2.1 `TokenBlacklistKeys` 新增 `refreshUsedKey(jti)`（前缀 `whale:auth:refresh:used:`）与 `refreshFamilyKey(familyId)`（前缀 `whale:auth:refresh:family:`）
- [x] 2.2 既有 `auth:token:blacklist:` 键前缀**未改动**
- [x] 2.3 `JwtTokenProvider` 新增 `CLAIM_FAMILY_ID`；`generateRefreshToken(userId, username, familyId)` 写入 familyId；accessToken 不含 familyId
- [x] 2.4 键约定单测 + refreshToken familyId 断言通过

## 三、轮换存储

- [x] 3.1 `RefreshTokenRotationStore` 接口（`markUsed` / `isFamilyRevoked` / `revokeFamily`）已定义
- [x] 3.2 `RedisRefreshTokenRotationStore` 用 `SET NX` 实现 `markUsed`；首次 true、二次 false
- [x] 3.3 Redis 异常时 `markUsed→false`、`isFamilyRevoked→true`（fail-closed），并 `warn` + `SecurityDegradationAlerter.alarm`
- [x] 3.4 `RefreshRotationProperties`（`whale.auth.refresh.rotation.enabled` 默认 `false`）
- [x] 3.5 存储单测覆盖「首次/二次/吊销/fail-closed」

## 四、AuthService 接入

- [x] 4.1 `login` 生成 familyId 传入 `buildLoginResponse`
- [x] 4.2 `refresh` 在开关开启时按 §2.4 顺序执行：类型校验 → family 吊销判定 → 登出黑名单 → `markUsed` → 复用则吊销 family + 告警并 401 → 否则旧 token 入黑名单并签发同 family 新令牌
- [x] 4.3 开关关闭时 `refresh` 行为与现状一致（回归）
- [x] 4.4 familyId 缺失的旧 refreshToken 在开启态被拒（401）
- [x] 4.5 单测覆盖「开启：二次使用→401 + family 吊销 / 正常轮换 / 关闭：现状」

## 五、测试与门禁

- [x] 5.1 `mvn -f whale-backend/pom.xml clean verify -T 1C`：BUILD SUCCESS，全模块既有用例不回归
- [x] 5.2 `mvn -f whale-backend/pom.xml checkstyle:check`：全 reactor 0 违规（E16 Javadoc / 行宽 ≤150 / C4 ≤4 参 / 魔法值）
- [x] 5.3 新增单测全部纳入构建（无 `@Disabled` 逃逸）

## 六、留痕与销账

- [x] 6.1 检查报告 `docs/reviews/2026-10-18-wave5.13-auth39-refresh-rotation.md` 逐项对应 [code-review-checklist.md](../../checklists/code-review-checklist.md) 编号
- [x] 6.2 §2.6 偏差（复用检测以告警通道替代 `@AuditLog`）已在报告与 devlog 留痕
- [x] 6.3 devlog 追加本波条目，四节（需求项 / 完成项 / 待做项 / 已修正问题项）齐备
- [x] 6.4 台账销账：`whale-auth-backlog.md`（AUTH-39 删除线 + ✅）、`register.md`（REQ-SEC-004）
- [x] 6.5 变更已 commit 并 push 至 Gitee `origin/main`，工作区干净且 `main == origin/main`

## 七、核验结论

- 核验人：BlueWhale ｜ 日期：2026-10-18 ｜ 结论：通过
- 门禁实测：`clean verify -T 1C` BUILD SUCCESS（28 模块全 SUCCESS，292 用例 0 fail·0 error·0 skipped，其中 auth 143 / security 6）；`checkstyle:check` 全 reactor **0 违规**。
- 规格偏离：① §2.6 复用检测以 `SecurityDegradationAlerter` 告警通道替代 `@AuditLog` 埋点（理由：轮换发生在令牌签发早于审计上下文建立）；② `JwtTokenProvider` 新增 `refreshExpireSeconds` getter（供黑名单 TTL 精确对齐）。
- 证据：检查报告 [2026-10-18-wave5.13-auth39-refresh-rotation.md](../../reviews/2026-10-18-wave5.13-auth39-refresh-rotation.md)；devlog §33、§三 #70/#71。
