# 运维部署指南

> 端口 / 服务 / 组件权威清单：[service-catalog.md](service-catalog.md)（§四 基础设施、§五 端口速查、§八 环境变量）。

## 开发环境

### 1. 中间件与监控栈（Docker Compose）

```bash
cp infrastructure/docker/.env.example infrastructure/docker/.env   # 按需填写口令等
cd infrastructure/docker
docker compose up -d        # MySQL / Redis / Nacos / RabbitMQ / Prometheus / Grafana / Alertmanager / Nginx
docker compose ps           # 健康检查
```

- 端口仅回环绑定（`127.0.0.1:`）；Grafana 未配 `GRAFANA_ADMIN_PASSWORD` 直接启动失败（无弱口令回退）。
- 监控栈：Prometheus（`infrastructure/prometheus/prometheus-docker.yml`，抓取宿主机 `whale-*` job）→ Alertmanager（webhook → `whale-alert`）→ Grafana（provisioning 自动装载数据源 `bluewhale-prometheus` 与 [BlueWhale 服务总览看板](../infrastructure/prometheus/grafana/dashboards/bluewhale-overview.json)，目录 `grafana/dashboards/` 只读挂载 `/var/lib/grafana/dashboards`）。

### 2. 后端服务

```bash
./mvnw -o -DskipTests -T 1C -f whale-backend/pom.xml install
# 方式一：脚本拉起
pwsh scripts/dev/whale-start-all.ps1
# 方式二：单服务
./mvnw -o -f whale-backend/pom.xml -pl whale-services/whale-gateway spring-boot:run
```

服务启动顺序建议：Nacos → gateway → service-auth → 其余按需；全部服务需 `WHALE_INTERNAL_TOKEN`（内网令牌，见 [backend.md](backend.md) 配置基线）。

### 3. 前端

```bash
cd whale-frontend && pnpm install && pnpm run dev
```

## 生产环境

- 生产覆盖层：`docker-compose.prod.yml`（Nginx TLS + 前端容器 + 资源限额）；**前置条件**为宿主机 Let's Encrypt 证书（`fullchain.pem`/`privkey.pem`）挂载，未挂载不得 `up`（V-09）。
- 变更前契约检查：`scripts/ci/bw-deploy-contract-check.ps1`；镜像矩阵后端 21 + 前端 14 由 GitHub Actions `docker-deploy` 构建推送 GHCR。
- 上线必检：网络边界（直连业务端口 403、`/internal/**` 令牌校验），清单见 [network-boundary.md](design/network-boundary.md) 与 `scripts/smoke/bw-network-boundary-check.ps1`。

## 运维手册索引

| 主题 | 文档 |
|---|---|
| SLO 与 PromQL | [ops/slo.md](ops/slo.md) |
| 文档服务运维（备份/清理/排障） | [ops/doc-service-ops.md](ops/doc-service-ops.md) |
| 音乐平台运维（部署/迁移/备份/清理/排障） | [ops/music-service-ops.md](ops/music-service-ops.md) |
| 知识索引重建/排障 | [ops/agent-index-ops.md](ops/agent-index-ops.md) |
| Agent MCP / IDE 接入 | [ops/agent-mcp.md](ops/agent-mcp.md) |
| 外部集成凭据预检 | [ops/external-integration-onboarding.md](ops/external-integration-onboarding.md) |
| CI/CD 流水线 | [design/cicd.md](design/cicd.md) |
| 冒烟脚本（32 个） | [../scripts/smoke/](../scripts/smoke/) |
