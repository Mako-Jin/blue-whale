# Tasks：Wave 5.13 AUTH-39 refreshToken 一次性轮换实施

> 关联 spec：[spec.md](./spec.md) ｜ 关联 ADR：[ADR-001](../../design/adr/2026-10-17-refresh-token-rotation.md)
> 执行顺序即下表顺序；每项完成后按 REQ-QA-002 留痕，未通过构建不得提交。

| # | 任务 | 依赖 | 优先级 | 验收 | 状态 |
|---|---|---|---|---|---|
| [ ] A1 | `whale-security` `TokenBlacklistKeys` 新增 `refreshUsedKey(jti)`（`whale:auth:refresh:used:`）与 `refreshFamilyKey(familyId)`（`whale:auth:refresh:family:`）+ 键约定单测 | 无 | 高 | 键前缀/格式正确 | ⏳ |
| [ ] A2 | `JwtTokenProvider` 新增 `CLAIM_FAMILY_ID`；`generateRefreshToken(userId, username, familyId)` 增携 familyId；更新调用方与单测 | A1 | 高 | refreshToken 含 familyId claim；access 不含 | ⏳ |
| [ ] A3 | 新增 `RefreshTokenRotationStore` 接口 + `RedisRefreshTokenRotationStore`（`SET NX` + fail-closed）+ `RefreshRotationProperties`（`whale.auth.refresh.rotation.enabled` 默认 false）+ 单测 | A1 | 高 | 首次 true/二次 false；family 吊销可查；异常 fail-closed | ⏳ |
| [ ] A4 | `AuthService` 接入：`login` 生成 familyId、`buildLoginResponse(loginUser, familyId)`、`refresh` 按 §2.4 顺序实现（开关门控）+ 单测 | A2、A3 | 高 | 开启后二次使用 → 401 且 family 吊销；关闭时行为不变 | ⏳ |
| [ ] A5 | 构建与门禁：`mvn -B -f backend/pom.xml clean verify -T 1C` + `checkstyle:check` | A1–A4 | 高 | BUILD SUCCESS；全 reactor 0 违规 | ⏳ |
| [ ] A6 | 留痕收口：检查报告 `docs/reviews/2026-10-18-wave5.13-auth39-refresh-rotation.md` + devlog 追加 + 台账销账（`whale-auth-backlog.md` AUTH-39 / `register.md` REQ-SEC-004） | A5 | 中 | 四类信息齐备；§2.6 偏差留痕 | ⏳ |
| [ ] A7 | 自动 commit + push（Gitee origin/main） | A6 | 中 | 工作区干净且 `main == origin/main` | ⏳ |

## 实施注意（给实现者的事实约束）

- **默认关闭**：`whale.auth.refresh.rotation.enabled` 默认 `false`；关闭时 `refresh` 行为必须与现状**逐字节一致**（仅登出黑名单校验）。**不得**在关闭态改变任何返回值/异常文案。
- **键不迁移**：既有登出黑名单前缀 `auth:token:blacklist:` **保持不动**（网关共用）；新键 `whale:auth:refresh:*` 只用于轮换状态。
- **fail-closed 仅限 refresh 路径**：`RedisRefreshTokenRotationStore` 在 Redis 异常时 `markUsed→false`、`isFamilyRevoked→true`，并 `warn` + `SecurityDegradationAlerter.alarm(...)`。**不得**改动 `RedisTokenBlacklist` 既有「降级放行」语义。
- **familyId 缺失即拒**：开启轮换后，refreshToken 无 `familyId`（旧令牌）→ 401，引导重登。
- **C4 方法参数 ≤ 4**：`AuthService#refresh` 已 1 参；`buildLoginResponse` 增 `familyId` 后为 2 参；勿再扩张。
- **D1–D5 魔法值常量化**：场景串、键前缀、日志文案一律 `static final` 或 `TokenBlacklistKeys` 常量。
- **E16 中文 Javadoc**：新增类（含私有方法）与改动方法须有中文 Javadoc + `@author`。
- **不改 gateway**：网关只校验登出黑名单（`TokenBlacklistChecker`），轮换 family 语义不在网关判定（access 短时效；refresh 不经网关换发）。

## 待做项（本波明确不做，登记备查）

- **前端 token 持久化替换（AUTH-32 / AUTH-34）**：轮换默认关闭即为此；前端就绪后另行开启。
- **family 级完整审计事件**：复用检测以 ERROR 日志 + 告警通道留痕（§2.6 偏差）。
- **accessToken family 吊销**：ADR 未纳入（access 短时效）。
