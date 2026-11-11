# Wave 6.14 · 网关熔断半开探测运行时取证

- 日期：2026-11-11
- 目标：销掉 gateway 台账最后一项残余风险——熔断半开探测此前只有单测覆盖，未在运行时取证。
- 结论：**真实故障注入全链 PASS**，台账风险 #1 已销账。

## 一、取证方法（真实故障注入，非 mock）

以正在运行的 auth 路由为登录来源、以**未启动**的 whale-audit 为故障目标：

1. 网关起服，登录获取 accessToken；
2. 连续 20 次请求 `GET /audit/log/page`（窗口 20 / 阈值 50%，`Unable to find instance for whale-audit`）→ 全部降级 503 → CLOSED→OPEN；
3. 再请求一次 → 熔断短路，503 `{"code":503,"message":"服务暂时不可用，请稍后重试"}`；
4. 等 11s（`wait-duration-in-open-state-millis=10000`）→ 首个探测请求放行（半开只放一个），下游仍不可用 → HALF_OPEN→OPEN；
5. 启动 bw-audit 并等 healthy → 轮询探测，首个成功请求 200 → HALF_OPEN→CLOSED，此后请求正常。

## 二、证据

状态迁移日志（`logs/gateway.e2e2.out.log`，摘录末轮）：

```
网关熔断状态迁移: route=whale-audit, CLOSED -> OPEN     ← 20 连败触发
网关熔断已打开，短路请求并降级: route=whale-audit        ← OPEN 期短路
网关熔断状态迁移: route=whale-audit, OPEN -> HALF_OPEN  ← 等待期满
网关熔断状态迁移: route=whale-audit, HALF_OPEN -> OPEN  ← 探测失败回开
…（恢复轮询期间逐轮探测，均 HALF_OPEN -> OPEN）
网关熔断状态迁移: route=whale-audit, HALF_OPEN -> CLOSED ← 08:05:11 探测成功
```

prometheus（`/actuator/prometheus`，OPEN 期与恢复后对照）：

```
gateway_circuit_state{route="whale-audit"}              1.0  →  0.0
gateway_circuit_short_circuits_total{route="whale-audit"} 1.0 → 6.0
gateway_circuit_calls_total{route="whale-audit",result="failure"}  20.0 → 25.0
gateway_circuit_calls_total{route="whale-audit",result="success"}         → 2.0
```

## 三、脚本修复（`bw-gateway-circuit-breaker-smoke.ps1`）

该脚本此前从未真正跑通过，「运行时未取证」的根因就在这里，三处潜伏缺陷：

1. **UTF-8 无 BOM**：Windows PowerShell 5.1 按 ANSI 解析中文脚本直接报 ParserError。
   全仓排查补齐 **39 个**含中文的 `.ps1`（一次性修复，见提交记录）；
2. **登录路径陈旧**：脚本写死 `/auth/login`，auth URL 版式定为 `/auth/v{n}` 后即 404；
3. **5.1 读不到降级体**：`Invoke-WebRequest` 对 4xx/5xx 抛异常且异常响应流已被释放，
   读回空串 → 改用 .NET `HttpClient`（非 2xx 不抛异常）+ 显式 `Add-Type -AssemblyName System.Net.Http`。

同时新增 `-Recovery` 开关与恢复阶段（探测成功 → HALF_OPEN→CLOSED → 后续请求 200），
并补 prometheus 指标抓取输出。

## 四、顺带发现的部署漂移

取证时登录 401，排查发现**网关镜像停在 11-07**，未包含 11-10 `33a40f7a` 的
auth URL 版式白名单（`/auth/v1/login`），导致新版登录路径被网关当未认证请求拒绝。
重新 `package + build-service-images.sh gateway` 后恢复。

> 教训：与「target/ 旧 jar 不能作验收依据」同源——**镜像同样会静默过期**。
> 起服前若涉及 yml/白名单/路由变更，必须重建镜像而非 `restart`。

## 五、现场还原

- admin 口令哈希临时替换（BCrypt 联调口令）后已按备份还原，临时文件已删；
- bw-audit 已停（恢复容器基线）；bw-gateway 保留运行（healthy）。

## 六、遗留

- 无阻断项。gateway 台账 GW-01~07 与残余风险至此**全部闭环**；
- 熔断指标仅在容器内 Micrometer 暴露，未接告警规则（ops profile 的 monitor 可按
  `gateway_circuit_state > 0` 加规则，属可选增强）。
