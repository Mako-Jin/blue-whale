# ADR-001 · refreshToken「一次一换」轮换策略

| 项 | 内容 |
|---|---|
| 状态 | **已决（采纳，分期实施）** — 本 ADR 仅定稿策略，**不含实现** |
| 关联需求 | REQ-SEC-004（登出令牌失效）、REQ-SEC-002（令牌黑名单）、REQ-DIST-001/002 |
| 关联任务 | **AUTH-03**（本 ADR）→ 实施另立 **AUTH-39** |
| 决策日期 | 2026-10-17 ｜ 作者：BlueWhale |
| 影响面 | whale-auth（签发/刷新/登出）、whale-gateway（黑名单校验）、前端 `whale-frontend/admin`（token 持久化） |
| 数据来源 | `whale-backend/whale-services/whale-auth/src/main/java/com/bluewhale/auth/jwt/JwtTokenProvider.java`、`service/AuthService.java`、`security/RedisTokenBlacklist.java`、[distributed.md](distributed.md) §3.6 |

---

## 1. 背景与问题

现状（Wave 5.2 事实）：

- `/auth/refresh` 用 refreshToken 换取新的 accessToken / refreshToken，两者均带 `jti` 与 `tokenType`；
- 登出时 accessToken **与** refreshToken 一并写入黑名单（`RedisTokenBlacklist`，TTL = 剩余有效期）；
- **但没有「一次性消费」约束**：同一 refreshToken 在其有效期内（当前 7 天）可被**重复使用**。

后果：若 refreshToken 被窃取，攻击者可在用户登出/换新之后，仍持旧 refreshToken 反复换取新 accessToken —— 即存在**重放窗口**，登出双写只能覆盖「用户主动登出」这一条路径，无法覆盖「已换新但未登出」的场景。

---

## 2. 决策

**采纳**方案 B：**refreshToken 一次一换（Rotation）+ 旧 token 立即失效 + 复用检测**，**分两阶段实施**；本轮**只定稿策略，不写实现**。

### 2.1 目标行为

1. 每次 `POST /auth/refresh` **成功**后，**旧 refreshToken 立即加入黑名单**（TTL = 其剩余有效期），同时签发新的 accessToken + refreshToken（新 `jti`，**token family 标识不变**）。
2. 若某 refreshToken 被**二次使用**（复用）→ 判定为泄露：**吊销该 family 下全部有效 refreshToken**，并记 `@AuditLog(type = OTHER, operation = "refresh 复用检测")`。
3. accessToken 保持短时效（当前 7200s）策略不变。

### 2.2 实施要点（阶段一 · 最小改动）

- Redis 键约定（`whale:<域>:<用途>:<标识>`，扩展 `whale-security` 的 `TokenBlacklistKeys`）：
  - `whale:auth:refresh:used:{jti}` — 已消费标记，`SET NX` + TTL = 剩余有效期；
  - `whale:auth:refresh:family:{familyId}` — family 吊销标记。
- `AuthService#refresh` 处理顺序：校验签名/类型 → 查「已消费」键 → **命中**则吊销 family 并审计；**未命中**则写「已消费」键并签发新对。
- 前端须在每次 refresh 后**替换**本地 refreshToken（与 AUTH-32 / AUTH-34 前端对接同批处理）。

### 2.3 备选方案与取舍

| 方案 | 结论 | 理由 |
|---|---|---|
| A. 维持现状（不轮换） | **否决** | refreshToken 有效期长（7 天），重放窗口过大；登出双写仅覆盖「主动登出」路径，不满足 REQ-SEC-004 的延伸安全意图 |
| **B. 一换一 + 旧 token 黑名单 + 复用检测** | **采纳** | OAuth2 标准最佳实践；复用即泄露检测；改动集中在 `AuthService#refresh` 与 Redis 键，风险可控；复用现有黑名单基础设施，无新增表 |
| C. 仅缩短 refreshToken 有效期 | 否决 | 治标不治本：窗口缩小但重放仍可能，且恶化体验（频繁重登） |

---

## 3. 影响与风险

| # | 风险 | 缓解 |
|---|---|---|
| R1 | 前端未同步「替换 refreshToken」→ 换新后仍用旧 token 请求 → 被误判复用而吊销 | 与 AUTH-32/34 同批发布；实施时以 feature flag（`whale.auth.refresh.rotation.enabled`）灰度，可秒级回滚 |
| R2 | Redis 不可用时降级语义 | 现有黑名单**降级放行**；轮换的「已消费」检查建议**保守拒绝**（fail-closed），避免泄露期被放行 —— 与降级放行策略需在实施时显式定夺并留痕 |
| R3 | family 吊销的时序竞争（并发 refresh） | 「已消费」写用 `SET NX` 原子抢占，天然互斥；失败方按复用处理 |

无新增数据表；复用现有 Redis 与黑名单键约定。

---

## 4. 结论与后续

- **决策结论：采纳（方案 B）**；`AUTH-03` 状态从「策略未定（不确定项）」→「**策略已定稿（ADR-001）**」，实现工作另立。
- **新增任务 AUTH-39**「refreshToken 一次性轮换实施（旧 token 黑名单 + 复用检测 + family 吊销 + 前端同步 + 单测）」，依赖 AUTH-32 / AUTH-34（前端 token 持久化改造），建议 Wave 6 排期。
- 本轮**不实现**，避免半实现引发前端不兼容。
