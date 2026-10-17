# 服务与端口清单（Service Catalog）

> 用途：**全平台端口 ↔ 服务 ↔ 组件**的唯一权威清单。看端口即知对应哪个服务，看服务即知其分组、库、对外前缀与职责。
> 关联需求：REQ-ARCH-005（新增）、REQ-ARCH-004（命名规范，[naming-conventions.md](naming-conventions.md) §八）
> 最后更新：2026-10-17（Wave 5.1i）｜ 维护：BlueWhale

---

## 〇、口径说明

| 项 | 说明 |
|---|---|
| 数据来源 | 实测核对：各服务 `src/main/resources/application.yml` 的 `server.port` / `spring.application.name` / `datasource.url`、`whale-gateway/application.yml` 路由、各前端 `vite.config.ts` 的 `server.port`、`infrastructure/docker/docker-compose*.yml`、本机 `Get-NetTCPConnection` 实监听核验 |
| 「本机状态」含义 | **已运行**＝本机实测监听/注册过；**可启动**＝模块已具备启动条件、尚未在本机启动；**规划**＝仅设计登记，无实现 |
| 端口分段 | 见 §七；服务端口一律用 `${SERVER_PORT:xxxx}` 或 `${server.port:xxxx}` 外置可覆盖（`whale-auth` 当前为硬编码 `8081`，见 §九 偏差 C-02） |
| 命名铁律 | `模块目录 == artifactId == spring.application.name == Nacos 服务名 == whale-<域>`（[naming-conventions.md](naming-conventions.md) §三） |

---

## 一、后端服务清单（15 个）

| # | 服务名（Spring / Nacos） | 所属分组 | 模块目录 | 端口 | 对外一级前缀 | 网关路由 | 数据库（`bluewhale_<域>`） | 启动类 | 职责 | 本机状态 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `whale-gateway` | whale-services | `whale-services/whale-gateway` | **8080** | ——（唯一入口） | —— | 无（仅 Redis 存放黑名单） | `WhaleGatewayApplication` | 统一入口：路由转发、JWT 鉴权、令牌黑名单校验、TraceId | **已运行**（已在 Nacos 注册） |
| 2 | `whale-auth` | whale-services | `whale-services/whale-auth` | **8081** | `/auth/**` | ✅ `lb://whale-auth` | **`bluewhale_auth`**（9 表，已建） | `WhaleAuthApplication` | 认证与权限中心：登录/刷新/注销、RBAC+ABAC、用户/部门/角色/菜单/权限 | **已运行**（已在 Nacos 注册） |
| 3 | `whale-job` | whale-services | `whale-services/whale-job` | **8082** | `/job/**` | ✅ `lb://whale-job` | `bluewhale_job`（**规划，未建库**） | `WhaleJobApplication` | 定时任务调度 | 可启动 |
| 4 | `whale-workflow` | whale-services | `whale-services/whale-workflow` | **8083** | `/workflow/**` | ✅ `lb://whale-workflow` | `bluewhale_workflow`（**规划，未建库**） | `WhaleWorkflowApplication` | 工作流引擎（审批流） | 可启动 |
| 5 | `whale-form` | whale-services | `whale-services/whale-form` | **8084** | `/form/**` | ✅ `lb://whale-form` | `bluewhale_form`（**规划，未建库**） | `WhaleFormApplication` | 动态表单 | 可启动 |
| 6 | `whale-file` | whale-services | `whale-services/whale-file` | **8085** | `/file/**` | ✅ `lb://whale-file` | `bluewhale_file`（**规划，未建库**） | `WhaleFileApplication` | 文件上传下载、附件管理 | 可启动 |
| 7 | `whale-pay` | whale-services | `whale-services/whale-pay` | **8086** | `/pay/**` | ✅ `lb://whale-pay` | `bluewhale_pay`（**规划，未建库**） | `WhalePayApplication` | 支付与结算 | 可启动 |
| 8 | `whale-erp` | whale-apps | `whale-apps/whale-erp` | **9001** | `/erp/**` | ❌ **未配路由** | `bluewhale_erp`（**规划，未建库**） | `WhaleErpApplication` | ERP 业务应用 | 可启动 |
| 9 | `whale-mes` | whale-apps | `whale-apps/whale-mes` | **9002** | `/mes/**` | ❌ **未配路由** | `bluewhale_mes`（**规划，未建库**） | `WhaleMesApplication` | MES 制造执行 | 可启动 |
| 10 | `whale-pdm` | whale-apps | `whale-apps/whale-pdm` | **9003** | `/pdm/**` | ❌ **未配路由** | `bluewhale_pdm`（**规划，未建库**） | `WhalePdmApplication` | PDM 产品数据管理 | 可启动 |
| 11 | `whale-oa` | whale-apps | `whale-apps/whale-oa` | **9004** | `/oa/**` | ❌ **未配路由** | `bluewhale_oa`（**规划，未建库**） | `WhaleOaApplication` | OA 协同办公 | 可启动 |
| 12 | `whale-audit` | whale-ops | `whale-ops/whale-audit` | **9101** | `/audit/**` | ✅ `lb://whale-audit` | **`bluewhale_audit`**（`audit_log`，已建） | `WhaleAuditApplication` | 审计日志落库与查询 | **已运行**（已在 Nacos 注册） |
| 13 | `whale-monitor` | whale-ops | `whale-ops/whale-monitor` | **9102** | `/monitor/**` | ✅ `lb://whale-monitor` | ——（采集为主） | `WhaleMonitorApplication` | 监控指标采集 | 可启动 |
| 14 | `whale-alert` | whale-ops | `whale-ops/whale-alert` | **9103** | `/alert/**` | ❌ **未配路由** | ——（规划） | `WhaleAlertApplication` | 告警通知 | 可启动 |
| 15 | `whale-config` | whale-registry | `whale-registry/whale-config` | **9201** | `/config/**` | ❌ **未配路由** | ——（配置来自 Nacos） | `WhaleConfigApplication` | 配置中心管理面 | 可启动 |

> 「对外一级前缀」= 服务自持的前缀，网关**只转发、不剥离**（REQ-ARCH-003）。
> 网关 `discovery.locator.enabled=true` 已开启服务发现路由：`lb://whale-<域>` → `http://<服务实例>`。

---

## 二、网关路由映射（`whale-gateway`，现配 8 条）

| 路由 id | 断言前缀 | 目标 URI | 对应服务 | 状态 |
|---|---|---|---|---|
| `whale-auth` | `Path=/auth/**` | `lb://whale-auth` | whale-auth | ✅ 已配（**注意**：仅覆盖 `/auth/**`，用户/部门/角色/菜单/权限等管理接口待补，见 AUTH-38） |
| `whale-job` | `Path=/job/**` | `lb://whale-job` | whale-job | ✅ 已配 |
| `whale-workflow` | `Path=/workflow/**` | `lb://whale-workflow` | whale-workflow | ✅ 已配 |
| `whale-form` | `Path=/form/**` | `lb://whale-form` | whale-form | ✅ 已配 |
| `whale-file` | `Path=/file/**` | `lb://whale-file` | whale-file | ✅ 已配 |
| `whale-pay` | `Path=/pay/**` | `lb://whale-pay` | whale-pay | ✅ 已配 |
| `whale-monitor` | `Path=/monitor/**` | `lb://whale-monitor` | whale-monitor | ✅ 已配 |
| `whale-audit` | `Path=/audit/**` | `lb://whale-audit` | whale-audit | ✅ 已配 |
| —— | `/erp/**`、`/mes/**`、`/pdm/**`、`/oa/**`、`/config/**`、`/alert/**` | —— | whale-erp / mes / pdm / oa / config / alert | ❌ **缺路由**（见 §九 偏差 C-03） |

**网关免鉴权白名单**：`/auth/login`、`/auth/refresh`、`/actuator/**`、`/v3/api-docs/**`、`/doc.html`、`/webjars/**`、`/favicon.ico`。

---

## 三、前端应用清单（6 个，开发态端口）

| # | 应用 | 目录 | 包名（NPM） | 角色 | 开发端口 | base 路径 | 备注 |
|---|---|---|---|---|---|---|---|
| 1 | whale-platform | `frontend/platform-apps/whale-platform` | ——（基座） | qiankun **基座**（登录页 + MainLayout 多标签） | **8000** | `/` | 承载所有子应用 |
| 2 | whale-admin | `frontend/admin` | `whale-admin` | 管理端（权限/用户/角色/菜单） | **8080** | `/` | ⚠ 与 `whale-gateway` 8080 **端口冲突**（偏差 C-01）；目录名与包名不一致（N-03） |
| 3 | whale-ops | `frontend/ops` | `whale-ops` | 运维端（审计/监控/告警） | **8081** | `/` | ⚠ 与 `whale-auth` 8081 **端口冲突**（偏差 C-01） |
| 4 | whale-portal | `frontend/platform-apps/whale-portal` | `whale-portal` | qiankun **子应用**（门户） | **8101** | `/portal` | 微应用名 `whale-portal` |
| 5 | whale-erp | `frontend/platform-apps/whale-erp` | `whale-erp` | qiankun **子应用**（ERP） | **8102** | `/erp` | 微应用名 `whale-erp` |
| 6 | whale-oa | `frontend/platform-apps/whale-oa` | `whale-oa` | qiankun **子应用**（OA） | **8103** | `/oa` | 微应用名 `whale-oa` |

共享包（不占端口）：`frontend/shared-packages/whale-common-tools`、`whale-common-theme`、`whale-common-three`、`frontend/elemental/whale-ui`、`frontend/games/whale-games-core`（NPM scope `@bluewhale/`）。

---

## 四、第三方 / 基础设施组件清单（含端口）

| # | 组件 | 用途 | 端口 | 版本 | 部署方式 | 本机状态 |
|---|---|---|---|---|---|---|
| 1 | **MySQL** | 业务库 + Nacos 外置存储 | **3306**（TCP） | 8.0.45（本机 `D:\mysql8`）／镜像 8.4（compose） | 本机原生；compose `bw-mysql` | **已运行**。库：`bluewhale_auth`、`bluewhale_audit`、`bluewhale_nacos`（另有外部遗留 `yc-sts-db-auth` / `yc-sts-db-flow`，登记不改名） |
| 2 | **Redis** | 令牌黑名单、i18n 缓存、分布式锁 | **6379**（TCP） | 5.0.14.1（本机 `tools/`）／镜像 7-alpine（compose） | 本机原生；compose `bw-redis` | **已运行** |
| 3 | **Nacos** | 服务注册发现 + 配置中心 | **8848**（HTTP OpenAPI + 主服务）<br>**8849**（控制台，仅 3.x 独立控制台）<br>**9848**（gRPC 客户端→服务端）<br>**9849**（gRPC 服务端→服务端，集群） | 3.2.4（本机 `tools/nacos`）／镜像 2.3.2（compose） | 本机原生（standalone + MySQL 外置存储）；compose `bw-nacos` | **已运行**（PID 实测监听 8848/8849/9848/9849；已注册 `whale-gateway` / `whale-auth` / `whale-audit`） |
| 4 | **RabbitMQ** | 审计日志汇聚、异步消息（规划） | **5672**（AMQP）<br>**15672**（Management 控制台） | 3-management-alpine | 仅 compose `bw-rabbitmq`（profiles: `messaging`） | ⚠ **未运行**（本机无 Docker）→ 审计「跨库可见性」等验证受阻 |
| 5 | **Nginx** | 反向代理 + 前端静态资源 + TLS | **80**（HTTP）<br>**443**（HTTPS） | 自建镜像（`infrastructure/docker/nginx`） | 仅 compose `bw-nginx` | ⚠ **未运行**（本机无 Docker） |
| 6 | **Prometheus** | 指标采集 | **9090**（默认） | —— | 仅有 `infrastructure/prometheus/prometheus.yml`，**compose 未定义该服务** | 规划 |
| 7 | **Grafana** | 监控看板 | **3000**（默认） | —— | **未定义**（无 compose / 无配置） | 规划 |
| 8 | **Sentinel Dashboard** | 流控/熔断控制台（REQ-ARCH 限流方案） | **8858**（默认） | —— | **未引入**（`whale-gateway` 未接 Sentinel） | 规划 |

> Nacos 端口版本差异：本机 3.2.4 控制台独立于 **8849**；compose 内 2.3.2 控制台与 API 同处 **8848**（`/nacos`）。升级 compose 镜像时须同步端口与健康检查（见 §九 偏差 C-04）。
> Nacos 3.x 已移除 v1 OpenAPI（`/nacos/v1/ns/service/list` 返回 501、`/nacos/v1/console/health/readiness` 返回 404），服务列表核验请改用控制台或日志。

---

## 五、端口 → 服务 速查（升序）

| 端口 | 归属 | 类型 | 说明 |
|---|---|---|---|
| **80** | Nginx | 基础设施 | HTTP 入口（compose，未运行） |
| **443** | Nginx | 基础设施 | HTTPS 入口（compose，未运行） |
| **3000** | Grafana | 基础设施 | 规划 |
| **3306** | MySQL | 基础设施 | 业务库 + `bluewhale_nacos` |
| **5672** | RabbitMQ | 基础设施 | AMQP（未运行） |
| **6379** | Redis | 基础设施 | 黑名单 / 缓存 / 锁 |
| **8000** | `whale-platform` | 前端（基座） | qiankun 基座 dev server |
| **8080** | `whale-gateway` | 后端 | **唯一 API 入口**；⚠ 与 `frontend/admin`(8080) 冲突 |
| **8081** | `whale-auth` | 后端 | 认证与权限中心；⚠ 与 `frontend/ops`(8081) 冲突 |
| **8082** | `whale-job` | 后端 | 定时任务 |
| **8083** | `whale-workflow` | 后端 | 工作流 |
| **8084** | `whale-form` | 后端 | 动态表单 |
| **8085** | `whale-file` | 后端 | 文件服务 |
| **8086** | `whale-pay` | 后端 | 支付 |
| **8101** | `whale-portal` | 前端（子应用） | base `/portal` |
| **8102** | `whale-erp` | 前端（子应用） | base `/erp` |
| **8103** | `whale-oa` | 前端（子应用） | base `/oa` |
| **8848** | Nacos | 基础设施 | HTTP OpenAPI + 主服务（contextPath `/nacos`） |
| **8849** | Nacos Console | 基础设施 | 控制台（Nacos 3.x） |
| **8858** | Sentinel Dashboard | 基础设施 | 规划（未引入） |
| **9001** | `whale-erp` | 后端 | ERP（缺网关路由） |
| **9002** | `whale-mes` | 后端 | MES（缺网关路由） |
| **9003** | `whale-pdm` | 后端 | PDM（缺网关路由） |
| **9004** | `whale-oa` | 后端 | OA（缺网关路由） |
| **9090** | Prometheus | 基础设施 | 规划（compose 未定义） |
| **9101** | `whale-audit` | 后端 | 审计日志 |
| **9102** | `whale-monitor` | 后端 | 监控采集 |
| **9103** | `whale-alert` | 后端 | 告警（缺网关路由） |
| **9201** | `whale-config` | 后端 | 配置中心管理面（缺网关路由） |
| **9848** | Nacos | 基础设施 | gRPC 客户端→服务端 |
| **9849** | Nacos | 基础设施 | gRPC 服务端→服务端（集群） |
| **15672** | RabbitMQ | 基础设施 | Management 控制台（未运行） |

---

## 六、服务间调用契约

| 调用方 | 被调方 | 地址形式 | 说明 |
|---|---|---|---|
| 前端（任意） | 网关 | `http://<host>:8080/<一级前缀>/...` | 唯一对外入口，不再直连业务服务 |
| 网关 | 各后端服务 | `lb://whale-<域>` | Nacos 服务发现，服务名 = `spring.application.name` |
| 任意后端服务 | `whale-auth` | `lb://whale-auth` 或 SDK `whale-auth-sdk` | 包 `com.bluewhale.auth.sdk` |
| 任意后端服务 | `whale-file` | `lb://whale-file` | —— |

---

## 七、端口分段规划（已落地口径）

| 区段 | 用途 | 现状 |
|---|---|---|
| **80 / 443** | Nginx 反代与 TLS | 规划（compose） |
| **3000 / 9090** | 监控看板 / 指标 | 规划 |
| **3306 / 6379 / 5672 / 15672** | MySQL / Redis / RabbitMQ | 3306、6379 已运行；5672/15672 未运行 |
| **8000–8099** | **前端 dev server**（8000 基座、8101+ 子应用已用 8101–8103）<br>⚠ 现 `admin`=8080、`ops`=8081 落在后端区段，与网关/auth 冲突 | 见偏差 C-01 |
| **8080** | **网关（唯一后端入口）** | 已运行 |
| **8081–8099** | **核心服务** | auth 8081、job 8082、workflow 8083、form 8084、file 8085、pay 8086 |
| **8848 / 8849 / 9848 / 9849** | **Nacos** | 已运行 |
| **9001–9099** | **业务应用** | erp 9001、mes 9002、pdm 9003、oa 9004 |
| **9101–9199** | **运维（ops）** | audit 9101、monitor 9102、alert 9103 |
| **9201–9299** | **注册与配置** | config 9201 |

---

## 八、环境变量覆盖（部署时按此覆盖，勿改源码）

| 变量 | 作用 | 默认 | 使用方 |
|---|---|---|---|
| `SERVER_PORT` | 网关/审计端口 | 8080 / 9101 | whale-gateway、whale-audit |
| `server.port` | 其余服务端口 | 见 §一 | job/workflow/form/file/pay/monitor/alert/erp/mes/pdm/oa/config |
| `NACOS_ADDR` / `NACOS_SERVER` | 注册中心地址 | `127.0.0.1:8848` | 全部服务（网关用 `NACOS_ADDR`，其余用 `NACOS_SERVER`） |
| `NACOS_NAMESPACE` | 命名空间 | `public` | 全部服务 |
| `REDIS_HOST` / `REDIS_PORT` | Redis | `127.0.0.1` / `6379` | whale-gateway、whale-auth |
| `MYSQL_USER` / `MYSQL_PASSWORD` | 库账号 | 见各 yml | whale-auth、whale-audit |
| `JWT_SECRET` | 认证签名密钥 | 开发占位（非 dev 拒绝启动） | whale-auth |
| `WHALE_GATEWAY_JWT_SECRET` | 网关校验密钥 | 开发占位 | whale-gateway |
| `SPRING_PROFILES_ACTIVE` | 环境 | `dev` | 全部服务 |

> ⚠ 注意：`NACOS_ADDR`（网关）与 `NACOS_SERVER`（其余服务）**两个变量名不一致**，见 §九 偏差 C-05。

---

## 九、待办与偏差

| 编号 | 问题 | 优先级 | 处置建议 |
|---|---|---|---|
| **C-01** | 前端 dev 端口与后端冲突：`frontend/admin` 8080 = 网关 8080、`frontend/ops` 8081 = `whale-auth` 8081，同时启动会端口占用失败 | **P2** | 前端 dev 端口统一收进 §七 的前端区段（如 admin→8001、ops→8002），并同步 `vite.config.ts` 与本清单 |
| **C-02** | `whale-auth` 端口硬编码 `server.port: 8081`，未按其他服务写法 `${server.port:8081}` 外置 | P3 | 改为 `${SERVER_PORT:8081}`，与规范统一 |
| **C-03** | `whale-gateway` **缺 6 条路由**：`/erp/**`、`/mes/**`、`/pdm/**`、`/oa/**`、`/config/**`、`/alert/**`；另 `/auth/**` 未覆盖用户/部门/角色/菜单/权限管理接口（AUTH-38） | **P1** | 按「服务自持一级前缀」补齐路由，逐条冒烟 |
| **C-04** | Nacos 版本/端口不一致：本机 3.2.4（控制台 8849）vs compose 镜像 2.3.2（控制台 8848） | P2 | 统一 compose 镜像至 3.x 并同步端口、健康检查（`/nacos/actuator/health/liveness`） |
| **C-05** | 注册中心环境变量名分裂：网关 `NACOS_ADDR` vs 其余 `NACOS_SERVER` | P3 | 统一为 `NACOS_SERVER`（保留 `NACOS_ADDR` 兼容期），登记后批量替换 |
| **C-06** | 8 个业务服务库（job/workflow/form/file/pay/erp/mes/pdm/oa）在规范 §八 已登记库名，但 `application.yml` **均未配数据源**、库亦未建 | P2 | 按需逐服务接入 datasource + `db/init.sql`，建库后本清单状态由「规划」改「已建」 |
| **C-07** | Prometheus/Grafana/Sentinel 仅在文档中「规划」，compose 未定义、网关未接 Sentinel | P3 | 待监控需求明确后补 compose 服务与端口登记 |

---

## 十、维护方式

1. **新增服务/组件**：先在本清单登记（端口从 §七 对应区段内分配），再落代码/配置；同时按 [naming-conventions.md](naming-conventions.md) §八 登记域词。
2. **改端口**：同步更新本清单 §一/§四/§五/§七 + `application.yml`/`vite.config.ts`，并跑一次冒烟。
3. **门禁**：代码检查清单 **E15** 已含「库/服务/包命名符合性」；服务端口与对外前缀的一致性由本清单兜底核对。
