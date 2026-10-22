# 蓝鲸系统 BlueWhale Platform

> 企业级全栈业务平台 | Spring Boot 4 · React 19 · 微前端 qiankun · Docker

## 技术栈

> 本表只描述技术选型，**不硬编码版本号**。版本以代码与清单为唯一事实源，避免文档与构建漂移。

| 层级 | 技术 | 版本来源 |
|---|---|---|
| 后端框架 | Spring Boot | [backend/whale-parent/pom.xml](backend/whale-parent/pom.xml)（`spring-boot.version`） |
| 微服务 | Spring Cloud + Spring Cloud Alibaba | 同上（`spring-cloud.version` / `spring-cloud-alibaba.version`） |
| JDK | OpenJDK | 同上（`java.version`） |
| 构建工具 | Apache Maven | [mvnw](mvnw) / [.mvn/wrapper](.mvn/wrapper) |
| 前端框架 | React + TypeScript | [frontend/package.json](frontend/package.json) |
| 构建工具 | Vite | 各前端应用 `package.json` |
| 微前端 | qiankun | [frontend/package.json](frontend/package.json) |
| Monorepo | Turborepo + pnpm workspace | [frontend/package.json](frontend/package.json) |
| 网关 | Spring Cloud Gateway | [backend/whale-parent/pom.xml](backend/whale-parent/pom.xml) |
| 注册配置 | Nacos | [docs/service-catalog.md](docs/service-catalog.md) §四 |
| 数据库 | MySQL | [docs/service-catalog.md](docs/service-catalog.md) §四 |
| 缓存 | Redis + Redisson | 同上 |
| 消息队列 | RabbitMQ | 同上 |
| 容器化 | Docker Compose / Kubernetes | [infrastructure/docker](infrastructure/docker) |
| 反向代理 | Nginx | [infrastructure/docker/nginx](infrastructure/docker/nginx) |
| 监控 | Spring Boot Admin + Prometheus | [docs/service-catalog.md](docs/service-catalog.md) §四 |
| CI/CD | GitHub Actions | [.github/workflows](.github/workflows) |

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

全部端口与服务地址以 [docs/service-catalog.md](docs/service-catalog.md) §五「端口 → 服务 速查」为唯一权威清单；本机开发默认从网关 `http://localhost:8080` 进入。

## 架构说明

参见 `docs/architecture.md`。

## AI Agent

蓝鲸系统内置 AI Agent 平台，可咨询系统架构、API 文档、运维问题，并提供 Skill 工具集成。

## License

TBD
