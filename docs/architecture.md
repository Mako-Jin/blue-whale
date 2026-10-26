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

| 分类 | 模块 | 说明 |
|---|---|---|
| **parent** | whale-parent | 统一依赖版本（Spring Boot 4 / Cloud / Alibaba） |
| **common** | whale-tools | 通用工具包 |
| | whale-security | 加密算法、JWT |
| | whale-spring-boot-starter | 自动配置 Starter |
| **sdk** | whale-i18n-sdk | 国际化（数据库 > 属性文件 > 默认值）；随服务启动自动建 `sys_i18n_message`，各服务自持 `i18n/<模块>/messages*.properties`，本地管理接口按服务开关 |
| | whale-auth-sdk | 认证鉴权 SDK |
| **services** | whale-service-auth | 权限中心（RBAC + ABAC 六层级） |
| | whale-gateway | API 网关 |
| | whale-job / workflow / form / file / pay | 基础服务 |
| **apps** | whale-erp / mes / pdm / oa | 业务应用 |
| **ops** | whale-audit / monitor / alert | 运维监控 |

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

开发环境使用 Docker Compose 一键拉起；生产环境使用 Kubernetes 编排。

详细部署步骤参见 `docs/devops.md`。
