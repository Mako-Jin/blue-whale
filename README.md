# 蓝鲸系统 BlueWhale Platform

> 企业级全栈业务平台 | Spring Boot 4 · React 19 · 微前端 qiankun · Docker

## 技术栈

| 层级 | 技术 | 版本 |
|---|---|---|
| 后端框架 | Spring Boot | 4.0.0-M3 |
| 微服务 | Spring Cloud + Spring Cloud Alibaba | 2024.0.0-M3 |
| JDK | OpenJDK | 21 |
| 构建工具 | Apache Maven | 3.9+ |
| 前端框架 | React + TypeScript | 19 + 5.6 |
| 构建工具 | Vite | 6 |
| 微前端 | qiankun | 2 |
| Monorepo | Turborepo + pnpm workspace | 2 + 10 |
| 网关 | Spring Cloud Gateway | - |
| 注册配置 | Nacos | 2.3 |
| 数据库 | MySQL | 8.4 |
| 缓存 | Redis + Redisson | 7 |
| 消息队列 | RabbitMQ | 3 |
| 容器化 | Docker Compose / Kubernetes | - |
| 反向代理 | Nginx | - |
| 监控 | Spring Boot Admin + Prometheus | - |
| CI/CD | GitHub Actions | - |

## 目录结构

```
BlueWhale/
├── backend/                # Java 21 + Maven 多模块
│   ├── whale-parent/       # 父POM（统一依赖版本）
│   ├── whale-common/       # 基础组件
│   ├── whale-sdk/          # SDK
│   ├── whale-services/     # 核心服务
│   ├── whale-apps/         # 业务应用
│   ├── whale-ops/          # 运维监控
│   └── whale-registry/     # 注册配置
├── frontend/               # pnpm + Turborepo + qiankun
│   ├── shared-packages/    # 共享包
│   ├── elemental/          # 公共元素/组件库
│   ├── platform-apps/      # qiankun 微前端
│   ├── admin/              # 后台管理 SPA
│   ├── ops/                # 运维 SPA
│   └── games/              # 小游戏
├── infrastructure/         # Docker / Nginx / Redis / Prometheus
├── deploy/                 # Dockerfile 部署模板
├── .github/workflows/      # CI/CD Pipeline
├── scripts/                # 运维脚本
├── docs/                   # 文档
└── pom.xml                 # 根聚合 POM
```

## 快速开始

### 1. 基础设施

```bash
# 复制环境变量模板并填写
cp .env.example infrastructure/docker/.env

# 启动 MySQL / Redis / Nacos / RabbitMQ
cd infrastructure/docker
docker compose up -d
```

### 2. 后端

```bash
cd backend
mvn clean install -DskipTests

# 启动 API 网关
mvn -pl whale-services/whale-gateway spring-boot:run

# 启动权限中心
mvn -pl whale-services/whale-auth spring-boot:run
```

### 3. 前端

```bash
cd frontend
pnpm install
pnpm run dev              # 同时启动所有应用
```

### 4. 访问

| 服务 | URL |
|---|---|
| API 网关 | http://localhost:8080 |
| qiankun 基座 | http://localhost:8000 |
| 后台管理 | http://localhost:8080 |
| 运维平台 | http://localhost:8081 |
| Nacos | http://localhost:8848/nacos |

## 架构说明

参见 `docs/architecture.md`。

## AI Agent

蓝鲸系统内置 AI Agent 平台，可咨询系统架构、API 文档、运维问题，并提供 Skill 工具集成。

## License

TBD
