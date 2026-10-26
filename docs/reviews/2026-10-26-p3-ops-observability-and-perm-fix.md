# P3 运维偏差收口（可观测性 Compose + 端口/注册中心变量 + MES 权限码）—— 检查报告

- 日期：2026-10-26
- 来源：`docs/service-catalog.md` §九 偏差 C-02/C-04/C-05/C-07 与实机回归中暴露的 `/auth/me` 400
- 结论：**4 项偏差已闭合（C-07 的 Sentinel 除外），并修复 1 个由 MES 权限码播种引入的平台缺陷**

## 一、范围

1. C-02：`whale-auth` 端口外置（硬编码 `8081` → `${SERVER_PORT:8081}`）。
2. C-04：Nacos 版本口径统一（以本机实装 **2.4.0** 为准，compose 镜像已是 `nacos/nacos-server:v2.4.0`）。
3. C-05：注册中心环境变量收敛（网关 `NACOS_ADDR` vs 其余 `NACOS_SERVER`）。
4. C-07：compose 补齐 **Prometheus / Alertmanager / Grafana**（Sentinel 仍为规划）。
5. 缺陷修复：MES 权限码 `sys:mes:work-order:*` 违反 `PermCode` 正则，导致登录后 `/auth/me` 恒 `code=400`。

## 二、变更点

### 运维编排（`infrastructure`）

- `infrastructure/docker/docker-compose.yml` 新增 3 个服务与 3 个命名卷：
  - `bw-prometheus`：`prom/prometheus:v2.54.1`，端口 `${PROMETHEUS_HOST_PORT:-9090}`，保留期 `${PROMETHEUS_RETENTION:-15d}`，挂载 docker 版抓取配置与 `deploy/monitoring/prometheus-rules.yml`，`extra_hosts: host.docker.internal:host-gateway`。
  - `bw-alertmanager`：`prom/alertmanager:v0.27.0`，端口 `${ALERTMANAGER_HOST_PORT:-9093}`，webhook 指向宿主机 `whale-alert` 9103。
  - `bw-grafana`：`grafana/grafana:11.2.0`，端口 `${GRAFANA_HOST_PORT:-3000}`，provisioning 自动装配 Prometheus 数据源，`depends_on: prometheus`。
- 新增 `infrastructure/prometheus/prometheus-docker.yml`：job 名与 `deploy/monitoring/prometheus.yml` 保持一致（`whale-*` / `nacos`），targets 改 `host.docker.internal:<port>`，新增 whale-contract/erp/mes/pdm/oa/config 等 19 个 job，`alerting` 指向 `alertmanager:9093`。
- 新增 `infrastructure/prometheus/alertmanager-docker.yml`：webhook `http://host.docker.internal:9103/alert/internal/prometheus`，Bearer 令牌（生产须替换）。
- 新增 `infrastructure/prometheus/grafana/provisioning/datasources/prometheus.yml`：默认数据源 `http://prometheus:9090`。
- `docker-compose.prod.yml` 补 3 个服务的资源限制（prometheus 1c/1g、alertmanager 0.5c/256m、grafana 1c/512m）。
- `.env.example` 补 `PROMETHEUS_HOST_PORT` / `ALERTMANAGER_HOST_PORT` / `GRAFANA_HOST_PORT` / `PROMETHEUS_IMAGE` / `ALERTMANAGER_IMAGE` / `GRAFANA_IMAGE` / `PROMETHEUS_RETENTION` / `GRAFANA_ADMIN_PASSWORD`。

### 端口与注册中心变量

- `whale-auth/application.yml`：`server.port: ${SERVER_PORT:8081}`。
- `whale-auth` / `whale-gateway` 的 Nacos 地址统一为 `${NACOS_SERVER:${NACOS_ADDR:127.0.0.1:8848}}`（首选 `NACOS_SERVER`，保留 `NACOS_ADDR` 回退）。
- `scripts/dev/whale-start-all.ps1`：网关启动改用 `NACOS_SERVER`。

### MES 权限码修复

- 根因：`PermCode` 正则 `^[a-z][a-z0-9]*(?::[a-z0-9]+)+$` **不允许连字符**；`migration-5.59-mes.sql` 播种的 207–210 与菜单 26 使用 `work-order`，`PermissionPersistenceAssembler.toDomain` 组装权限时抛 `PARAM_ERROR` → `/auth/me` HTTP 200 但 `code=400`，登录后前端拿不到权限。
- 修正：
  - `migration-5.59-mes.sql`（新装口径）改为 `sys:mes:workorder:list/add/edit/delete`。
  - 新增幂等 `migration-5.64-perm-code-format.sql`：仅更新仍为旧值的 207–210 与菜单 26（`sys_role_permission` 按 `permission_id` 关联，授权不受影响）。
  - `MesWorkOrderController` 4 处 `@RequirePermission` 与类注释、`whale-mes/application.yml` 注释同步。
  - 文档同步：MES 检查报告、Phase 4 规划表。
- 复核 SQL：`bluewhale_auth.sys_permission` 与 `sys_menu.permission_code` 全表按正则 `^[a-z][a-z0-9]*(:[a-z0-9]+)+$`（大小写敏感）校验，违规行 **0**（修复前 5 行：207–210 + 菜单 26）。

## 三、验证

### 编排与配置

- `docker compose --env-file .env.example config --quiet`：**dev OK**；叠加 `docker-compose.prod.yml`：**prod overlay OK**。
- `promtool check config`（挂载真实布局）：**SUCCESS**；`amtool check-config`：**SUCCESS**。
- `docker compose up -d prometheus alertmanager grafana` 后三容器 healthy：
  - Prometheus `/-/ready` → 200；
  - Alertmanager `/-/ready` → 200；
  - Grafana `/api/health` → `{"database":"ok",...}`；`/api/datasources` 返回默认数据源 `BlueWhale Prometheus`。
- Prometheus targets（`/api/v1/targets`）：`whale-gateway` / `whale-auth` / `whale-workflow` / `whale-contract` 经 `host.docker.internal` 抓取 **up**（容器 → 宿主机链路验证通过）；`whale-monitor` / `whale-alert` 当时未运行，显示 down 属预期。
- 镜像拉取：Docker Hub 直连失败，按 `.env.example` 既有镜像站口径经 `docker.m.daocloud.io` 拉取后本地 tag，未改动仓库默认镜像名。

### 服务重启与回归

- `mvn -o -pl backend/whale-services/whale-gateway,backend/whale-services/whale-auth -am package`：**BUILD OK**；两服务以 `SERVER_PORT=8080/8081` 启动，`/actuator/health/readiness` 均 `{"status":"UP"}`（嵌套占位符 `${NACOS_SERVER:${NACOS_ADDR:...}}` 解析正常，Nacos 注册成功）。
- `whale-mes` 改注解后重打包（BUILD OK）并重启 9002，readiness UP。
- 经网关 8080 回归：`POST /auth/login` `code=0`；`GET /auth/me` **`code=0`，perms=172，menus=11**（修复前 `code=400`）；`GET /mes/work-order/page` **`code=0`，total=2**。

### 环境侧修正（不入仓）

- 用户级环境变量 `SPRING_DATA_REDIS_PASSWORD` 为过期的 2 字符值，导致 auth 启动 Redisson `AUTH` 失败；已按本机 `redis.windows.conf` 的实际 `requirepass` 重置（值不回显、不入仓）。

## 四、风险与观察

- **Grafana 口令**：`.env.example` 的 `GRAFANA_ADMIN_PASSWORD` 仅供本机/内网，生产必须替换；Alertmanager webhook 的 Bearer 令牌同理。
- **抓取拓扑**：容器版假设后端服务跑在宿主机（`host.docker.internal`）。若后续把业务服务也容器化，需改为 `bw-net` 服务名并同步 `prometheus-docker.yml`。
- **规则文件复用**：规则来自 `deploy/monitoring/prometheus-rules.yml`，裸机与容器共用一份，避免漂移；抓取地址是唯一差异点。
- **Sentinel**：C-07 剩余项（Dashboard 8858 + 网关限流）未引入，保持规划状态。
- **权限码播种校验**：本次为「种子绕过值对象校验」类问题的第二次复发（前次 `resetPwd` / `bpmnImport`）。已记录约定：新增权限码必须匹配 `^[a-z][a-z0-9]*(:[a-z0-9]+)+$`，禁止连字符。

## 五、结论

C-02/C-04/C-05/C-07（Prometheus/Grafana/Alertmanager 部分）已闭合，MES 权限码缺陷已修复并经网关实测 `/auth/me` 与 MES 分页恢复；可观测性栈首次具备「抓取 → 告警路由 → 看板」的可运行闭环。
