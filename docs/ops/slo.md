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

## SLO-4 文档保存时延：P95 < 500ms

```promql
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/doc/page/*",method="PUT",application="whale-doc"}[5m])))
```

## SLO-5 文档搜索时延：P95 < 1000ms

```promql
histogram_quantile(0.95, sum by (le) (rate(doc_search_seconds_bucket{application="whale-doc"}[5m])))
```

`doc_search_seconds` 为业务计时器，按 `mode="fulltext"` / `mode="like"` 区分 ngram 索引与短关键词回退；
保存冲突另见 `doc_page_save_conflict_total`。

## SLO-6 音乐首页时延：P95 < 300ms

```promql
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/music/home",application="whale-music"}[5m])))
```

## SLO-7 歌曲详情时延：P95 < 200ms

```promql
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/music/public/song/{id}",application="whale-music"}[5m])))
```

## SLO-8 流式首字节时延：P95 < 300ms

```promql
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/music/stream/{songId}",application="whale-music"}[5m])))
```

- 播放链路口径：`POST /music/play/ticket` 签发绑定 `租户 + 用户 + 歌曲` 的票据（TTL 120s，可配置），
  `GET /music/stream/{songId}?ticket=...` 每次开流都复核可见性与版权状态，再以 `InputStreamResource`
  代理 whale-file `/file/internal/{id}/content`，因此该端点耗时即「校验 + 开流 + 首字节」；
  上游内容接口的耗时计入本端点直方图，`whale-file` 侧同名 URI（`uri="/file/internal/{id}/content"`）可用于拆分两跳。
- 容量口径：SLO-8 的 50 并发验收见 `scripts/smoke/bw-music-m5-load.ps1`；
  该脚本直连 8096（不经网关），并发探针用共享 HttpClient + 异步栅栏 + 每请求独立计时。

## SLO-9 视频首页时延：P95 < 300ms

```promql
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/video/home",application="whale-video"}[5m])))
```

## SLO-10 内容详情时延：P95 < 250ms

```promql
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/video/content/{id}",application="whale-video"}[5m])))
```

## SLO-11 视频流首字节时延：P95 < 400ms

```promql
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/video/stream/{contentId}",application="whale-video"}[5m])))
```

- 播放链路口径：`POST /video/play/ticket` 签发绑定 `租户 + 用户 + 内容` 的票据（TTL 300s，
  `refreshIn=240s` 提示客户端续票），`GET /video/stream/{contentId}?ticket=...` 每次开流都复核
  票据三要素、可见性、发布状态与版权状态，再以 `InputStreamResource` 代理 whale-file 内容接口，
  因此该端点耗时即「校验 + 开流 + 首字节」；上游 `whale-file` 同名 uri 可用于拆分两跳。
- 分段口径：`/video/stream/**` 支持 200 / 206 / 416，响应固定 `Cache-Control: private, no-store`
  与 `X-Content-Type-Options: nosniff`；长视频一次播放会跨多个 Range 请求，故网关对该路由
  不加熔断（`circuit-breaker.apply-to-routes` 不含 `/video/stream/**`）。
- 容量口径：SLO-11 的 50 并发验收见 `scripts/smoke/bw-video-m5-load.ps1`（造数 → 顺序 P95 →
  50 并发首字节 P95 多轮取中位）；网关六条视频限流（stream 120 / ticket 60 / comment 20 /
  like 30 / favorite 30 / upload 20，每 60s）单独见 `scripts/smoke/bw-video-gateway-rate-limit-smoke.ps1`。
- 本机口径：`whale-video` 已开启 `management.metrics.distribution.percentiles-histogram.[http.server.requests]=true`，
  `/video/meta`、`/video/home`、`/video/content/{id}`、`/video/stream/{contentId}` 均输出
  `http_server_requests_seconds_bucket`，SLO-9/10/11 可直接 `histogram_quantile`。

## 采集前提

- 服务侧 `management.endpoints.web.exposure.include` 至少包含 `health,info,prometheus`。
- `management.metrics.tags.application=${spring.application.name}`，便于按服务聚合。
- Prometheus 抓取目标为各服务 `http://<host>:<port>/actuator/prometheus`。
- P95 查询依赖直方图桶 `http_server_requests_seconds_bucket`。该桶默认不输出，已开启直方图的服务为
  **whale-auth / whale-workflow / whale-doc / whale-music / whale-video**（`management.metrics.distribution.percentiles-histogram.[http.server.requests]=true`）；
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

whale-music 开启直方图后（2026-10-31 实测），`http_server_requests_seconds_bucket{application="whale-music"}`
已按 `uri="/music/home"`、`uri="/music/public/song/{id}"`、`uri="/music/stream/{songId}"` 输出，可直接用于 SLO-6/7/8。

本机口径参考（8 核，单机同时承载 6 个 JVM + MySQL + Redis + Nacos，`scripts/smoke/bw-music-m5-load.ps1`）：

| 指标 | 实测 | 阈值 |
| --- | --- | --- |
| 首页 `/music/home` 顺序 P95 | 4.8–8.6ms | < 300ms |
| 详情 `/music/public/song/{id}` 顺序 P95 | 5.3–8.8ms | < 200ms |
| 流式 `/music/stream/{songId}` 50 并发首字节 P95（多轮中位） | 216–303ms | < 300ms |

单机压测时客户端与 6 个 JVM 抢同一批 CPU，客户端观测值里含大量本机排队；
生产验收以 Prometheus 直方图（SLO-6/7/8）与 Grafana「音乐平台监控」面板为准。

## 本地运行 Prometheus + Grafana（非 Docker）

二进制与运行数据均在 `tools/monitoring`（`/tools/` 已在 `.gitignore`）：

```powershell
$base='D:/BlueWhale/BlueWhale/tools/monitoring'
Start-Process .\tools\monitoring\prometheus\prometheus-3.13.5.windows-amd64\prometheus.exe `
  -ArgumentList "--config.file=$base/prometheus.local.yml","--storage.tsdb.path=$base/data/prometheus","--web.listen-address=127.0.0.1:9090"
Start-Process .\tools\monitoring\grafana-13.2.4\bin\grafana.exe `
  -ArgumentList 'server',"--homepath=D:/BlueWhale/BlueWhale/tools/monitoring/grafana-13.2.4","--config=$base/grafana.ini"
```

- Prometheus：`http://127.0.0.1:9090`，抓取目标见 `tools/monitoring/prometheus.local.yml`（8080–8090 / 8093–8095 / 9001–9004 / 9101–9103 / 8848；`whale-config` 已冻结，9201 不再抓取）。
- Grafana：`http://127.0.0.1:3000`，数据源 `Prometheus` 由 `grafana-provisioning` 自动注入，默认 `admin/admin`，仅监听回环地址。
- 后端服务未启动时目标显示 `down` 属正常，启动后查询 `up` 即可确认。

## 告警规则（Prometheus，版本化）

- 规则文件：`deploy/monitoring/prometheus-rules.yml`，共 8 条，按 `availability / http / doc / jvm` 四组。
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
| DocSaveConflictSpike | 文档保存冲突速率 > 1/s | 10m | warning |
| DocSearchP95Slow | 文档搜索 P95 > 1s（SLO-5） | 10m | warning |
| DocOrphanCleanupFailed | 孤儿附件引用清理失败批次 > 0 | 5m | warning |
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
- **运维展示**：`whale-frontend/ops` 监控页顶部提供 Prometheus / Grafana 外链
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

- 仪表盘 JSON：`deploy/monitoring/grafana/dashboards/bluewhale-overview.json`（uid `bluewhale-overview`，标题「BlueWhale 平台监控总览」）；
  音乐平台专项 `bluewhale-music.json`（uid `bluewhale-music`，标题「音乐平台监控（Wave 5.75）」）；
  视频平台专项 `bluewhale-video.json`（uid `bluewhale-video`，标题「视频平台监控（Wave 5.77）」）。
  Docker 侧同源副本位于 `infrastructure/prometheus/grafana/dashboards/`（数据源 uid 为 `bluewhale-prometheus`）。
- 自动加载：`tools/monitoring/grafana-provisioning/dashboards/bluewhale.yml` 指向上述目录，30s 轮询，文件夹 `BlueWhale`。
- 数据源：`Prometheus`，uid `prometheus`（`grafana-provisioning/datasources/prometheus.yml`）。
- 访问：`http://127.0.0.1:3000/d/bluewhale-overview`（本地 admin/admin，仅回环监听）。
- 面板（13 个 + 2 个文档专项）：存活抓取目标、登录 P95（SLO-1）、5xx 错误率、审计上报 QPS、HTTP QPS（按服务）、5xx 错误率（按服务）、P95/P99 时延、JVM 堆使用率、GC 暂停速率、抓取目标存活明细、文档保存冲突速率、文档搜索 P95（SLO-5）。

## 本地监控闭环验证（2026-10-23）

- 启动组合：MySQL 3306 + Redis 6379 + Nacos 8848 + whale-gateway 8080 + whale-auth 8081 + whale-audit 9101 + Prometheus 9090 + Grafana 3000。
- 抓取结果：`whale-gateway` / `whale-auth` / `whale-ops(9101)` 三个目标 `up=1`；其余 15 个未启动目标 `down` 并触发 `ServiceDown`（本地按需启动，属预期）。
- 实测指标：登录 P95 ≈ 0.153s；平台总 QPS ≈ 0.42；审计上报 QPS ≈ 0.086；whale-auth 堆使用率 ≈ 18%。
- 校验方式：`/api/v1/rules`（3 组 5 条）、`/api/v1/alerts`（15 条 ServiceDown FIRING）、Grafana `/api/dashboards/uid/bluewhale-overview`（16 panels）、Grafana `/api/ds/query`（返回 0.389）。
- 已知问题与处理：Grafana 首次启动报 `Datasource provisioning error: data source not found`（旧 `grafana.db` 数据源 uid 与新增 `uid: prometheus` 冲突），备份 `grafana.db` 后重建即恢复；旧库已保留为 `grafana.db.bak-20261023`。
