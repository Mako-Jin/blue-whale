# Wave 5.46 · Phase 4 业务应用填充规划（erp / mes / pdm / oa）

> 关联需求：REQ-FUNC-006 延伸、roadmap Phase 4
> 制定日期：2026-10-21 ｜ 状态：**规划已定稿，待启动**
> 执行原则：**先补底座与契约 → 四应用并行 → 网关/权限/前端联调收尾**；每个子任务独立可验证。
> 上游依据：[roadmap.md](roadmap.md) Phase 4、[service-catalog.md](../service-catalog.md)、[wave5.45 devlog §十七](../devlog/2026-10-21.md)

---

## 一、背景与现状（2026-10-21 审计实测）

| 维度 | 现状 | 证据 |
|---|---|---|
| 后端四应用 | 仅 `*Application.java` + pom，**无任何 Controller/Service/Entity/Mapper**；pom 未引 `whale-spring-boot-starter`、MyBatis-Plus、MySQL 驱动 | `whale-backend/whale-apps/whale-{erp,mes,oa,pdm}` |
| 数据源 | 四应用 `application.yml` **无 datasource**，库 `bluewhale_{erp,mes,oa,pdm}` **未建** | 全库 `url: jdbc:mysql` 扫描仅命中 9 个服务 |
| 网关路由 | 缺 `/erp/**`、`/mes/**`、`/pdm/**`、`/oa/**` 四条（`/config` 非本波范围） | [gateway application.yml](../../whale-backend/whale-services/whale-gateway/src/main/resources/application.yml) |
| 权限种子 | 无 `sys:erp:*` / `sys:mes:*` / `sys:pdm:*` / `sys:oa:*` 权限点 | `whale-auth/src/main/resources/db/init.sql` |
| 前端四子应用 | 列表页/表单页骨架已就绪，但 `api/{erp,oa,mes,pdm}.ts` 为**占位 mock + TODO**；契约已冻结为 `ErpItem` / `OaDocument` / `MesWorkOrder` / `PdmProduct`（`id: string`、`status: ENABLED\|DISABLED`、`code/name/remark`） | `whale-frontend/platform-apps/whale-{erp,oa,mes,pdm}/src/api/*.ts` |
| 基座登录 | `whale-platform` 的 `api/auth.ts` 仍是**本地 mock（admin/123456 + 假 JWT）**，未接真实 `/auth/login`；基座 `vite.config.ts` **无 proxy** | `whale-frontend/platform-apps/whale-platform` |
| 基座占位页 | `system/users`、`system/roles`、`system/menus`、`i18n-demo` 指向 `PlaceholderPage` | `src/router/routes.tsx` |
| 门户占位 | Dashboard 三项统计、通知中心、待办跳审批均硬编码 | `whale-portal/src/pages/{Dashboard,Notice,Todo}.tsx` |

---

## 二、目标与范围

### In Scope

1. **四应用最小可用闭环**（每应用 1 条业务主线）：分页查询 → 新增 → 编辑 → 启停/删除 → 前端联调。
2. **网关 4 条路由 + 权限点种子**，经网关 8080 全链路可达。
3. **前端基座真实登录**（替换 mock）+ 基座 proxy 补齐，使 qiankun 子应用经基座同源可调后端。
4. **基座 4 个 PlaceholderPage 收口**（用户/角色/菜单/国际化示例改为真实页或重定向 admin）。
5. **门户 3 处占位收口**（工作台统计接真实接口、通知中心接 `/alert/record` 或站内消息、待办跳转工作流设计器）。

### Out of Scope（明确不做）

- 行业深度逻辑：MRP 运算、排产算法、BOM 展开、工艺路线、图纸版本比对。
- 多租户/数据权限（沿用现有 `X-Tenant-Id` 约定，但不在本波做业务隔离）。
- 生产部署、Docker、CI 实机验证（仍受环境限制，见 roadmap Phase 1/5）。
- `whale-config` 管理面（`/config` 路由不在本波）。

---

## 三、业务主线与契约（每应用一条主线）

| 应用 | 端口 | 库 | 核心表 | 对外路径 | 权限码前缀 | 前端契约 |
|---|---|---|---|---|---|---|
| whale-erp | 9001 | `bluewhale_erp` | `erp_item`（物料/商品） | `/erp/item` | `sys:erp:item:*` | `ErpItem` |
| whale-mes | 9002 | `bluewhale_mes` | `mes_work_order`（生产工单） | `/mes/work-order` | `sys:mes:workorder:*` | `MesWorkOrder` |
| whale-pdm | 9003 | `bluewhale_pdm` | `pdm_product`（产品/图纸） | `/pdm/product` | `sys:pdm:product:*` | `PdmProduct` |
| whale-oa | 9004 | `bluewhale_oa` | `oa_document`（公文/流程单据） | `/oa/document` | `sys:oa:document:*` | `OaDocument` |

**统一字段基线**（与前端契约逐字对齐）：

```
id BIGINT PK（雪花，Jackson 序列化为 string）
code VARCHAR(64) UNIQUE NOT NULL
name VARCHAR(128) NOT NULL
status VARCHAR(16) NOT NULL DEFAULT 'ENABLED'   -- ENABLED | DISABLED
remark VARCHAR(512) NULL
tenant_id BIGINT NOT NULL DEFAULT 1
create_time / update_time DATETIME
deleted TINYINT DEFAULT 0
```

**统一接口形态**（四应用同构，降低前端复制成本）：

| 方法 | 路径 | 说明 | 权限点 |
|---|---|---|---|
| GET | `/{prefix}/{entity}/page?pageNum&pageSize&code&name&status` | 分页 | `sys:<app>:<entity>:list` |
| GET | `/{prefix}/{entity}/{id}` | 详情 | `sys:<app>:<entity>:list` |
| POST | `/{prefix}/{entity}` | 新增（code 唯一，重复 400） | `sys:<app>:<entity>:add` |
| PUT | `/{prefix}/{entity}/{id}` | 编辑 | `sys:<app>:<entity>:edit` |
| PUT | `/{prefix}/{entity}/{id}/status` | 启停 | `sys:<app>:<entity>:edit` |
| DELETE | `/{prefix}/{entity}/{id}` | 逻辑删除 | `sys:<app>:<entity>:delete` |

> 写操作一律加 `@AuditLog`；返回统一 `Result<T>` / `PageResult<T>`；`size` 上限 200。

---

## 四、任务拆分（可并行执行）

### 轨 A：公共底座（先行，串行 1 项）

- **A1 后端模板统一**：四个 pom 补 `whale-spring-boot-starter`、`mybatis-plus-spring-boot4-starter`、`mysql-connector-j`；`application.yml` 补 `spring.datasource`（`bluewhale_<app>`，`MYSQL_HOST/PORT/USER/PASSWORD` 环境变量）+ MyBatis-Plus 逻辑删除/自动填充 + `whale.permission.header.enabled=true`。
- **A2 前端 HTTP 统一**：四子应用 `api/http.ts` 增加 `Authorization: Bearer` 注入 + 401 清态 + 统一错误提示；API 基址沿用相对路径（基座 proxy 统一转发）。
- **验收**：`mvn -pl whale-backend/whale-apps/whale-erp -am compile` 通过；`pnpm --filter whale-erp build` 通过。

### 轨 B：erp（与 C/D/E 并行）

- **B1** `erp_item` DDL + `db/init.sql`（含 `idx_code`、`idx_tenant`）。
- **B2** Entity / Mapper / Service / Controller（6 端点）。
- **B3** 权限点种子（auth `db/init.sql` 追加 4 条，id 段 80+）。
- **B4** 前端 `api/erp.ts` 由 mock 改为真实接口 + 列表分页 + 表单保存/启停/删除。
- **验收**：建库 → 启动 9001 → 经网关 `/erp/item` 6 端点冒烟 `code=0`（重复 code 400、删后 404）。

### 轨 C：mes（与 B/D/E 并行）

- **C1** `mes_work_order` DDL；**C2** 六层代码；**C3** 权限种子；**C4** 前端 `api/mes.ts` 接真实接口 + 页面补分页/启停/删除。
- **验收**：同 B，端口 9002，路径 `/mes/work-order`。

### 轨 D：pdm（与 B/C/E 并行）

- **D1** `pdm_product` DDL；**D2** 六层代码；**D3** 权限种子；**D4** 前端 `api/pdm.ts` 接真实接口。
- **验收**：同 B，端口 9003，路径 `/pdm/product`。

### 轨 E：oa（与 B/C/D 并行）

- **E1** `oa_document` DDL；**E2** 六层代码；**E3** 权限种子；**E4** 前端 `api/oa.ts` 接真实接口。
- **验收**：同 B，端口 9004，路径 `/oa/document`。

### 轨 F：网关与权限（依赖 B–E 的路径定稿）

- **F1** 网关 `application.yml` 追加 4 条路由（`/erp`、`/mes`、`/pdm`、`/oa`），**不配 StripPrefix**（REQ-ARCH-003）。
- **F2** 权限种子与菜单：`sys_menu` 增加四应用入口（可选，供基座侧栏）。
- **F3** 经网关冒烟：四路径 page/create/update/status/delete 全绿，无 token → 401，缺权限码 → 403。
- **验收**：`C-03` 剩余 4 条路由结案；service-catalog 状态同步。

### 轨 G：基座与门户收口（可与 A–F 并行）

- **G1 基座真实登录**：`whale-platform/src/api/auth.ts` 改接 `POST /auth/login`、`GET /auth/me`、`POST /auth/logout`；token 存 prefix `whale_platform_`；登录后拉取权限码供子应用共享。
- **G2 基座 proxy**：`vite.config.ts` 增加 `/auth|/user|/dept|/role|/menu|/permission|/erp|/mes|/pdm|/oa|/workflow|/form|/file|/i18n|/alert|/monitor|/audit` → `http://127.0.0.1:8080`。
- **G3 基座占位页**：`system/users`、`system/roles`、`system/menus` 改为**重定向 admin**（8001）或内嵌真实页；`i18n-demo` 接 `/i18n/message` 真实列表。
- **G4 门户 Dashboard**：进行中流程数 ← `GET /workflow/instance/page?status=RUNNING`；未读通知数 ← 通知接口；系统服务数 ← `GET /monitor/service/list`。
- **G5 门户通知中心**：接真实数据源（优先 `/alert/record/page`，无则站内消息接口）；`Todo.tsx` 审批跳转改为打开 `whale-workflow-designer`（8003）对应待办。
- **验收**：浏览器实机：基座登录 → 工作台统计非 0 → 待办点击跳转可达 → 三占位路由不再显示 PlaceholderPage。

### 轨 H：回归与收尾

- **H1** `mvn -o -f whale-backend/pom.xml clean install -T 1C`（JDK21，32 模块）全绿 + Checkstyle 0 违规。
- **H2** `pnpm build` 全量（14+ 包）通过。
- **H3** 运行时冒烟脚本 `wave5.46-phase4.ps1`：四应用 6 端点 + 网关路由 + 权限/鉴权负向。
- **H4** 文档：roadmap 勾选、devlog、检查报告、service-catalog 同步。

---

## 五、依赖与执行顺序

```
A1/A2（底座）
  ├─► B(erp) ─┐
  ├─► C(mes) ─┼─► F(网关/权限) ─► H(回归)
  ├─► D(pdm) ─┤
  └─► E(oa)  ─┘
G(基座/门户) 与 A–F 并行，G4/G5 依赖 F 完成后联调
```

- **每个应用轨可独立并行**（互不共享表/代码，权限码前缀隔离）。
- F 依赖 B–E 的路径与权限码定稿；G4/G5 依赖 F 的路由生效。
- 建议先做 **A → B（样板）**，B 通过后 C/D/E 复制同构实现，避免四路同时踩同一坑。

---

## 六、验收总口径

| # | 验收项 | 口径 |
|---|---|---|
| 1 | 构建 | 后端 32 模块 `clean install` SUCCESS；Checkstyle `severity=error` 0 违规 |
| 2 | 前端 | `pnpm build` 全包通过；`eslint` 0 error |
| 3 | 数据 | 四库建成，各表字段与 DDL 一致；种子数据可查 |
| 4 | 网关 | 四路径经 8080 全链路 `code=0`；无 token 401；缺权限码 403 |
| 5 | 契约 | 前端 `ErpItem`/`MesWorkOrder`/`PdmProduct`/`OaDocument` 字段与后端 VO 一一对应，`id` 全链路为 string |
| 6 | 占位清零 | 基座 4 个 PlaceholderPage、门户 3 处占位不再出现（grep `TODO` / `PlaceholderPage` 归零） |
| 7 | 文档 | devlog + review 报告归档；roadmap Phase 4 勾选；service-catalog 同步 |

---

## 七、风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| 四应用同构代码重复 | 维护成本 | 先由 erp 产出模板，其余复制后按需精简；不抽公共业务模块（避免过早抽象） |
| 基座 proxy 缺失导致子应用请求 404 | 联调失败 | G2 先行；子应用独立 dev 时各自 `vite.config` 也补 proxy，双路径可验证 |
| 基座登录从 mock 切真实后影响既有演示 | 体验回退 | 保留 mock 开关 `VITE_USE_MOCK_AUTH`（仅 dev），默认走真实 |
| 权限种子 id 冲突 | 鉴权失败 | 先全表扫描现有 id（当前最大 79），从 80 起分配并在报告中登记 |
| 数据库未建 | 服务启动失败 | H 阶段前统一执行四份 `db/init.sql`；建库脚本纳入冒烟前置 |

---

## 八、交付物清单

- 后端：4 个模块的 DDL + Entity/Mapper/Service/Controller + 配置；网关 4 条路由；auth 权限种子。
- 前端：4 个子应用 API 层与页面补全；基座真实登录 + proxy + 占位路由收口；门户 3 处占位收口。
- 验证：`wave5.46-phase4.ps1` 冒烟脚本 + `docs/reviews/2026-10-21-wave5.46-phase4.md`（实施后）。
- 文档：本规划、roadmap/devlog/service-catalog 同步。
