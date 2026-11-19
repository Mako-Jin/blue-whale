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
./mvnw -o -f whale-backend/pom.xml -pl whale-services/whale-service-gateway spring-boot:run
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
- 内网端点口径门禁：`python scripts/check-internal-boundary.py`（网关收口 / 令牌模式 / auth 白名单 / 权限注解冲突 / CI 工作目录），CI 作业 `internal-boundary`。

## 环境分层

| 环境 | 用途 | 触发 | 中间件 | 部署方式 | 审批 | 现状 |
|---|---|---|---|---|---|---|
| dev | 开发自测 | 手工 / main push | 本机 compose（`infrastructure/docker`） | Docker Compose | 无 | 已具备（本文件 §开发环境） |
| staging | 预发布验证 | tag 自动 | 独立 compose（数据隔离） | Compose / K8s | 无或轻量 | **未入库**（T-CI-8 待做） |
| prod | 生产 | tag + 人工 | 生产集群 | K8s（`docker-compose.prod.yml` 备选） | **必须人工审批** | 覆盖层已具备，未实际发布 |

- 配置隔离：`SPRING_PROFILES_ACTIVE=dev|staging|prod`；环境差异只允许出现在环境变量与配置中心，不允许在代码里判断环境。
- 环境矩阵的完整口径见 [design/cicd.md](design/cicd.md) §7；staging 落地前，预发布验证在 dev 完成并记录于波次报告。
- 数据库迁移**只向前**（Flyway `V*`），staging 与 prod 禁止手工改表；回滚靠应用版本回退 + 补偿脚本，不靠 `undo` 迁移。

## 密钥与配置

| 名称 | 用途 | 存放 | 约束 |
|---|---|---|---|
| `JWT_SECRET` | access / refresh 签名 | CI/CD Secrets 或环境变量 | auth 与 gateway **必须一致**；禁止硬编码与入库 |
| `WHALE_INTERNAL_TOKEN` | 服务间 `X-Internal-Token` | 各服务环境变量 | 所有后端服务必填；网关剥离外部同名头 |
| `MYSQL_PASSWORD` / `REDIS_PASSWORD` | 数据源与缓存 | Secrets | 不得写入 `application.yml` |
| `MYSQL_ROOT_PASSWORD` | compose 初始化 | `infrastructure/docker/.env`（**不提交**） | 仅本地开发 |
| `NACOS_AUTH_IDENTITY_*` / `NACOS_NAMESPACE` | 注册与配置中心身份 | Secrets / 环境变量 | 生产必须开启鉴权 |
| `GRAFANA_ADMIN_PASSWORD` | 监控面板 | Secrets | **未配置时 Grafana 启动失败**（无弱口令回退） |
| 第三方凭据（地图 Key、邮箱、支付、电子签） | 外部集成 | Secrets / `secretRef` | 预检作业单：[ops/external-integration-onboarding.md](ops/external-integration-onboarding.md) |

- 配置分工：**Nacos** 管服务装配配置（端口/数据源/路由），**`whale-config`** 管业务应用配置（版本历史 + 回滚），二者不混用。
- 本地 `.env` 由 `.env.example` 复制，禁止提交；新增变量必须同步补 `.env.example` 并在 [service-catalog.md](service-catalog.md) §八 登记。

## 日志、指标与链路

| 维度 | 机制 | 入口 |
|---|---|---|
| 链路追踪 | 网关生成 `X-Trace-Id` 并透传；限流/熔断响应同样带该头 | 各服务日志 MDC；`X-Trace-Id` 响应头 |
| 指标 | Spring Boot Actuator + Micrometer，`/actuator/prometheus`，统一 `application` 标签 | [ops/slo.md](ops/slo.md) |
| 抓取 | Prometheus `infrastructure/prometheus/prometheus-docker.yml` 抓宿主机 `whale-*` job | Prometheus `/targets` |
| 告警 | 规则 `deploy/monitoring/prometheus-rules.yml` → Alertmanager → `whale-alert` webhook | Prometheus `/alerts`、Grafana |
| 看板 | provisioning 自动装载 `bluewhale-overview` / `bluewhale-music` / `bluewhale-video` | Grafana 目录 `grafana/dashboards/`（只读挂载） |

## 告警阈值（`deploy/monitoring/prometheus-rules.yml`）

| 告警 | 表达式要点 | 持续时间 | 级别 |
|---|---|---|---|
| `ServiceDown` | `up{job=~"whale-.*\|nacos"} == 0` | 1m | critical |
| `TargetFlapping` | 10 分钟内 `up` 变化 > 4 次 | 5m | warning |
| `Http5xxRateHigh` | 服务 5xx 占比 > 5% | 5m | critical |
| `LoginP95Slow` | `/auth/login` P95 > 1.5s | 5m | warning |
| `DocSaveConflictSpike` | 文档保存冲突 > 1 次/秒 | 10m | warning |
| `DocSearchP95Slow` | 文档搜索 P95 > 1s | 10m | warning |
| `DocOrphanCleanupFailed` | 1 小时内孤儿附件清理失败批次 > 0 | 5m | warning |
| `JvmHeapHigh` | 堆使用率 > 85% | 5m | warning |

> SLO 目标值（如登录 P95 < 1500ms、表单提交成功率 ≥ 99%）见 [ops/slo.md](ops/slo.md)；阈值变更须同步本表与规则文件。

## 备份与恢复

### 已具备

| 对象 | 脚本 | 方式 | 校验 |
|---|---|---|---|
| `bluewhale_doc` | `scripts/ops/doc-backup-restore.ps1` | `mysqldump --single-transaction` | 恢复到 `*_restore_check` 临时库，校验表数量/全文索引/行数后删除 |
| `bluewhale_music` | `scripts/ops/music-backup-restore.ps1` | 同上 | 同上 |
| `bluewhale_video` | `scripts/ops/video-backup-restore.ps1` | 同上 | 同上 |
| 文件字节 | `whale-file` 存储插件（local / MinIO） | 依赖存储层自身备份/版本 | 与数据库备份**同一批次**校验 |

### 目标与缺口

- 目标（待演练兑现）：**RPO ≤ 24h、RTO ≤ 4h**（单库）；核心业务库（auth / workflow / OA / ERP / CRM）优先。
- 缺口：其余库尚无专用备份脚本（RSK-15）；全库统一备份与异地留存未脚本化。
- 演练步骤（以 doc 为例）：
  1. 设置 `MYSQL_PASSWORD`，执行脚本，确认退出码 `0`；
  2. 检查输出中的表数量、索引与页面行数与源库一致；
  3. 保留一次 `-KeepRestoreDatabase` 结果人工抽查关键表；
  4. 在 devlog 记录备份时间、库名、耗时、校验结论。
- **禁止**把恢复校验库命名为源库；脚本已内置该保护，不得绕过。

## 发布与回滚手册

1. **发布前**：`scripts/ci/bw-deploy-contract-check.ps1` 通过；目标镜像 tag 与 `deploy/ci/images.json` 一致；变更含迁移时确认脚本幂等且只向前。
2. **发布**：先 gateway 后业务服务；单服务启动后 `GET /actuator/health` 为 `UP` 再放流量。
3. **发布后**：网关烟测（登录 `code=0`、核心路由 200）、Prometheus `ServiceDown` 无新告警、5xx 占比无异常抬升。
4. **回滚**：应用镜像回退到上一 tag → 重新部署 → 复跑同一组烟测；**数据库不做自动回退**，如迁移不兼容需执行预先准备的补偿脚本。
5. **开关回滚**：优先使用已存在的开关降级（如 `whale.crm.demand-gate-passed=false` 回维护态、限流/熔断 `enabled=false`），再决定是否回退镜像。
6. **留痕**：发布与回滚均写入当日 devlog（时间、版本、验证结果、回滚原因）。

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
