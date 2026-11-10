# Wave 5.13 · AUTH-39 refreshToken 一次性轮换实施

| 项 | 内容 |
|---|---|
| 关联需求 | REQ-SEC-004（refreshToken 一并失效）、REQ-SEC-002（令牌黑名单）、REQ-DIST-001/002、REQ-QA-002、REQ-DOC-002 |
| 任务映射 | **AUTH-39**（↔ AUTH-03 定稿的 [ADR-001](../../design/adr/2026-10-17-refresh-token-rotation.md) 实施） |
| 生成日期 | 2026-10-18 ｜ 作者：BlueWhale |
| 依据 | [ADR-001](../../design/adr/2026-10-17-refresh-token-rotation.md) §2.1/§2.2/§3；[whale-auth-backlog.md](../../plans/whale-auth-backlog.md) AUTH-39；[distributed.md](../../design/distributed.md) §3.6 |
| 执行原则 | 文档先行（REQ-DOC-002）→ 编码 → 按 [code-review-checklist.md](../../checklists/code-review-checklist.md) 自检留报告（REQ-QA-002）→ 构建门禁 → 提交推送 |

> **本波只实施 ADR-001 阶段一（后端最小改动）**：旧 refreshToken 用后即失效 + 复用检测 + family 吊销 + feature flag + 单测。
> **前端 token 持久化替换（AUTH-32 / AUTH-34）不在本波范围**；因之轮换默认**关闭**（`whale.auth.refresh.rotation.enabled=false`），避免旧客户端被误判复用。

---

## 一、需求项

| 需求 | 本波次覆盖点 | 验收口径 |
|---|---|---|
| REQ-SEC-004（延伸） | 每次 `POST /auth/refresh` 成功后**旧 refreshToken 立即失效**（写「已消费」标记，TTL = 剩余有效期）；签发新一对令牌（新 `jti`，**family 标识不变**） | 同一 refreshToken 二次使用被拒 |
| REQ-SEC-004（延伸） | **复用检测**：二次使用某 refreshToken → 判定泄露 → **吊销该 family 全部有效 refreshToken** | 复用后同 family 的其它 refreshToken 亦不可再换发 |
| REQ-SEC-002 | 复用现有 Redis 基础设施与黑名单键约定（`whale-security` `TokenBlacklistKeys` 扩展） | 无新增数据表；键遵守 `whale:<域>:<用途>:<标识>` |
| REQ-QA-002 | feature flag 灰度 + 单测 + 检查报告 | 开关可秒级回滚；报告逐项对应清单编号 |

---

## 二、设计

### 2.1 Redis 键约定（扩展 `whale-security` `TokenBlacklistKeys`）

| 键 | 用途 | 语义 |
|---|---|---|
| `whale:auth:refresh:used:{jti}` | 已消费标记 | `SET NX` 抢占，TTL = 该 refreshToken 剩余有效期 |
| `whale:auth:refresh:family:{familyId}` | family 吊销标记 | 存在即视为该 family 全部 refreshToken 已吊销，TTL = 最长 refresh 有效期 |

> 既有登出黑名单键 `auth:token:blacklist:<sha256(token)>` **不动**（网关侧共用，改键会破坏跨服务一致性）。

### 2.2 JWT claims

- `JwtTokenProvider` 新增常量 `CLAIM_FAMILY_ID = "familyId"`。
- `generateRefreshToken(userId, username, familyId)`：refreshToken 增携 `familyId`（登录时生成、轮换时原样沿用）；accessToken **不带** family（短时效，不在本策略范围）。

### 2.3 轮换存储（whale-auth 新增）

- `RefreshTokenRotationStore`（接口）：
  - `boolean markUsed(String jti, long ttlSeconds)` —— `SET NX`：`true` = 首次消费；`false` = 已被消费（复用）；
  - `boolean isFamilyRevoked(String familyId)`；
  - `void revokeFamily(String familyId, long ttlSeconds)`。
- `RedisRefreshTokenRotationStore`（实现）：
  - 复用 `StringRedisTemplate`；
  - **fail-closed**（ADR R2）：Redis 异常时 `markUsed` 返回 `false`、`isFamilyRevoked` 返回 `true`，并 `warn` + `SecurityDegradationAlerter.alarm(...)`（可用性让位于安全，仅影响 refresh 路径）。

### 2.4 `AuthService#refresh` 处理顺序（`enabled=true` 时）

```
解析 refreshToken → 校验 tokenType=refresh
  → familyId = claims.familyId；缺失 → 401（旧令牌，需重登）
  → isFamilyRevoked(familyId) → 命中 401
  → 登出黑名单命中 → 401（既有语义）
  → markUsed(jti, 剩余TTL)
        ├─ false（已消费）→ revokeFamily(familyId, 最长TTL) + 告警 → 401「复用检测」
        └─ true            → 旧 refreshToken 写入登出黑名单（复用 TokenBlacklist.add）
                             → 签发新 access/refresh（同 familyId，新 jti）
```

- 开关关闭（默认）时：**保持既有行为**（仅登出黑名单校验，不消费、不轮换），令牌仍带 `familyId` 但不参与校验。

### 2.5 配置项

| 配置 | 默认 | 说明 |
|---|---|---|
| `whale.auth.refresh.rotation.enabled` | `false` | 是否启用一次性轮换；灰度开关，可秒级回滚（ADR R1） |

### 2.6 复用检测的留痕（偏差登记）

ADR §2.1 期望复用检测记 `@AuditLog(type=OTHER, operation="refresh 复用检测")`。但 `@AuditLog` 由 AOP 织入 **Spring 代理的公有方法**，复用分支位于 `AuthService` 内部私有路径，注解无法条件化命中。本波实现为：**ERROR 级结构化日志 + `SecurityDegradationAlerter.alarm("auth:refresh:reuse", …)`**（复用既有安全降级告警通道），偏差登记于检查报告与 devlog。

---

## 三、组件与落点

| 模块 | 类/文件 | 动作 |
|---|---|---|
| `whale-security` | `com/bluewhale/security/token/TokenBlacklistKeys.java` | 新增 `refreshUsedKey(jti)` / `refreshFamilyKey(familyId)` |
| `whale-auth` | `com/bluewhale/auth/jwt/JwtTokenProvider.java` | 新增 `CLAIM_FAMILY_ID`；`generateRefreshToken` 增 `familyId` 参数 |
| `whale-auth` | `com/bluewhale/auth/security/RefreshTokenRotationStore.java`（新增） | 轮换存储接口 |
| `whale-auth` | `com/bluewhale/auth/security/RedisRefreshTokenRotationStore.java`（新增） | Redis 实现（fail-closed） |
| `whale-auth` | `com/bluewhale/auth/config/RefreshRotationProperties.java`（新增） | `whale.auth.refresh.rotation` 绑定 |
| `whale-auth` | `com/bluewhale/auth/service/AuthService.java` | `login` 生成 familyId；`refresh` 接入轮换；`buildLoginResponse` 增 familyId 参数 |
| `whale-auth` | `.../jwt/JwtTokenProviderTest.java`、`.../security/RedisRefreshTokenRotationStoreTest.java`（新增）、`.../service/AuthServiceTest.java` | 单测 |

---

## 四、验收

| 项 | 验证方式 | 通过标准 |
|---|---|---|
| 键约定 | `TokenBlacklistKeysTest` | 两新键前缀/格式正确 |
| 轮换存储 | `RedisRefreshTokenRotationStoreTest` | `markUsed` 首次 true / 二次 false；family 吊销可查；Redis 异常 fail-closed |
| 刷新轮换 | `AuthServiceTest` | 开启后：旧 token 二次使用 → 401 且 family 被吊销；正常刷新旧 token 入黑名单；关闭时行为与现状一致 |
| 门禁 | `mvn -B -f whale-backend/pom.xml clean verify -T 1C`、`checkstyle:check` | BUILD SUCCESS；全 reactor 0 违规 |

---

## 五、风险与残余

| 风险 | 影响 | 处置 |
|---|---|---|
| R1 前端未同步替换 refreshToken | 开启后被误判复用而吊销 | **默认关闭**；与 AUTH-32/34 同批发布后再开启；开关可回滚 |
| R2 Redis 不可用 | 降级语义 | refresh 路径 **fail-closed**（保守拒绝），与登出黑名单「降级放行」显式区分并留痕 |
| R3 并发 refresh 时序竞争 | 双写 | 「已消费」用 `SET NX` 原子抢占；失败方按复用处理 |
| 残余 | — | 前端 token 持久化同步（AUTH-32/34）、family 级别完整审计事件（用告警通道替代，见 §2.6） |

---

## 六、执行顺序

```
1) 文档先行：本 spec + tasks + checklist
2) TokenBlacklistKeys 扩展 + JwtTokenProvider familyId
3) RefreshTokenRotationStore / Redis 实现 + RefreshRotationProperties
4) AuthService 接入（login/refresh/buildLoginResponse）
5) 单测（键/存储/刷新）+ mvn clean verify + checkstyle:check
6) 检查报告 + devlog + 台账（backlog AUTH-39 / register）→ commit + push
```
