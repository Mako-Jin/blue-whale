# 可观测性 SLO（P1-3b）

指标来源为 Spring Boot Actuator + Micrometer Prometheus Registry，各服务通过
`/actuator/prometheus` 暴露，统一附加 `application` 标签（值为 `spring.application.name`）。

## SLO-1 登录时延：P95 < 1500ms

```promql
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/auth/login",application="whale-auth"}[5m])))
```

## SLO-2 表单提交成功率：≥ 99%

```promql
sum(rate(http_server_requests_seconds_count{uri="/form/data",method="POST",status!~"5..",application="whale-form"}[5m]))
/
sum(rate(http_server_requests_seconds_count{uri="/form/data",method="POST",application="whale-form"}[5m]))
```

## SLO-3 审批流转时延：P95 < 1000ms

```promql
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri=~"/workflow/task/.*",application="whale-workflow"}[5m])))
```

## 采集前提

- 服务侧 `management.endpoints.web.exposure.include` 至少包含 `health,info,prometheus`。
- `management.metrics.tags.application=${spring.application.name}`，便于按服务聚合。
- Prometheus 抓取目标为各服务 `http://<host>:<port>/actuator/prometheus`。
- P95 查询依赖直方图桶 `http_server_requests_seconds_bucket`。该桶默认不输出，SLO-1/SLO-3 的归属服务
  **whale-auth / whale-workflow** 已开启 `management.metrics.distribution.percentiles-histogram.[http.server.requests]=true`；
  其余服务保持默认（不打桶，避免指标基数膨胀），如需新增时延 SLO 再按服务开启。

## 实测

验证日期：2026-10-22，本机重启新构建的 gateway / auth jar 后抓取：

| 端点 | HTTP 状态 |
| --- | --- |
| `http://127.0.0.1:8081/actuator/prometheus`（whale-auth） | 200 |
| `http://127.0.0.1:8080/actuator/prometheus`（whale-gateway） | 200 |

实际指标命名（Boot 4.0.8 / Micrometer 1.16.7）：

- `http_server_requests_seconds_count`
- `http_server_requests_seconds_sum`
- `http_server_requests_seconds_max`

标签包含 `application`、`method`、`status`、`outcome`、`error`、`exception`、`uri`，
`application` 值与 `spring.application.name` 一致。实际为 `_seconds`（非 `_milliseconds`）。

whale-auth / whale-workflow 开启直方图后，实测已输出 `http_server_requests_seconds_bucket`，
例如登录请求：

```
http_server_requests_seconds_bucket{application="whale-auth",method="POST",status="200",uri="/auth/login",le="0.001"} 0
```

故 SLO-1 / SLO-3 的 `histogram_quantile` 查询可直接使用。

## 本地运行 Prometheus + Grafana（非 Docker）

二进制与运行数据均在 `tools/monitoring`（`/tools/` 已在 `.gitignore`）：

```powershell
$base='D:/BlueWhale/BlueWhale/tools/monitoring'
Start-Process .\tools\monitoring\prometheus\prometheus-3.13.5.windows-amd64\prometheus.exe `
  -ArgumentList "--config.file=$base/prometheus.local.yml","--storage.tsdb.path=$base/data/prometheus","--web.listen-address=127.0.0.1:9090"
Start-Process .\tools\monitoring\grafana-13.2.4\bin\grafana.exe `
  -ArgumentList 'server',"--homepath=D:/BlueWhale/BlueWhale/tools/monitoring/grafana-13.2.4","--config=$base/grafana.ini"
```

- Prometheus：`http://127.0.0.1:9090`，抓取目标见 `tools/monitoring/prometheus.local.yml`（8080–8090 / 9001–9004 / 9101–9103 / 8848；`whale-config` 已冻结，9201 不再抓取）。
- Grafana：`http://127.0.0.1:3000`，数据源 `Prometheus` 由 `grafana-provisioning` 自动注入，默认 `admin/admin`，仅监听回环地址。
- 后端服务未启动时目标显示 `down` 属正常，启动后查询 `up` 即可确认。

## 告警规则（Prometheus，版本化）

- 规则文件：`deploy/monitoring/prometheus-rules.yml`，共 5 条，按 `availability / http / jvm` 三组。
- 抓取 + Alertmanager 配置：`deploy/monitoring/prometheus.yml`、`deploy/monitoring/alertmanager.yml`。
- 本地由 `tools/monitoring/prometheus.local.yml` 的 `rule_files` 引用；也可直接用版本化的
  `deploy/monitoring/prometheus.yml` 启动（`prometheus --config.file=deploy/monitoring/prometheus.yml`）。
- 规则清单：

| 规则 | 表达式要点 | for | 级别 |
| --- | --- | --- | --- |
| ServiceDown | `up{job=~"whale-.*\|nacos"} == 0` | 1m | critical |
| TargetFlapping | `changes(up{job=~"whale-.*"}[10m]) > 4` | 5m | warning |
| Http5xxRateHigh | 5xx 占比 > 5%（按 application） | 5m | critical |
| LoginP95Slow | 登录 P95 > 1.5s（SLO-1） | 5m | warning |
| JvmHeapHigh | 堆使用率 > 85%（按 application） | 5m | warning |

- 校验与查看：

```powershell
.\tools\monitoring\prometheus\prometheus-3.13.5.windows-amd64\promtool.exe check rules .\deploy\monitoring\prometheus-rules.yml
curl http://127.0.0.1:9090/api/v1/rules
curl http://127.0.0.1:9090/api/v1/alerts
```

- 热加载配置：Prometheus 启动参数已加 `--web.enable-lifecycle`，改规则后执行 `curl -X POST http://127.0.0.1:9090/-/reload`。

## Alertmanager 与告警中心闭环（2026-10-25，REQ-ARCH-007）

链路：`Prometheus 规则命中 → Alertmanager → whale-alert /alert/internal/prometheus → alert_record → 通知渠道`。

- **Prometheus 抓取**：`deploy/monitoring/prometheus.yml` 覆盖全部在役服务
  （8080–8086 / 8090 / 8091 / 8093 / 8094 / 9001–9004 / 9101–9103 / 8848；`whale-config` 冻结后不再抓取 9201），
  `job` 名均以 `whale-` 开头以匹配规则；Docker 部署时把 `127.0.0.1` 换成 `host.docker.internal`。
- **Alertmanager**：`deploy/monitoring/alertmanager.yml` 路由到
  `http://127.0.0.1:9103/alert/internal/prometheus`，`send_resolved: true`，
  令牌通过 `Authorization: Bearer <token>` 传递。
- **后端入站**：`whale-alert` 的 `AlertPrometheusController` 校验 `X-Alert-Token`
  或 `Authorization: Bearer`，与 `whale.alert.notify.webhook-token`（环境变量
  `ALERT_WEBHOOK_TOKEN`，本地默认 `bluewhale-alert-token`）常量时间比较；
  `firing` 落 `FIRING`、`resolved` 落 `RECOVERED`，网关对 `/alert/internal/**` 统一 403。
- **通知渠道**（规则 `channel` 选择，目标地址为全局配置）：
  - `EMAIL`：`ALERT_EMAIL_ENABLED=true` + `ALERT_EMAIL_FROM` + `ALERT_EMAIL_TO`（逗号分隔），
    并配置 `SPRING_MAIL_HOST` / `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` 以创建 `JavaMailSender`；
    未配置时自动跳过并记日志。
  - `WEBHOOK`：`ALERT_WEBHOOK_ENABLED=true` + `ALERT_WEBHOOK_URL`，POST JSON
    （`ruleCode / ruleName / metric / target / level / status / content / triggerTime`）。
  - `SMS` 暂未接入，记录告警日志后跳过；`NONE` 不通知。
  - 通知失败只记日志，不回滚记录，也不影响入站接口返回。
- **运维展示**：`frontend/ops` 监控页顶部提供 Prometheus / Grafana 外链
  （`VITE_PROMETHEUS_URL`、`VITE_GRAFANA_URL`，默认 `http://127.0.0.1:9090` / `http://127.0.0.1:3000`）；
  告警页“告警记录”直接展示入站落库结果。
- **验证入口**：

```powershell
# 规则与配置校验
promtool check rules deploy/monitoring/prometheus-rules.yml
promtool check config deploy/monitoring/prometheus.yml
# 手工模拟 Alertmanager 入站（需服务已启动）
curl -X POST http://127.0.0.1:9103/alert/internal/prometheus `
  -H "X-Alert-Token: bluewhale-alert-token" -H "Content-Type: application/json" `
  -d '{"version":"4","status":"firing","alerts":[{"status":"firing","labels":{"alertname":"ServiceDown","severity":"critical"},"annotations":{"summary":"手工联调"},"startsAt":"2026-10-25T10:00:00+08:00"}]}'
```

> 已知边界：入站告警未做按 `fingerprint` 去重，Alertmanager 重复通知会追加记录（保留告警历史）；
> 入站无 JWT / 租户头，记录归属默认租户（`tenant_id=1`）。

## Grafana 仪表盘（版本化）

- 仪表盘 JSON：`deploy/monitoring/grafana/dashboards/bluewhale-overview.json`（uid `bluewhale-overview`，标题「BlueWhale 平台监控总览」）。
- 自动加载：`tools/monitoring/grafana-provisioning/dashboards/bluewhale.yml` 指向上述目录，30s 轮询，文件夹 `BlueWhale`。
- 数据源：`Prometheus`，uid `prometheus`（`grafana-provisioning/datasources/prometheus.yml`）。
- 访问：`http://127.0.0.1:3000/d/bluewhale-overview`（本地 admin/admin，仅回环监听）。
- 面板（11 个）：存活抓取目标、登录 P95（SLO-1）、5xx 错误率、审计上报 QPS、HTTP QPS（按服务）、5xx 错误率（按服务）、P95/P99 时延、JVM 堆使用率、GC 暂停速率、抓取目标存活明细。

## 本地监控闭环验证（2026-10-23）

- 启动组合：MySQL 3306 + Redis 6379 + Nacos 8848 + whale-gateway 8080 + whale-auth 8081 + whale-audit 9101 + Prometheus 9090 + Grafana 3000。
- 抓取结果：`whale-gateway` / `whale-auth` / `whale-ops(9101)` 三个目标 `up=1`；其余 15 个未启动目标 `down` 并触发 `ServiceDown`（本地按需启动，属预期）。
- 实测指标：登录 P95 ≈ 0.153s；平台总 QPS ≈ 0.42；审计上报 QPS ≈ 0.086；whale-auth 堆使用率 ≈ 18%。
- 校验方式：`/api/v1/rules`（3 组 5 条）、`/api/v1/alerts`（15 条 ServiceDown FIRING）、Grafana `/api/dashboards/uid/bluewhale-overview`（16 panels）、Grafana `/api/ds/query`（返回 0.389）。
- 已知问题与处理：Grafana 首次启动报 `Datasource provisioning error: data source not found`（旧 `grafana.db` 数据源 uid 与新增 `uid: prometheus` 冲突），备份 `grafana.db` 后重建即恢复；旧库已保留为 `grafana.db.bak-20261023`。
