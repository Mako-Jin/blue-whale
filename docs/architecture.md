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
| **sdk** | whale-i18n-sdk | 国际化（数据库 > 属性文件 > 默认值） |
| | whale-auth-sdk | 认证鉴权 SDK |
| **services** | whale-auth | 权限中心（RBAC + ABAC 六层级） |
| | whale-gateway | API 网关 |
| | whale-job / workflow / form / file / pay | 基础服务 |
| **apps** | whale-erp / mes / pdm / oa | 业务应用 |
| **ops** | whale-audit / monitor / alert | 运维监控 |

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
- 注意：当前为里程碑版本（4.0.0-M3），生产使用前建议跟进正式版

### Spring Cloud Gateway
- 响应式（WebFlux），高并发、低延迟
- 动态路由从 Nacos 读取
- 集成 Sentinel 限流熔断

### qiankun
- 基于 single-spa 的微前端方案，社区成熟
- 支持样式隔离、JS 沙箱、预加载

## 部署架构

开发环境使用 Docker Compose 一键拉起；生产环境使用 Kubernetes 编排。

详细部署步骤参见 `docs/devops.md`。
