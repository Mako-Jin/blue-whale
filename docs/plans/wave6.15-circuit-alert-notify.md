# Wave 6.15 · 网关熔断状态告警上报

- 日期：2026-11-11
- 目标：熔断从「只有日志和指标」升级为「打开时主动告警」——运维不需要盯着
  `gateway.circuit.state` 指标或日志，whale-alert 落记录并触发通知。
- 结论：**实机全链验证通过**（打开 ERROR 记录 + 恢复 INFO 记录均落库）。

## 一、方案

不引入 Prometheus/Alertmanager 栈（8G VM 扛不住，且 alert 服务已有完整通道）：
网关在熔断**状态迁移**时自报 whale-alert 内网接口 `POST /alert/internal/record`
（与 iot 的 `AlertNotifyClient` 同一模式）。该接口自带：

- fingerprint 窗口去重（默认 300s）——重开抖动不刷屏；
- 按规则编码匹配启用规则后触发邮件/webhook/短信通知。

| 事件 | 级别 | 规则编码 | 指纹 | 内容 |
|---|---|---|---|---|
| CLOSED→OPEN | ERROR | `gateway:circuit:{routeId}` | `gateway-circuit-{routeId}` | 网关熔断打开（失败率达阈值，已短路降级 503） |
| HALF_OPEN→CLOSED | INFO | 同上 | `gateway-circuit-recovered-{routeId}` | 网关熔断恢复（半开探测成功） |

## 二、实现落点

- `CircuitBreakerProperties.alertBaseUrl`（`WHALE_GATEWAY_ALERT_BASE_URL`，留空 = 不上报）；
- `support/CircuitAlertNotifier`：WebClient 出站，`boundedElastic` 异步（不阻塞 reactor 事件线程），
  3s 超时，任何失败只记 WARN——告警是熔断链路的旁路，告警系统挂了不影响熔断与降级；
  配置了 `WHALE_INTERNAL_TOKEN` 时自动附加令牌头（dev 空令牌 fail-open，对齐其它服务）；
- `CircuitBreakerGlobalFilter` 状态迁移监听器挂钩（原来只有 S-07 迁移日志）；
- compose 网关服务注入 `WHALE_GATEWAY_ALERT_BASE_URL`（默认 `http://bw-alert:9103`；
  alert 属 ops profile 未起时调用失败仅 WARN）。

## 三、验证（实机）

1. 起 bw-alert（ops profile）+ 网关重建镜像；
2. whale-audit 不启动 → 22 次请求全部降级 503 → `CLOSED -> OPEN`，
   网关日志 `熔断告警已上报: route=whale-audit, level=ERROR, response={"code":0,"data":1}`；
   `alert_record` 落库：`rule_code=gateway:circuit:whale-audit`、`alert_level=ERROR`、
   `status=FIRING`、指纹 `gateway-circuit-whale-audit`、`tenant_id=1`；
3. 拉起 bw-audit 等 healthy → 首个探测 200 → `OPEN -> HALF_OPEN -> CLOSED`，
   恢复记录落库：`alert_level=INFO`、指纹 `gateway-circuit-recovered-whale-audit`（与打开告警
   指纹刻意不同，避免被打开告警的去重窗口吞掉）；
4. 现场还原：bw-alert / bw-audit 已停。

单测：网关 **130/130**（新增 `CircuitAlertNotifierTest` 6 例：请求路径与方法、
打开/恢复字段与指纹差异、未配置跳过不出站、远端 500 降级 WARN、受理返回 true），
Checkstyle 0 违例。

## 四、过程坑

- `CircuitAlertNotifier` 初版漏标 `@Component`，网关启动即崩
  （`required a bean ... could not be found`，重启循环）——新类挂进既有构造器参数链时，
  **必须先过一次本地起服或至少确认 Bean 注册**，编译与单测都不覆盖装配；
- Spring Framework 7 中 `ClientRequest` 已无 `bodyToMono`、`HttpHeaders` 不再实现 `Map`，
  单测桩改为「notifier 抽出 `buildBody` 断言字段 + 桩只抓 URL/方法/头」。

## 五、遗留

- 告警通知默认走 whale-alert 的规则匹配：`gateway:circuit:*` 目前无启用规则时只落库不通知，
  需要邮件/webhook 的话在告警规则页建一条 `gateway:circuit:*` 规则即可（属运营配置，非代码）；
- `alert_record` 的 status 恒为 FIRING（内网 create 路径不写 RECOVERED），恢复以独立
  INFO 记录表达——语义可接受，若后续要「同一事件开合成对」需动 whale-alert 的记录模型。
