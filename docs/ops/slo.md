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
