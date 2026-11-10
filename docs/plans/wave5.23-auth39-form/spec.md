# Wave 5.23 规格（AUTH-39 残余收口 + whale-form 最小可用 · 冻结契约）

> 用户指令（2026-10-19）：① 完成 AUTH-39；② 当前服务任务已完成 → 启动 whale-form 服务。
> 经 AskUserQuestion 确认本轮口径：**AUTH-39 = 前端自动续期 + 运行时取证**；**whale-form = 表单定义 CRUD 最小可用**。
> 执行原则：**先落文档（REQ-DOC-002）→ 冻结契约（本文 §三）→ 轨内并行 → 模块级自检 → 收尾串行全量回归（REQ-QA-002）**。
> 关联台账：[whale-auth-backlog.md](../whale-auth-backlog.md)、[roadmap.md](../roadmap.md)、[task-breakdown.md](../task-breakdown.md)、[service-catalog.md](../../service-catalog.md)、[ADR-001](../../design/adr/2026-10-17-refresh-token-rotation.md)。

---

## 一、范围与非目标

| 轨 | 范围 | 落点模块 |
|---|---|---|
| **T1 AUTH-39 残余** | ① admin 前端 `/auth/refresh` 续期链（401 → 刷新 → 重放，单飞合并；**每次刷新替换本地 refreshToken**）；② 轮换运行时端到端取证（换新后复用旧 token → 401 + family 吊销） | `whale-frontend/admin`、`whale-backend/whale-services/whale-auth`、运行时 |
| **T2 whale-form MVP** | 表单定义 CRUD 最小可用：`bluewhale_form` 建库 + `form_definition` 表 + 5 个 REST 接口 + `sys:form:*` 权限点种子 + 单测 + 经网关冒烟 | `whale-backend/whale-services/whale-form`、`whale-auth` db 种子 |

**非目标（明确不做）**
- 表单**数据**（`form_data`）与提交/查询接口、表单渲染/设计器（前端）——本轮仅「定义」侧。
- AUTH-10 MQ 汇聚、AUTH-17 DDD 应用层迁移、AUTH-39 之外的 refresh 策略变更。
- whale-workflow / whale-pay / whale-monitor / whale-alert 等服务（仍留 roadmap）。
- 前端表单设计器 UI 与 admin 表单管理页（本轮仅后端服务）。

---

## 二、现状事实（研究结论，勿重复调研）

- **AUTH-39 后端已实现（Wave 5.13）**：`RefreshRotationProperties`（前缀 `whale.auth.refresh.rotation`，`enabled` **默认 false**）；`RefreshTokenRotationStore` 接口 + `RedisRefreshTokenRotationStore`（键 `whale:auth:refresh:used:{jti}` 已消费、`whale:auth:refresh:family:{familyId}` 整族吊销，键约定下沉 `whale-security` `TokenBlacklistKeys`）；`AuthService#refresh` 顺序为「签名/类型 → family 校验 → 登出黑名单 → `SET NX` 原子消费（失败即复用：整族吊销 + `SecurityDegradationAlerter.alarm` + 401）→ 旧 token 入黑名单 → 同 familyId 换发」；`JwtTokenProvider` 的 `CLAIM_FAMILY_ID` 仅 refreshToken 携带。**残余**：前端无 `/auth/refresh` 调用；轮换开关未在运行时取证。
- **whale-frontend/admin**：`api/http.ts` 对 HTTP 401 直接 `clearAuth()` 并抛 `ApiError`（**无刷新重试**）；`api/auth.ts` 仅 `login`/`fetchMe`/`logout`，**无 refresh**；`api/token.ts` 已有 `getRefreshToken()`/`saveLogin()`/`clearAuth()`。
- **whale-form 裸骨架**：仅 `WhaleFormApplication` + `pom.xml`（已含 web/validation/actuator/nacos/mybatis-plus/mysql/whale-tools）+ `application.yml`（**无 datasource、无 mybatis 配置、无权限头开关**）；`/form/**` 网关路由与 8084 端口**已就绪**；`bluewhale_form` 库**未建**。
- **权限种子落库位置**：`sys_permission` 表在 `bluewhale_auth`（whale-auth `db/init.sql`），当前种子 id 至 **35**（file 4 条：upload/download/list/delete）。
- **参考实现模板**：whale-file 的 `FileInfo`(继承 `BaseEntity`) / `FileInfoMapper`(仅继承 `BaseMapper`，靠 `com.bluewhale.**.mapper` 扫描) / `FileInfoService(+Impl)` / `FileController`（`@RequirePermission` + `com.bluewhale.auth.sdk.annotation.RequirePermission`）/ `db/init.sql`。

---

## 三、冻结契约（实现前不得更改）

### T1 · 前端续期链

- 刷新接口：`POST /auth/refresh`，请求体 `{ refreshToken }`，响应与 `login` 同构（`LoginResponse`）。
- 刷新**成功**必须落盘**新的** accessToken **与** refreshToken（复用 `saveLogin`），实现「一次一换」的前端同步。
- 401 处理：业务请求首次 401 → 触发**单飞**刷新（并发 401 合并为一次刷新）→ 成功则**重放原请求一次**；刷新失败 / 重放再 401 / 无 refreshToken → `clearAuth()` + 抛 401（由路由守卫跳登录）。`/auth/refresh` 与 `/auth/login` 自身 401 **不触发**递归刷新。
- 轮换开启时后端行为（供取证）：旧 refreshToken 换新即被消费；再次使用同一旧 token → **HTTP 401 + 业务码 401** 文案 `refreshToken 复用检测命中，请重新登录`；该 family 全部 refreshToken 一并吊销。

### T2 · whale-form 接口与数据

- 服务内路径前缀 `/form`（服务自持一级前缀，网关只转发不剥离）。
- 5 个接口与权限码一一对应：

  | 方法 | 路径 | 权限码 | 说明 |
  |---|---|---|---|
  | POST | `/form/definition` | `sys:form:add` | 新增表单定义 |
  | PUT | `/form/definition/{id}` | `sys:form:edit` | 更新表单定义 |
  | DELETE | `/form/definition/{id}` | `sys:form:delete` | 逻辑删除 |
  | GET | `/form/definition/{id}` | `sys:form:list` | 详情 |
  | GET | `/form/definition/page` | `sys:form:list` | 分页查询 |

- 统一响应体 `Result<T>` / `PageResult<T>`；列表支持 `name` 模糊、`code` 等值、`status` 等值过滤，按 `create_time desc`。
- 表 `form_definition`（库 `bluewhale_form`，`utf8mb4_general_ci`，InnoDB）列：
  `id / code / name / schema_json / version / status / remark / create_time / update_time / deleted`；
  `code` 建普通索引 `idx_code`（**不用唯一索引**，避免与逻辑删除冲突），唯一性由服务层「先查重」保证。
- `schema_json` 为表单模板 JSON 串，类型 `MEDIUMTEXT`，长度上限 64KB（超限抛 `PARAM_ERROR`）。
- 新增权限种子 id **36–39**：`sys:form:add(36) / sys:form:edit(37) / sys:form:delete(38) / sys:form:list(39)`（写入 whale-auth `db/init.sql` 并落 `bluewhale_auth.sys_permission`）。

---

## 四、完成定义（DoD）

- `whale-form` 模块 `test` + `checkstyle:check` 全绿（0 违规）；前端 `pnpm --filter whale-admin build`（tsc -b && vite build）通过。
- 全量 `mvn clean install -T 1C` BUILD SUCCESS。
- 运行时段证据：
  - whale-form 8084 经网关 `/form/**` 完成 新增→分页→详情→更新→删除 冒烟，`code=0`；无权限/无 token 相应 401/403。
  - AUTH-39：`whale.auth.refresh.rotation.enabled=true` 下，登录 → refresh 换新 → 复用旧 refreshToken → 401 且 family 键落 Redis。
- `docs/reviews/2026-10-19-wave5.23-auth39-form.md` + devlog 追加 + 台账（whale-auth-backlog / roadmap / task-breakdown）勾选 + commit & push origin（Gitee）。
