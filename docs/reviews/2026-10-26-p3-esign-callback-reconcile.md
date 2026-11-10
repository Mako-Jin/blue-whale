# Wave 5.61 遗留：签署回调乱序/丢失定时补偿（检查报告）

> 日期：2026-10-26　模块：`whale-contract`（9006）　设计稿：[wave5.61-contract-esign.md](../plans/wave5.61-contract-esign.md) §十 / R5
> 目标：把设计稿 R5「回调乱序/重复 → 定时 `queryTask` 兜底」从「未做」补齐为可运行能力，且不重写既有回调处理链路。

## 一、范围

- 新增定时补偿任务：扫描停留超时仍为 `SIGNING` 的**非 local** 签署任务，调用渠道 `queryTask` 查询真实状态。
- 三方返回终态时，按回调协议**自签 HMAC** 生成内部回调，交由既有 `EsignCallbackService#handle` 处理。
- 配置化：开关、轮询间隔、首次延迟、超时阈值、单轮批量，全部支持环境变量覆盖。
- 不含：真实三方平台 API 接入（当前 `queryTask` 仍为骨架实现）、`whale-archive` 内部快照、前端改动。

## 二、方案要点

| 关注点 | 处置 |
|---|---|
| 复用而非复制 | 补偿不重写业务逻辑，只造一条「合法回调」交给 `EsignCallbackService`，验签/唯一键幂等/证据落库/工作流推进与真实回调完全一致 |
| 幂等键 | `providerEventId = RECONCILE-<signTaskId>-<action>`（≤128 字符限制内），同一任务同一动作重复补偿只会推进一次 |
| 签名口径 | 抽出 `EsignCallbackSigner` 统一 HMAC-SHA256 计算，`AbstractHmacEsignProvider` 验签与补偿签名共用，避免两处实现漂移 |
| 时间窗 | 补偿回调使用当前秒级时间戳 + 32 位 UUID nonce，天然落在 ±300s 窗口内 |
| 筛选条件 | `status=SIGNING` + `provider_task_id` 非空 + `provider<>LOCAL` + `update_time` 早于 `stale-seconds`，按 `update_time` 升序 `LIMIT batch-size` |
| 失败隔离 | 单任务异常只 `warn` 并继续下一个，不中断整轮；整轮异常不影响下一次调度 |
| 安全开关 | 未配置 `callback-secret` 时整轮跳过（不查库、不签名），避免用默认密钥自签 |
| 调度 | `@Scheduled(fixedDelay)` + 启动类 `@EnableScheduling`；`whale.contract.esign.reconcile.enabled=false` 时不装配该 Bean |

## 三、变更点

- 新增 [`EsignCallbackSigner`](../../whale-backend/whale-apps/whale-contract/src/main/java/com/bluewhale/contract/esign/provider/EsignCallbackSigner.java)：`HEADER_TIMESTAMP/NONCE/SIGNATURE` 常量 + `sign(secret,timestamp,nonce,body)` + `hmacBase64(secret,content)`。
- 修改 [`AbstractHmacEsignProvider`](../../whale-backend/whale-apps/whale-contract/src/main/java/com/bluewhale/contract/esign/provider/AbstractHmacEsignProvider.java)：头常量与 HMAC 计算委托给 `EsignCallbackSigner`，删除其内联 `Mac/SecretKeySpec/Base64` 实现。
- 新增 [`SignTaskReconcileJob`](../../whale-backend/whale-apps/whale-contract/src/main/java/com/bluewhale/contract/esign/reconcile/SignTaskReconcileJob.java)：`reconcile()` → `staleQuery()` → `reconcileOne()` → `mapAction()` → `deliver()`。
- 修改 [`ContractEsignProperties`](../../whale-backend/whale-apps/whale-contract/src/main/java/com/bluewhale/contract/config/ContractEsignProperties.java)：新增 `reconcile{enabled,staleSeconds,batchSize}`。
- 修改 [`WhaleContractApplication`](../../whale-backend/whale-apps/whale-contract/src/main/java/com/bluewhale/contract/WhaleContractApplication.java)：加 `@EnableScheduling`。
- 修改 [`application.yml`](../../whale-backend/whale-apps/whale-contract/src/main/resources/application.yml)：`whale.contract.esign.reconcile` 五项配置，环境变量 `CONTRACT_ESIGN_RECONCILE_*` 可覆盖。
- 新增 [`SignTaskReconcileJobTest`](../../whale-backend/whale-apps/whale-contract/src/test/java/com/bluewhale/contract/esign/reconcile/SignTaskReconcileJobTest.java)：4 用例。

## 四、验证

### 4.1 构建与单测

- `mvn -o -pl whale-backend/whale-apps/whale-contract -am package` **BUILD SUCCESS**，Checkstyle 0 违规（首轮 `EsignCallbackSigner` 私有构造缺 Javadoc 被门禁拦截，已补注释后通过）。
- `SignTaskReconcileJobTest`：**Tests run: 4, Failures: 0, Errors: 0**。
  1. `FINISHED` → 生成 `SIGN` 回调，断言 `providerEventId=RECONCILE-<id>-SIGN`、payload 字段齐全、签名可用同一密钥复算一致；
  2. 三方仍 `SIGNING` → 不触发回调；
  3. `REJECTED` → 生成 `REFUSE` 回调并携带三方原因；
  4. 未配置 `callback-secret` → 不查库、不签名。

### 4.2 实机验证（本机 9006 + MySQL 8.0.26）

以 `CONTRACT_ESIGN_RECONCILE_INTERVAL_MS=10000`、`CONTRACT_ESIGN_RECONCILE_STALE_SECONDS=30` 启动，插入三条验证任务（合同 2）：

| 任务 | 构造 | 预期 | 实测 |
|---|---|---|---|
| 过期三方 | `esignbao` / `SIGNING` / `update_time = now-300s` / 有三方任务号 | 被轮询 | 16:05:33 起每 10s 记录「签署任务 … 三方状态 SIGNING，暂不推进」 |
| 本地渠道 | `LOCAL` / `SIGNING` / 过期 | 不参与 | 全程无记录 |
| 未超时 | `fadada` / `SIGNING` / `update_time = now` | 30s 后才参与 | 16:06:03 首次出现（插入后 32s） |

- 骨架渠道 `queryTask` 返回 `SIGNING`，任务未被错误推进（`status` 仍 `SIGNING`、合同仍 `APPROVED`），证明「非终态不推进」。
- 清理验证数据后，下一轮仅执行查询 SQL、无任务命中，确认逻辑删除与筛选条件生效。
- 恢复默认配置（60s / 120s）重启，9006 `/actuator/health` = `UP`；经网关回归 `/contract/page` `code=0`、`total=11`。

## 五、结论与遗留

- R5 的「定时兜底」已落地：乱序/丢失回调不再依赖人工重放；同任务多轮补偿因唯一键幂等不会重复推进。
- 遗留（不影响本项验收）：
  1. 三方 `queryTask` 仍为骨架实现（恒 `SIGNING`），真实终态推进需接入平台 API 后由单测覆盖的 `deliver` 路径生效；
  2. 同一批超时任务会持续占据 `LIMIT batch-size` 名额，若长期非终态任务超过批量上限，新任务排队靠后；真实接入后应结合平台查询配额再评估「轮询时间戳回写」；
  3. 补偿回调不落「外部回调原始报文」，`payload` 中带 `RECONCILE-` 前缀事件号，可与真实回调区分。
