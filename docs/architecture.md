# 架构设计文档

## 总体架构

```
                    ┌─────────────────────────────────┐
                    │         用户浏览器 / 客户端       │
                    └──────────────┬──────────────────┘
                                   │
                    ┌──────────────▼──────────────────┐
                    │           Nginx / CDN            │
                    │   (SSL终止 / 静态资源 / 反向代理) │
                    └──────────────┬──────────────────┘
                                   │
              ┌────────────────────┼────────────────────┐
              ▼                    ▼                    ▼
    ┌─────────────────┐   ┌───────────────┐   ┌─────────────────┐
    │  qiankun 基座    │   │  API Gateway  │   │   静态资源服务    │
    │  whale-platform │   │  Spring Cloud │   │  (Nginx 托管)    │
    │  (React + TS)   │   │  Gateway      │   │                 │
    └────────┬────────┘   └───────┬───────┘   └─────────────────┘
             │                    │
    ┌────────┴────────┐     ┌─────┴─────┐
    │                 │     │           │
    ▼                 ▼     ▼           ▼
┌───────┐         ┌───────┐ ┌──────┐ ┌──────┐
│ Portal │         │  ERP  │ │ Auth │ │ Job  │  ...微服务集群
└───────┘         └───────┘ └──────┘ └──────┘
                              │           │
                        ┌─────┴───────────┴─────┐
                        │     Nacos 注册/配置中心   │
                        └───────────┬────────────┘
                                    │
              ┌─────────────────────┼─────────────────────┐
              ▼                     ▼                     ▼
         ┌─────────┐          ┌─────────┐          ┌───────────┐
         │  MySQL   │          │  Redis  │          │ RabbitMQ  │
         │ (8.4)    │          │   (7)   │          │    (3)    │
         └─────────┘          └─────────┘          └───────────┘
```

## 后端模块划分

### Maven reactor 结构（7 个聚合模块，52 个 Maven 模块）

| 分类 | 聚合模块 | 说明 |
|---|---|---|
| **parent** | whale-parent | 统一依赖版本（Spring Boot / Cloud / Alibaba，版本号以 `pom.xml` 为准） |
| **common** | whale-common | `whale-tools`（通用工具）、`whale-security`（加密/JWT）、`whale-spring-boot-starter`（自动配置：Result/异常/审计切面/i18n/内网令牌等） |
| **sdk** | whale-sdk | `whale-i18n-sdk`（国际化，随服务启动自建 `sys_i18n_message`）、`whale-auth-sdk`（认证鉴权 SDK） |
| **services** | whale-services | 网关 + 核心服务：gateway / service-auth / job / workflow / form / service-file / pay / agent / im / archive / message / doc |
| **apps** | whale-apps | 业务应用：erp / mes / pdm / oa / pm / contract |
| **ops** | whale-ops | 运维监控：audit / monitor / alert |
| **registry** | whale-registry | 业务配置：config（与 Nacos 互补，管业务应用配置） |

> `ai/whale-ai`（Python / FastAPI，8092）为独立树，**不进 Maven reactor**，仅被 `whale-agent` 内网调用。

### 服务全景（25 个已落地：Java 24 + Python 1；另 2 个规划）

> 端口、库、网关路由与本机状态的**唯一权威清单**是 [service-catalog.md](service-catalog.md)；下表为架构视角摘要，两表由维护流程保持同步。

| 服务 | 端口 | 对外前缀 | 数据库（`bluewhale_<域>`） | 限界上下文与职责 |
|---|---|---|---|---|
| whale-gateway | 8080 | ——（唯一入口） | ——（Redis 存黑名单） | **接入网关**：路由转发、JWT 鉴权、令牌黑名单、限流 429（Redis 固定窗口）、熔断 503（resilience4j）、TraceId、内网路径屏蔽（internal-deny + 令牌头剥离） |
| whale-service-auth | 8081 | `/auth` `/user` `/dept` `/role` `/menu` `/permission` `/tenant` | bluewhale_auth | **身份与权限**：登录/刷新/注销（令牌轮换与撤销）、RBAC+ABAC、用户/部门/角色/菜单/权限/租户、权限缓存广播与重对齐 |
| whale-job | 8082 | `/job` | bluewhale_job | **任务调度**：定时任务注册/调度/重对齐 |
| whale-workflow | 8083 | `/workflow` | bluewhale_workflow | **工作流**：BPMN 流程定义/发起/审批/待办，业务回调 |
| whale-form | 8084 | `/form` | bluewhale_form | **动态表单**：表单模型/填报/动态表引擎/设计器元数据 |
| whale-file（whale-service-file） | 8085 | `/file` | bluewhale_file | **文件**：上传下载、附件、桶白名单（知识入库 default-deny） |
| whale-pay | 8086 | `/pay` | bluewhale_pay | **支付**：支付单、mock 渠道支付/退款、流水与对账 |
| whale-agent | 8090 | `/agent` | bluewhale_agent | **知识 Agent**：事实层采集、Lucene 检索、问答编排；对外调 `whale-ai` |
| whale-ai（Python） | 8092 | ——（仅内网） | ——（记忆存 Redis） | **LLM 编排**：LangChain/LangGraph 工具循环与确认态；不注册 Nacos/网关，不持业务库凭据 |
| whale-im | 8091 | `/im`（含 `/im/ws`） | bluewhale_im | **即时通讯**：会话/消息/已读/在线/机器人；WS ticket 鉴权 |
| whale-archive | 8093 | `/archive` | bluewhale_archive | **档案**：门类/规则/条目/档号/快照/检索、补扫 |
| whale-message | 8094 | `/message` | bluewhale_message | **统一消息**：模板渲染、INAPP/EMAIL/SMS/IM 渠道路由、幂等与投递记录、失败重试、站内信收件箱 |
| whale-doc | 8095 | `/doc`（含 `/doc/ws`） | bluewhale_doc | **在线文档/Wiki**：空间与页面树、TipTap 协作、版本/评论/附件/搜索/ACL/模板/审批/分享/归档、Agent 知识索引 |
| whale-music | 8096 | `/music` | bluewhale_music | **音乐平台**（M0–M5 已落地，可上线）：歌手/专辑/歌曲/标签曲库、用户上传与版权协议、公开/自听可见性；播放票据与 Range 流式、歌单、发现、评论与审核、版权申诉、7 天榜；429 限流、指标与 Grafana 面板、备份演练已实测 |
| whale-scene（**规划**） | 8097 | `/scene` | bluewhale_scene（规划） | **城池场景导航**：场景/区域/建筑/入口节点树、入口绑定、角色/部门/用户可见性、2D 优先导航与 3D 可选增强；低配/无 WebGL 降级 2D；不拥有业务数据（Wave 5.77，未编码） |
| whale-video | 8098 | `/video` | bluewhale_video | **视频平台**（M0–M5 已落地）：短视频 / 视频 / 系列 / 电影三条内容线共享 `vd_content` 注册、播放票据与 Range 流式、点赞/收藏/评论、分类标签与观看历史底座；视频字节归 `whale-file`（按权限码大文件上传策略 G1），`/video/internal/**` 对外 403、`/video/stream/**` 独立限流且不进熔断 |
| whale-erp | 9001 | `/erp` | bluewhale_erp | **ERP 业务应用**：物料主数据 + 供应商主数据（供应商准入/资质/绩效归 SRM，不属 CRM） |
| whale-mes | 9002 | `/mes` | bluewhale_mes | **MES 制造执行** |
| whale-pdm | 9003 | `/pdm` | bluewhale_pdm | **PDM 产品数据管理** |
| whale-oa | 9004 | `/oa` | bluewhale_oa | **OA 协同办公** |
| whale-pm | 9005 | `/pm` | bluewhale_pm | **项目管理**：项目空间/成员/状态流转、工作项、迭代看板、报表、站内通知 + 工作流审批（默认关闭） |
| whale-contract | 9006 | `/contract` | bluewhale_contract | **合同管理**：起草/相对方/版本/审批/终止/归档；印章/PDF 盖章/数字签名（CA/TSA/P12 fail-closed）/三方签章（mock） |
| whale-crm（**规划**） | 9007 | `/crm` | bluewhale_crm（规划） | **客户关系**：客户/联系人/商机/销售阶段/跟进/客户 360；供应商仍归 ERP/SRM，合同只引用 `crmCustomerId` 快照；需通过需求门禁（REQ-ARCH-011） |
| whale-audit | 9101 | `/audit` | bluewhale_audit | **审计**：审计日志汇聚落库与查询（HTTP 转发 + RabbitMQ 可选 + 本地回退） |
| whale-monitor | 9102 | `/monitor` | ——（只读聚合） | **监控聚合**：Nacos 实例只读列表/健康；采集存储归外部 Prometheus |
| whale-alert | 9103 | `/alert` | bluewhale_alert | **告警**：Alertmanager webhook 接入、指纹去重、EMAIL/SMS/WEBHOOK 通道 |
| whale-config | 9201 | `/config` | bluewhale_config | **业务配置**：配置项 CRUD（租户内键唯一）、版本历史、一键回滚；与 Nacos 互补 |

### 限界上下文要点

- **身份权限（auth）** 是全平台唯一身份源：其余服务经网关注入的 `X-User-Id` / `X-Permission-Codes` 判权，服务间内网调用统一附加 `X-Internal-Token`（网关入口剥离外部同名头，纵深防御见 [devlog 2026-10-30](devlog/2026-10-30.md)）。
- **一服务一库**（`bluewhale_<域>`），跨域不直连对方库；跨服务集成走「内网 REST（`/**/internal/**`）+ Nacos 服务发现」，消息类旁路可经 RabbitMQ（审计汇聚可选）。
- **审计横切**：各服务 `audit_log`/`api_log` 本地落库，`whale-audit` 提供汇聚与查询；前端行为日志经网关白名单批量上报。
- **可观测性横切**：各服务暴露 `/actuator/prometheus`（`application` 标签 = 服务名），Prometheus 以 `whale-*` job 抓取，Alertmanager 路由至 `whale-alert`；Grafana 看板经 provisioning 自动加载（[部署指南](devops.md)）。

## 网关路由与前缀约定

**规则（REQ-ARCH-003，Wave 5.1f 定稿）**：**服务自持对外一级路径前缀，网关只做转发、不剥离前缀。**

- 后端服务的 Controller 路径即**对外路径**，不做「网关去前缀、服务无前缀」的二次映射：
  - `whale-service-auth`：`AuthController` → `/auth/login`、`/auth/refresh`、`/auth/logout`；`UserController` → `/user/**`；另有 `/dept/**`、`/role/**`、`/menu/**`、`/permission/**`
  - `whale-audit`：`AuditLogController` → `/audit/log/**`
- 网关路由形如 `Path=/{segment}/**` → `uri: lb://whale-{service}`，**不得使用 `StripPrefix`** 过滤器。
- 鉴权白名单两侧按**同一对外路径**书写，避免「网关放行路径」与「服务放行路径」漂移：
  - 网关：`whale.gateway.jwt.white-list`（`/auth/login`、`/auth/refresh`、`/actuator/**` …）
  - 服务：`whale-service-auth` `SecurityConfig#authorizeHttpRequests`（`/auth/login`、`/auth/refresh`、`/auth/logout` …）

> **反面案例（Wave 5.1f 实测根因，AUTH-29）**：网关曾对 `/auth/**`、`/audit/**` 配 `StripPrefix=1`，
> 于是 `POST /auth/login` 被改写为 `/login` 转发给 `whale-service-auth`；该路径不在服务侧
> `SecurityConfig` 的 permitAll 列表内 → 服务 `RestAuthenticationEntryPoint` 直接返回
> `401 {"code":401,"message":"未认证"}`（HTTP 401），与网关 JWT 过滤器无关，从表面看极易误判为
> 「网关白名单未生效」。同类问题会让 `/user/**`、`/dept/**` 等路径根本无法被 `/auth/**` 路由命中。
> 实测证据：网关注册的下游请求 URI 为 `http://127.0.0.1:8080/login`；直连
> `POST http://127.0.0.1:8081/login` 与经网关 `POST http://127.0.0.1:8080/auth/login` 返回**完全相同**的 401 体。

## 命名规范

**规则（REQ-ARCH-004，Wave 5.1g 定稿）**：全平台命名统一以「品牌词根 `bluewhale` + 产品词根 `whale`」为骨架，**看名字即知属于哪一层、哪个域、做什么**。全量规则、域词表与映射速查表见 [naming-conventions.md](naming-conventions.md)，要点：

- **数据库**：`bluewhale_<域>`，一服务一库；**基础设施同前缀**（Nacos 库为 `bluewhale_nacos`，非裸 `nacos`）；
- **服务/模块**：`whale-<域>`，且 `模块目录 == artifactId == spring.application.name == Nacos 服务名`（`lb://whale-service-auth` 的可用前提）；
- **Java 包**：`com.bluewhale.<域>[.<层>]`；公共能力 `com.bluewhale.common.<能力>`；SDK 以 `.sdk` 结尾；
- **配置**：`whale.<域>.<能力>`；环境变量 `WHALE_<域>_<键>`；
- **Redis key**：`whale:<域>:<用途>:<标识>`；
- **端口分段**：8080 网关 / 8081–8099 核心服务 / 9001–9099 业务应用 / 9101–9199 运维 / 9201–9299 注册配置。

检查门禁：命名符合性纳入 [code-review-checklist.md](checklists/code-review-checklist.md) **E15**。

## 前端微前端架构

- **基座**：whale-platform（qiankun 主应用，路由分发）
- **子应用**：whale-portal / whale-erp / whale-oa（独立构建，动态加载）
- **共享包**：@bluewhale/*（tsup 构建，turbo 依赖链保证先构建）
- **样式隔离**：qiankun strictStyleIsolation + CSS Modules
- **数据通信**：initGlobalState 官方 API

## 技术选型说明

### Spring Boot 4
- 基于 Spring Framework 7，性能进一步提升
- GraalVM AOT 原生镜像支持更好
- 当前使用 Boot 4 正式版（GA），**具体版本号以 [whale-parent/pom.xml](../backend/whale-parent/pom.xml) 的 `spring-boot.version` 为准**，本文件不再重复版本号

### Spring Cloud Gateway
- 响应式（WebFlux），高并发、低延迟
- 动态路由从 Nacos 读取
- 限流：Redis 计数器（网关全局过滤器），超限返回 429 统一 `Result` 体
- 熔断：resilience4j（版本见父 POM），降级返回 503 统一 `Result` 体
- **Sentinel 未引入**（评估结论见 [reviews/2026-10-21-wave5.35-sentinel-evaluation.md](reviews/2026-10-21-wave5.35-sentinel-evaluation.md)）

### qiankun
- 基于 single-spa 的微前端方案，社区成熟
- 支持样式隔离、JS 沙箱、预加载

## 部署架构

开发环境使用 Docker Compose 一键拉起中间件与监控栈；生产环境使用生产覆盖层（Nginx TLS + 前端容器）编排。

详细部署步骤参见 [devops.md](devops.md)；端口/服务/组件的权威清单见 [service-catalog.md](service-catalog.md)。
