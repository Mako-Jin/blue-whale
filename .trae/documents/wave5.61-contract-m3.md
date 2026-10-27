# Wave 5.61 M3 实施计划：第三方电子签章 + 回调推进流程

## Context

M1 已交付合同主流程（9006、`/contract/**`、4 表、权限 215–222、菜单 28），M2 已交付印章/用章授权/PDF 盖章/BC 签名/链式证据/验签/作废（权限 223–231、菜单 29–30）。M3 要补齐第三方电子签章能力：可配置的 provider 适配层、异步回调验签与幂等、回调推进工作流、签署证据落库，以及签署完成后的生效/归档闭环。

设计依据：[wave5.61-contract-esign.md](file:///d:/BlueWhale/BlueWhale/docs/plans/wave5.61-contract-esign.md) §5.3、§6.3、§7.2、§9、§10-M3。

目标验收：
1. 回调重放 10 次仅推进一次；
2. 非法签名 / 超时间窗回调一律拒绝并落审计；
3. 证据链断裂可被 `/contract/{id}/verify` 检出（复用 M2 能力）；
4. 签署完成后可生效并归档；
5. provider 切换只改配置不改代码。

## 已核实现状

- `whale-contract`（9006）：[SignServiceImpl.java](file:///d:/BlueWhale/BlueWhale/backend/whale-apps/whale-contract/src/main/java/com/bluewhale/contract/service/impl/SignServiceImpl.java) 只有本地签署；`ct_sign_task.provider/provider_task_id` 字段已预留；`FileClient` 走 `/file/internal/**`；`ContractApprovalClient` 走 `workflow-url`。
- `whale-workflow`（8083）：仅有只读 `/workflow/internal/instance/**`；[ApproveCommand.java](file:///d:/BlueWhale/BlueWhale/backend/whale-services/whale-workflow/src/main/java/com/bluewhale/workflow/service/impl/task/ApproveCommand.java) 依赖 `requirePendingTask`；`process_task` 无操作来源字段。
- 网关：`internal-deny` 已含 `/workflow/internal/**` 与 `/contract/internal/**`；JWT `white-list` 需新增回调路径。
- 迁移命名沿用：M1 `migration-5.61-contract.sql`、M2 `migration-5.62-contract-esign.sql`，M3 用 `5.63`。

## 实施步骤

### 1. 数据库迁移（幂等）

1. 新建 `backend/whale-apps/whale-contract/src/main/resources/db/migration-5.63-contract-esign-callback.sql`
   - `ct_esign_callback`：`provider`、`provider_event_id`、`contract_id`、`sign_task_id`、`payload_hash`、`payload`、`status(RECEIVED/PROCESSED/FAILED)`、`retry_count`、`error_msg`，唯一键 `(tenant_id, provider, provider_event_id)`。
   - `ct_sign_task` 增列 `workflow_task_id BIGINT`、`sign_url VARCHAR(512)`，用 `information_schema.COLUMNS` 守卫 + `PREPARE/EXECUTE` 实现可重复 ALTER。
2. 新建 `backend/whale-services/whale-workflow/src/main/resources/db/migration-5.63-workflow-task-operator.sql`
   - `process_task` 增 `operator_id BIGINT`、`operator_type VARCHAR(16)`、`operator_source VARCHAR(64)`（同守卫 ALTER）。
3. 新建 `backend/whale-services/whale-auth/src/main/resources/db/migration-5.63-contract-esign-m3.sql`
   - 权限 232 `sys:contract:effective`（合同生效）；菜单 31 证据中心（`/contract/evidence`，component `contract/evidence`，permission `sys:contract:evidence:read`）。

### 2. workflow 内部完成接口（设计稿方案 B）

- `ProcessTask` 增 `operatorId/operatorType/operatorSource` 字段。
- 新建 `dto/TaskCompleteRequest`：`operatorId`、`action(APPROVE/REJECT)`、`comment`、`variables`、`eventId`、`source`。
- `ProcessTaskService`/`ProcessTaskServiceImpl` 增 `completeInternal(Long id, TaskCompleteRequest req)`：
  - 任务不存在 → 404；已 `APPROVED` 且 action=APPROVE → 幂等返回；`REJECTED/CANCELED` → 参数错误。
  - 先写入操作来源字段，再复用 `ApproveCommand.approve` / `RejectCommand.reject`（不改现有命令签名）。
- 新建 `controller/WorkflowInternalTaskController`：`POST /workflow/internal/task/{id}/complete`；内网接口不加权限码（网关 `internal-deny` 已屏蔽）。

### 3. contract 电子签 provider 适配层

- 新建包 `com.bluewhale.contract.esign.provider`：
  - `EsignProvider`：`name()`、`createTask`、`queryTask`、`downloadSignedFile`、`verifyCallback`。
  - `EsignTaskCommand` / `EsignTaskResult(providerTaskId, signUrl)` / `EsignTaskStatus`（record）。
  - `AbstractHmacEsignProvider`：验签 `Base64(HMAC-SHA256(secret, timestamp + "\n" + nonce + "\n" + rawBody))`，请求头 `X-Esign-Timestamp/Nonce/Signature`，时间窗 ±`callbackWindowSeconds`（默认 300），常量时间比较；secret 为空一律拒绝。
  - `LocalEsignProvider`、`EsignbaoEsignProvider`、`FadadaEsignProvider`、`SsqEsignProvider`：三方通过 `EsignOpenApiClient` 调开放平台 HTTP 接口（创建/查询/下载 + HMAC 请求签名 + 合同快照上传），不再生成假任务号或固定返回 SIGNING；接口路径、回调地址、超时均可配置。
  - `EsignProviderRegistry`：注入全部实现，按名称解析，默认取配置 `whale.contract.esign.provider`。
- 扩展 `ContractEsignProperties`：`provider`（默认 `local`）、`callbackWindowSeconds`、`providers`（`baseUrl/appId/appSecret/callbackSecret/signUrlTemplate`）。
- `application.yml` 增加 `whale.contract.esign.provider/callback-window-seconds/providers.*`，全部支持环境变量覆盖。

### 4. 回调接口与处理

- 新建 `dto/EsignCallbackPayload`：`providerEventId`、`action(SIGN/REFUSE)`、`signTaskId`、`providerTaskId`、`evidenceNo`、`certSerial`、`signedAt`、`signedFileId`、`signedSha256`、`refuseReason`。
- 新建 `entity/CtEsignCallback` + `mapper/CtEsignCallbackMapper`。
- 新建 `service/EsignCallbackService` + impl：
  1. 未知 provider → 参数错误；`verifyCallback` 失败 → `FORBIDDEN`（控制器 `@AuditLog` 落审计）。
  2. 同 `(provider, providerEventId)` 已 `PROCESSED` → 直接幂等返回；`FAILED` → `retry_count+1` 重放处理。
  3. 事务内：按 `signTaskId` 或 `(provider, providerTaskId)` 定位任务；SIGN 时计算 `original_sha256`（优先合同底稿）与 `signed_sha256`（优先 `downloadSignedFile` 并上传 whale-file，否则取 payload 哈希），复用 `EsignHashUtils.chain` 生成 `chain_hash`，写 `ct_sign_record`，任务转 `SIGNED`；全部签完合同转 `SIGNED`。REFUSE 时任务转 `REFUSED`，合同停留 `SIGNING`。
  4. 任务带 `workflow_task_id` 时调用 workflow 内部完成接口（失败回滚并标记回调 `FAILED` 可重放）。
  5. 回调行更新 `PROCESSED`；异常更新 `FAILED + error_msg`。
- 新建 `client/WorkflowInternalClient`：`POST /workflow/internal/task/{id}/complete`，透传 `X-Tenant-Id`，幂等由服务端任务状态保证。
- 新建 `controller/EsignCallbackController`：`POST /contract/sign/callback/{provider}`（原始 body + 请求头），`@AuditLog`，无权限码。
- 网关 `whale.gateway.jwt.white-list` 增加 `/contract/sign/callback/**`。

### 5. 签署发起、生效与归档

- `SignTaskCreateRequest` 增 `workflowTaskId`；`SignTaskVO` 增 `providerTaskId/signUrl/workflowTaskId`。
- `SignServiceImpl.start`：provider 默认取 `properties.provider`；第三方任务调用 `createTask` 并保存 `providerTaskId/signUrl/workflowTaskId`，任务状态置 `SIGNING`。
- `SignService`/`ContractSignController` 增 `POST /contract/{id}/effective`：仅 `SIGNED → EFFECTIVE`，权限 `sys:contract:effective`，`@AuditLog`；归档继续走既有 `/contract/{id}/archive`（仅 `EFFECTIVE → ARCHIVED`）。
- 说明：外部签署归档快照对接 `whale-archive` 内部接口不在本轮新增，沿用既有平台审批实例归档规则；未启用归档时以「生效 → 归档」闭环验收，报告中标注。

### 6. 前端最小改动

- `frontend/platform-apps/whale-contract`：签署中心展示 `provider/providerTaskId/signUrl`；新增证据中心页面（菜单 31）复用 `GET /contract/{id}/evidence` + `POST /contract/{id}/verify`，展示时间线与链连续性；按既有 `Id = string`、`@bluewhale/http` 约定。
- 通过 `pnpm --filter ... typecheck/build`（沿用 M2 的命令）。

### 7. 冒烟脚本

新建 `scripts/smoke/bw-contract-m3-smoke.ps1`（PowerShell 7 执行），覆盖：
1. 登录 → 创建并审批通过合同；
2. 直连 workflow 建一个带 USER_TASK 的实例，取待办任务 ID；
3. `provider=esignbao` + `workflowTaskId` 发起签署，断言返回 `providerTaskId/signUrl`；
4. PowerShell 计算 HMAC 签名，经网关（8080，不带 Authorization）调用回调，断言任务 `SIGNED`、合同 `SIGNED`、workflow 实例通过；
5. 同一事件重放 10 次，断言 `ct_sign_record` 仅 1 条、workflow 任务状态不变；
6. 错误签名 / 时间戳超出 300s → `code=403` 且 `bluewhale_contract.audit_log` 有失败记录；
7. `provider=fadada` 走同一代码路径成功（验证配置切换）；
8. 证据链 + 验签通过；篡改 `chain_hash` 后 `chainValid=false` 并恢复；
9. `/contract/{id}/effective` → `/contract/{id}/archive` 状态闭环；
10. 未知 provider → 参数错误。

脚本不写死数据库口令：仅读取 `$env:MYSQL_PASSWORD`，缺失时跳过 DB 断言并提示。

### 8. 构建、重启与收口

- 应用 3 个迁移脚本（mysql CLI，口令取自环境变量）。
- 构建：`$env:JAVA_HOME='D:\tools\installer\jdk\java21'`，`mvn -o -pl whale-services/whale-workflow,whale-services/whale-gateway,whale-apps/whale-contract -am package`。
- 重启 workflow(8083)、gateway(8080)、contract(9006)（先停端口占用进程），执行 M3 冒烟。
- 文档：追加 `docs/devlog/2026-10-26.md`；新增 `docs/reviews/2026-10-26-p3-wave5.61-contract-m3.md`；更新 `docs/plans/priority-execution-ledger-2026-10-25.md` 序号 19、`docs/plans/roadmap.md`、`docs/plans/task-breakdown.md`、设计稿状态与变更记录。
- 提交并推送 Gitee origin；GitHub 镜像按既有策略在任务开始时尝试一次，禁止裸 `--force`。

## 验证清单

| # | 验证项 | 命令/断言 |
|---|---|---|
| 1 | 构建 | `mvn -o ... package` 全绿 |
| 2 | 服务健康 | 8080/8083/9006 `/actuator/health` 均 `UP` |
| 3 | 回调幂等 | 重放 10 次仅 1 条 `ct_sign_record` |
| 4 | 验签拒绝 | 错误签名/过期时间戳 → `code=403` + `audit_log.biz_status=0` |
| 5 | 流程推进 | workflow 任务 `APPROVED`、实例 `status=1` |
| 6 | provider 切换 | `esignbao`/`fadada` 仅改请求或配置即可回调成功 |
| 7 | 证据链 | `verify.hashMatch/chainValid/signatureValid` 均 true；篡改后 false |
| 8 | 生效归档 | `SIGNED → EFFECTIVE → ARCHIVED` |
| 9 | 前端 | `typecheck`/`build` 通过 |

## 风险与边界

- 三方真实 API 为骨架，HMAC 回调 + 本地文件兜底用于 PoC 验证；生产需替换为各平台 RSA/SM2 验签与持牌 CA/TSA，报告中明确。
- 回调匿名：依赖 HMAC 密钥 + 时间窗 + 事件幂等 + 网关白名单最小化；密钥默认值为 dev 占位，生产必须用环境变量覆盖。
- MySQL 无 `ADD COLUMN IF NOT EXISTS`，迁移采用 `information_schema` 守卫的 prepared statement。
