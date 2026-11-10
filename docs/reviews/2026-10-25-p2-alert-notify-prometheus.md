# P2 告警通知渠道 + Prometheus 接入检查报告（2026-10-25）

> 对应台账：[priority-execution-ledger-2026-10-25.md](../plans/priority-execution-ledger-2026-10-25.md) 序号 8（P2）
> 对应需求：REQ-ARCH-007（登记册 2026-10-25 新增）
> 目标：告警通知至少邮件 + Webhook；Prometheus 可抓取/存储并打通 Alertmanager 到告警中心，运维端可查看。

---

## 一、链路与设计

```
Prometheus 规则命中 → Alertmanager → POST /alert/internal/prometheus（Bearer 令牌）
  → whale-alert 落 alert_record（FIRING / RECOVERED）
  → 命中启用规则且 firing 时按 rule.channel 发通知（EMAIL / WEBHOOK）
  → whale-frontend/ops 告警页读取 /alert/record/page 展示
```

- **入口仅内网**：`/alert/internal/**` 加入网关 `internal-deny`，外部一律 403；服务端再校验
  `X-Alert-Token` 或 `Authorization: Bearer`，与 `whale.alert.notify.webhook-token` 常量时间比较。
- **通知不阻塞落库**：通道异常只记日志，不回滚记录、不影响入站返回。
- **目标地址全局化**：规则 `channel` 选择通道，邮件收件人 / Webhook 地址由全局配置提供，避免逐规则维护。
- **无新表**：复用 `alert_rule.channel` 与 `alert_record`，不引入迁移。

## 二、变更清单

| 文件 | 变更 |
|---|---|
| [AlertNotifyProperties.java](../../whale-backend/whale-ops/whale-alert/src/main/java/com/bluewhale/alert/config/AlertNotifyProperties.java) | 新增 `whale.alert.notify` 配置（邮件/Webhook/入站令牌） |
| [AlertNotifyService.java](../../whale-backend/whale-ops/whale-alert/src/main/java/com/bluewhale/alert/service/AlertNotifyService.java) / [Impl](../../whale-backend/whale-ops/whale-alert/src/main/java/com/bluewhale/alert/service/impl/AlertNotifyServiceImpl.java) | 邮件（`JavaMailSender` + `ObjectProvider` 缺失降级）与 Webhook（`HttpClient` + JSON）发送；`SMS` 跳过、`NONE` 不通知 |
| [PrometheusWebhookPayload.java](../../whale-backend/whale-ops/whale-alert/src/main/java/com/bluewhale/alert/dto/PrometheusWebhookPayload.java) | Alertmanager v4 请求体子集 |
| [AlertRecordService.java](../../whale-backend/whale-ops/whale-alert/src/main/java/com/bluewhale/alert/service/AlertRecordService.java) / [Impl](../../whale-backend/whale-ops/whale-alert/src/main/java/com/bluewhale/alert/service/impl/AlertRecordServiceImpl.java) | 新增 `ingest`：标签/注解映射、级别归一（warning→WARN）、RFC3339 时间解析、内容截断 512、firing 命中启用规则才通知 |
| [AlertPrometheusController.java](../../whale-backend/whale-ops/whale-alert/src/main/java/com/bluewhale/alert/controller/AlertPrometheusController.java) | `POST /alert/internal/prometheus`，双令牌头支持 |
| [application.yml](../../whale-backend/whale-ops/whale-alert/src/main/resources/application.yml) | 增加通知配置与环境变量默认值 |
| [whale-gateway/application.yml](../../whale-backend/whale-services/whale-gateway/src/main/resources/application.yml) | `internal-deny` 增加 `/alert/internal/**` |
| [deploy/monitoring/prometheus.yml](../../deploy/monitoring/prometheus.yml) | 版本化抓取配置：19 个 target（全服务 + Nacos）+ 规则文件 + Alertmanager |
| [deploy/monitoring/alertmanager.yml](../../deploy/monitoring/alertmanager.yml) | Webhook 路由到 whale-alert，`send_resolved: true`，Bearer 令牌 |
| [docs/ops/slo.md](../ops/slo.md) | 新增「Alertmanager 与告警中心闭环」章节、配置项、联调命令、边界 |
| [Monitor.tsx](../../whale-frontend/ops/src/pages/Monitor.tsx) | 服务概览卡增加 Prometheus / Grafana 外链（`VITE_PROMETHEUS_URL` / `VITE_GRAFANA_URL`） |
| [AlertNotifyServiceImplTest.java](../../whale-backend/whale-ops/whale-alert/src/test/java/com/bluewhale/alert/service/impl/AlertNotifyServiceImplTest.java) | 邮件发送、邮件禁用、Webhook POST（JDK `HttpServer`）、NONE 跳过 4 例 |
| [AlertRecordServiceImplTest.java](../../whale-backend/whale-ops/whale-alert/src/test/java/com/bluewhale/alert/service/impl/AlertRecordServiceImplTest.java) | firing 落库+通知、resolved 落库不通知、缺 alertname 跳过 3 例 |
| [AlertPrometheusControllerTest.java](../../whale-backend/whale-ops/whale-alert/src/test/java/com/bluewhale/alert/controller/AlertPrometheusControllerTest.java) | 双令牌头放行、缺失/错误令牌 403 3 例 |

## 三、配置项

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `ALERT_WEBHOOK_TOKEN` | `bluewhale-alert-token` | 入站校验令牌；生产必须覆盖，并与 alertmanager.yml 一致 |
| `ALERT_EMAIL_ENABLED` | `false` | 邮件通道开关 |
| `ALERT_EMAIL_FROM` | 空 | 发件人；为空时跳过 |
| `ALERT_EMAIL_TO` | 空 | 收件人，逗号分隔 |
| `SPRING_MAIL_HOST` / `USERNAME` / `PASSWORD` | 未配置 | 未配置时无 `JavaMailSender`，邮件自动跳过 |
| `ALERT_WEBHOOK_ENABLED` | `false` | 出站 Webhook 开关 |
| `ALERT_WEBHOOK_URL` | 空 | 出站 Webhook 地址 |
| `ALERT_WEBHOOK_TIMEOUT` | `3000` | 出站超时（毫秒） |
| `VITE_PROMETHEUS_URL` | `http://127.0.0.1:9090` | ops 外链 |
| `VITE_GRAFANA_URL` | `http://127.0.0.1:3000` | ops 外链 |

## 四、验证记录

| 项 | 命令 | 结果 |
|---|---|---|
| whale-alert 单测 | `mvn -pl whale-ops/whale-alert test` | **通过，20 用例，0 失败** |
| whale-alert Checkstyle | `mvn -pl whale-ops/whale-alert checkstyle:check` | **0 violation** |
| whale-gateway 构建 | `mvn -pl whale-services/whale-gateway -DskipTests package` | **通过**（配置加载正常） |
| ops 前端构建 | `pnpm build`（whale-frontend/ops） | **通过**（tsc + vite，2095 modules） |
| ops 前端 lint | `pnpm lint`（whale-frontend/ops，`--max-warnings 0`） | **通过** |
| `promtool check config` | 未执行 | 本机 `tools/monitoring` 无 promtool，未实机校验 |
| Alertmanager → whale-alert 实机联调 | 未执行 | 需启动 MySQL + Prometheus + Alertmanager + whale-alert |

## 五、边界与残留

1. **去重**：未按 `fingerprint` 去重，Alertmanager 重复通知追加记录（保留历史）；如需去重需加 `fingerprint` 列与唯一键。
2. **租户归属**：入站无 JWT / 租户头，记录归属默认租户 `tenant_id=1`；多租户告警隔离需后续在 payload 增加租户标签。
3. **SMS**：`alert_rule.channel=SMS` 仍未接入，仅记日志跳过。
4. **邮件模板**：纯文本；HTML 模板与多收件人分组未做。
5. **Prometheus 抓取地址**：版本化配置默认 `127.0.0.1`，Docker 部署需替换为 `host.docker.internal`（已在文件头注明）。
6. **前端外链**：仅跳转，不在 ops 内代理 Prometheus/Grafana API；如需内嵌面板需网关反代与鉴权。
