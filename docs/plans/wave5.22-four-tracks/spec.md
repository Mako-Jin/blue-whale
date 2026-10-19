# Wave 5.22 规格（四轨并行 · 冻结契约）

> 用户指令（2026-10-19）：在 Wave 5.21 交付后，经 AskUserQuestion（四项全选）确定本轮范围：
> ① 网关 backlog 收口 ② admin 动态路由 ③ whale-job 归档三件套收口（whale-file 对象存储 + 归档运行时取证） ④ whale-auth backlog 收口。
> 执行原则：**先落文档（REQ-DOC-002）→ 冻结契约（本文 §三）→ 轨内并行 → 模块级自检 → 收尾串行全量回归（REQ-QA-002）**。
> 关联台账：[whale-gateway-backlog.md](../whale-gateway-backlog.md)、[whale-auth-backlog.md](../whale-auth-backlog.md)、[roadmap.md](../roadmap.md)、[wave5.21-four-tracks/spec.md](../wave5.21-four-tracks/spec.md)。

---

## 一、范围与非目标

| 轨 | 范围 | 落点模块 |
|---|---|---|
| **A 网关 backlog** | GW-04 熔断 Micrometer 指标 / GW-05 多路由纳入熔断 / GW-06 内网屏蔽覆盖评估与配置化 / GW-07 测试基线 | `backend/whale-services/whale-gateway` |
| **B admin 动态路由** | 用 `GET /auth/me` 的 `menus[]` 驱动侧栏导航与路由；静态兜底保留 | `frontend/admin` |
| **C 归档三件套** | C1 whale-file 对象存储抽象扩展（`local`/`minio` 可插拔）；C2 归档链路运行时端到端取证（job 导出→file→逻辑删；audit 归档导出不删） | `whale-file`、`whale-job`、`whale-audit` |
| **D auth backlog** | D1 控制器层单测补齐；D2 DDD feature flag 运行时灰度验证；D3 AUTH-10 阻塞登记（不实现） | `backend/whale-services/whale-auth` |

**非目标（明确不做）**
- GW-01 Sentinel 接入、GW-02 resilience4j 替换（仍留 backlog；现自研熔断满足需求）。
- AUTH-10 MQ 汇聚（本机无 RabbitMQ，阻塞，仅登记）。
- 真实对象存储服务器端到端（本机无 MinIO/S3 实例，`minio` 实现仅单测级验证，运行时以 `local` 兜底）。
- 消息 CRUD、portal、Phase 4/5 等 roadmap 既有未勾选项。

---

## 二、现状事实（研究结论，勿重复调研）

- **whale-gateway**：`CircuitBreakerGlobalFilter`（order=-75，按路由隔离 `breakers` 映射，`tryAcquire`/`record` 均 `synchronized`，503 用 `GatewayResponse.of(503,msg).toJson()`）；`CircuitBreakerProperties`（enabled/timeoutMillis=3000/failureRateThreshold=50/slidingWindowSize=20/waitDurationInOpenStateMillis=10000/`applyToRoutes: List<String>`）；`InternalApiDenyProperties`（enabled + `patterns: List<String>` 默认 `/file/internal/**`，**已配置化**）；pom 已含 `spring-boot-starter-actuator` → **micrometer-core 已在依赖树**，`MeterRegistry` 可注入。
- **frontend/admin**：`App.tsx` 静态 `useRoutes`（`/` 下 users/roles/menus/components）；`PermissionGate` 仅注入 `me.permissions`；`AdminLayout` 侧栏为硬编码 `NAV_ITEMS`（只有 key/label）；`api/token.ts` 的 `AuthMeResponse.menus` 目前是 `unknown[]` 未建模；`api/menu.ts` 已有 `MenuVO`（id/parentId/name/path/component/icon/sort/menuType/permissionCode/visible/status/children）。
- **whale-file**：`FileStorage` SPI（`store/load/delete(bucket,objectKey,...)`），`LocalFileStorage` 按 `${root}/${bucket}/${objectKey}` 落盘并做目录穿越防护；`FileProperties`（root `D:/data/whale-file`、默认桶 `bluewhale`、max-size 52428800）；objectKey = `yyyy/MM/dd/<uuid>.<ext>`；上传端点 `POST /file/upload` 与内网 `POST /file/internal/upload`。
- **whale-job**：`JobLogArchiveHandler`（筛选过期 job_log → CSV → `JobArchiveClient.upload` → **成功后**逻辑删，失败不删）；`JobArchiveClient` 用 `RestClient` POST `/file/internal/upload`（表单 `file`+`bucket`，默认桶 `bluewhale`）；`JobProperties`（logRetentionDays/fileServiceUrl/archiveBatchSize）；种子任务 id=2。
- **whale-audit**：`AuditLogArchiver`（`whale.audit.retention.archive-enabled=true` 才导出，**绝不删除**）；`LogRetentionScheduler`（`@ConditionalOnProperty(whale.audit.retention.enabled)`）。
- **whale-auth**：6 控制器（Auth/User/Role/Menu/Permission/Dept），**控制器层无任何测试**；`DddMigrationProperties`（`whale.auth.ddd.role/menu/permission/dept`，默认全 false）；4 个 ServiceImpl 已有 flag 双路径（Role: page/loadRoleById/listEnabledRoleCodesByIds；Menu: loadAllMenus/loadEnabledVisibleMenus/loadMenuById；Permission: page/loadEnabledPermissionCodes/loadPermissionById；Dept: loadAllDepts/loadDeptById）；`WhaleAuthApplication` 有 `@ConfigurationPropertiesScan`；pom **无** MQ 依赖。
- **依赖可用性**：Micrometer-core 本地已有；`io.minio:minio:8.5.12` 经 `mvn dependency:get` 实测**可解析**（本地仓库已落 jar）。

---

## 三、冻结契约（各轨实现前不得更改）

### 轨 A · 网关

- **指标命名**（`MeterRegistry` 可选注入 `ObjectProvider<MeterRegistry>`，缺省时静默不注册）：
  - `gateway.circuit.calls` — Counter，tag `route` + `result=success|failure`
  - `gateway.circuit.short_circuits` — Counter，tag `route`
  - `gateway.circuit.state` — Gauge，tag `route`，值 `0=CLOSED / 1=OPEN / 2=HALF_OPEN`
- **配置**：`whale.gateway.circuit-breaker.apply-to-routes` 扩为 `[whale-auth, whale-file, whale-job, whale-audit]`；`whale.gateway.internal-deny.patterns` 保持列表语义（默认 `/file/internal/**`，可配置追加）。
- 不改动 429/503 响应契约（`Result` 体）。

### 轨 B · admin 动态路由

- `menus[]` 元素类型（与后端 `MenuVO` 对齐）：
  ```ts
  interface MenuNode { id: string; parentId?: string; name: string; path?: string; component?: string;
    icon?: string; sort?: number; menuType?: string; permissionCode?: string; visible?: boolean;
    status?: number; children?: MenuNode[] }
  ```
- `component` 名 → 页面组件映射：`user→UserList`、`role→RoleList`、`menu→MenuList`、`components→ComponentsDemo`；未匹配 → 占位 404 页（不崩）。
- `menus` 为空或请求失败 → 回退现有静态导航（行为不退化）。
- 侧栏层级与顺序：按 `parentId` 组树 + `sort` 升序 + `visible !== false` 过滤。

### 轨 C · 归档三件套

- `whale.file.storage.type` = `local`（默认）| `minio`；`minio` 时装配 `MinioFileStorage`，配置树 `whale.file.minio.{endpoint,access-key,secret-key,bucket,region}`。
- `FileStorage` SPI 签名**不变**（`store`/`load`/`delete` 按 bucket+objectKey）。
- 归档语义冻结：job「上传成功才逻辑删，失败不删且返回未清理条数」；audit「只导出不删除」。

### 轨 D · auth

- 控制器测试用 `@WebMvcTest` + `@MockitoBean`（Spring Boot 4 用 `org.springframework.test.context.bean.override.mockito.MockitoBean`）隔离 Service；安全链在测试中放行（`@AutoConfigureMockMvc(addFilters=false)` 或测试专用 SecurityFilterChain）。
- DDD 灰度验证以「同一请求在 flag=false/true 下返回等价」为等价口径。

---

## 四、完成定义（DoD）

- 各轨模块 `test` + `checkstyle:check` 全绿（0 违规）。
- 全量 `mvn clean install -T 1C` BUILD SUCCESS；前端 `pnpm --filter whale-admin build`（tsc -b && vite build）通过。
- 运行时段证据：网关多路由熔断 503、job 归档 e2e（CSV 落 file_info + job_log 逻辑删）、audit 归档导出且不删、auth DDD flag 灰度等价、admin 动态路由浏览器 e2e。
- `docs/reviews/2026-10-19-wave5.22-four-tracks.md` + devlog 追加 + 台账（roadmap / whale-gateway-backlog / whale-auth-backlog / task-breakdown）勾选 + commit & push origin（Gitee）。
