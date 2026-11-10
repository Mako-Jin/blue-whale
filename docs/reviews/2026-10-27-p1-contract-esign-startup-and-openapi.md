# P1 #1/#2 合同电子签生产护栏与开放平台接入检查报告

- 日期：2026-10-27
- 范围：P1 #1「合同电子签生产配置护栏」、P1 #2「三方签章 API 接入」
- 对应提交：`a14b4775 fix(contract): 补齐电子签生产配置护栏与三方签章 HTTP 接入`
- 模块：`whale-backend/whale-apps/whale-contract`
- 结论：**代码级闭环**；真实供应商沙箱仍需外部凭据，未做生产验收。

## 1. 原问题

| 编号 | 原风险 | 影响 |
| --- | --- | --- |
| P1 #1 | 非 dev 环境仍可能以 `local` 自签或默认回调密钥启动 | 合同不具备第三方效力，回调签名可被预测 |
| P1 #2 | 三方渠道只返回假任务号和固定 `SIGNING`，未真正调用开放平台 | 无法创建真实签署任务、查询状态或取回签章件 |

## 2. P1 #1 生产配置护栏

### 2.1 实现

`ContractEsignStartupChecker` 实现 `InitializingBean`，在 Bean 装配阶段、端口监听前执行：

1. 非 dev 环境 `provider` 为空或为 `local`：拒绝启动，并提示 `CONTRACT_ESIGN_PROVIDER`。
2. 非 dev 环境回调密钥为空或仍为默认开发值：拒绝启动，并提示 `CONTRACT_ESIGN_CALLBACK_SECRET`。
3. 非 dev 环境所选渠道缺少 `base-url` / `app-id` / `app-secret`：拒绝启动。
4. dev 环境保留 `local` 与默认密钥，允许本地联调。
5. 后续 S-04d-1 加固已并入同一启动器：`tsa-required=true` 必须有合法 `tsa-url`；配置 CA 信任根 / CRL / PKCS#12 时必须在启动阶段可读、可解、未过期且能链到信任根。

### 2.2 关键文件

- `whale-backend/whale-apps/whale-contract/src/main/java/com/bluewhale/contract/config/ContractEsignStartupChecker.java`
- `whale-backend/whale-apps/whale-contract/src/main/java/com/bluewhale/contract/config/ContractEsignProperties.java`
- `whale-backend/whale-apps/whale-contract/src/main/resources/application.yml`

### 2.3 覆盖

`ContractEsignStartupCheckerTest` 共 **11** 个用例，覆盖：

- dev 允许 local 与默认密钥；
- 非 dev 拒绝 local、空 provider、默认密钥、缺失渠道凭证；
- 非 dev 正常放行真实渠道；
- PKCS#12 不可读在 dev 也拒绝；
- `tsa-required` 无地址、TSA 协议非法、CRL 无 CA、CA 文件不可读均拒绝启动。

## 3. P1 #2 开放平台接入

### 3.1 实现

- 新增 `EsignOpenApiClient`：
  - `POST createPath` 创建任务；
  - `GET queryPath` 查询状态；
  - `GET downloadPath` 下载签章件，兼容二进制 PDF 与 JSON `data.fileBase64`；
  - JSON 信封 `{code,message,data}` 校验，非 0 / 缺 `taskId` / 缺 `status` / 空下载均抛 `BizException`；
  - 连接超时默认 3s、读取超时默认 10s，可由渠道配置覆盖；
  - 路径统一以 `/` 开头，任务号做 URL path segment 编码。
- 新增 `EsignRequestSigner`：
  - 请求头 `X-Esign-App-Id / X-Esign-Timestamp / X-Esign-Nonce / X-Esign-Signature`；
  - 签名口径 `Base64(HMAC-SHA256(appSecret, method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body))`。
- `AbstractHmacEsignProvider` 将创建/查询/下载统一委托客户端；回调验签使用时间戳 + nonce + rawBody，支持秒/毫秒时间戳与可配置窗口，使用常量时间比较。
- `SignServiceImpl#start` 发起三方签署时：
  - 先落库 `PENDING` 任务；
  - 加载未签署合同 PDF 快照并 Base64 上传渠道；
  - 回写真实 `providerTaskId`、`signUrl`，状态推进到 `SIGNING`；
  - 渠道调用失败时本地事务回滚，不再生成假任务号。
- `EsignCallbackServiceImpl` 签署完成时：
  - 优先调用渠道下载签章件并上传文件服务；
  - 下载不可用时回退回调中的 `signedFileId` / `signedSha256`；
  - 写签署证据、推进任务/合同状态与工作流。

### 3.2 渠道与配置

已注册 `esignbao`、`fadada`、`ssq` 三个渠道，统一读取：

```yaml
whale.contract.esign:
  provider: ${CONTRACT_ESIGN_PROVIDER:local}
  callback-secret: ${CONTRACT_ESIGN_CALLBACK_SECRET:...}
  providers:
    esignbao:
      base-url: ${CONTRACT_ESIGN_ESIGNBAO_BASE_URL:}
      app-id: ${CONTRACT_ESIGN_ESIGNBAO_APP_ID:}
      app-secret: ${CONTRACT_ESIGN_ESIGNBAO_APP_SECRET:}
      create-path: /v1/sign-tasks
      query-path: /v1/sign-tasks/{providerTaskId}
      download-path: /v1/sign-tasks/{providerTaskId}/file
```

`fadada`、`ssq` 使用同构配置键。切换渠道只改 `provider` 与对应环境变量，不改代码。

### 3.3 覆盖

- `EsignOpenApiClientTest`：**6** 个 Mock HTTP 用例，覆盖创建成功、查询状态、二进制下载、JSON Base64 下载、业务错误、缺配置。
- `EsignOpenApiSandboxTest`：**2** 个本机真实 HTTP 用例（JDK `HttpServer`），覆盖完整生命周期与供应商业务错误。
- 当前 `whale-contract` 模块：**47** 用例，0 失败，0 错误；Checkstyle **0** 违规。

说明：本机 HTTP 测试验证的是 HTTP 报文、签名头、状态映射与错误处理，**不等同于真实供应商沙箱验收**。

## 4. 本次验证与附带修复

执行：

```powershell
.\mvnw.cmd -B -pl whale-backend/whale-apps/whale-contract -am test
```

结果：

- `whale-contract`：Tests run **47**, Failures 0, Errors 0, Skipped 0。
- Checkstyle：**0 violations**。
- Reactor：`Whale Contract` SUCCESS，BUILD SUCCESS。

验证时发现 `EsignCallbackServiceImpl#process` 新增下载逻辑后有 2 条 Checkstyle 违规（方法 90 行、圈复杂度 17）。已将拒签分支提取为 `handleRefusal`，行为不变，`process` 降到门禁阈值内；重跑后测试与 Checkstyle 均通过。

## 5. 边界与未闭环项

| 项 | 状态 | 说明 |
| --- | --- | --- |
| 真实供应商沙箱 | **未验证** | 需要供应商账号、`base-url/app-id/app-secret`、测试合同与回调地址；当前仅有本机 HTTP 沙箱 |
| 供应商私有协议映射 | **未验证** | 当前实现为 BlueWhale 开放平台协议 v1；e签宝/法大大/上上签真实接口字段、签名算法可能不同，需按合同文档适配 |
| 回调验签算法 | 有条件 | 默认 HMAC-SHA256；供应商若使用 RSA/SM2，需要覆盖 `verifyCallback` |
| 远程创建补偿 | 未实现 | 渠道创建成功后本地事务提交失败，可能留下孤立三方任务；当前无 outbox/撤销补偿 |
| 签章件下载失败 | 有兜底 | 回退回调 `signedFileId` / `signedSha256`；若回调也未带文件，证据仅保留哈希，不保证本地有签章件 |
| CA/TSA/实名/意愿 | 另行验证 | 由 S-04d-1 / S-04e-1 及 P0 外部接入项覆盖，不在本报告重复声明 |

## 6. 结论

- P1 #1：**代码级闭环**。非 dev 环境无法以 local 自签或默认回调密钥静默启动。
- P1 #2：**代码级闭环**。三方渠道已具备真实 HTTP 创建/查询/下载与 HMAC 请求签名，不再生成假任务号。
- 生产可用性仍未闭环：真实供应商沙箱、协议映射与凭据属于 P0 外部接入阻塞项，不能用本报告的本地测试替代。

## 7. 证据索引

- 提交：`a14b4775`
- 启动护栏：`ContractEsignStartupChecker.java`
- HTTP 客户端：`EsignOpenApiClient.java`
- 请求签名：`EsignRequestSigner.java`
- 渠道公共实现：`AbstractHmacEsignProvider.java`
- 发起签署：`SignServiceImpl.java`
- 回调处理：`EsignCallbackServiceImpl.java`
- 配置：`application.yml`
- 测试：`ContractEsignStartupCheckerTest`、`EsignOpenApiClientTest`、`EsignOpenApiSandboxTest`
- 测试日志：`target/mvn-contract-test.log`
