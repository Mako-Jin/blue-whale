# T5 / V-11 + T6 / S-04 检查报告：合同电子签沙箱联调与签章合规

- 日期：2026-10-27
- 范围：`whale-contract` 电子签渠道、印章预览、本地数字签名
- 待办编号：T5 / V-11（供应商沙箱联调）、T6 / S-04（CA/TSA、实名意愿、P12、印章水印）
- 关联：[Wave 5.61 电子签设计](../plans/wave5.61-contract-esign.md)、[P3 回调补偿](2026-10-26-p3-esign-callback-reconcile.md)

## 1. 结论先行

| 子项 | 状态 | 说明 |
|---|---|---|
| T5 开放平台协议 v1 沙箱联调（代码侧） | ✅ 完成 | 新增真实 loopback HTTP 沙箱测试，服务端按协议重算 HMAC 并校验，覆盖创建 → 查询 → 下载 → 业务错误码 |
| T5 真实供应商沙箱（esignbao / fadada / ssq） | ⛔ 阻塞 | 仓库内无供应商沙箱地址、appId/appSecret、回调公网地址与真实协议文档；无法在无凭证情况下声称联调通过 |
| T6 印章预览半透明水印 + 低分辨率 | ✅ 完成 | 新增预览接口与前端预览弹窗，禁用缓存并附 `nosniff` |
| T6 P12 证书生产可用性 | ✅ 完成 | 启动校验 PKCS#12（文件/口令/私钥/有效期）；修复 BC 1.77/1.78 混用导致的首次签名 `NoClassDefFoundError`；改为标准 PDF 签名域并做真实验签 |
| T6 持牌 CA / TSA | ⛔ 阻塞 | 需 TSA 服务地址、账号口令与 CA 证书链；`tsa-url` 配置位已预留，未接真实服务 |
| T6 实名 / 意愿认证 | ⛔ 阻塞 | 需供应商实名接口、意愿校验方式与回调证据字段；本轮未发明协议 |
| T6 国密 SM2 | ✅ 评估完成 | 结论见 §5；实现需 CA 签发 SM2 证书与渠道支持，暂不落地 |

## 2. T5：开放平台协议 v1 沙箱联调

### 2.1 新增验证

`EsignOpenApiSandboxTest` 启动真实 `com.sun.net.httpserver.HttpServer`（随机端口），按协议重新计算：

```
HMAC-SHA256(secret, METHOD + "\n" + PATH + "\n" + TIMESTAMP + "\n" + NONCE + "\n" + BODY)
```

服务端校验 `X-ESign-App-Id`、`X-ESign-Timestamp`、`X-ESign-Nonce`、`X-ESign-Signature`，并模拟：

1. `POST /v1/tasks` → 返回 `taskId` + `signUrl`，同时断言请求体含 `contractCode` 与 `contractFileBase64`；
2. `GET /v1/tasks/{id}` → 返回 `SIGNED`；
3. `GET /v1/tasks/{id}/file` → 透传 PDF 字节；
4. 供应商返回 `code=40001` → 客户端抛 `BizException`，不生成假任务。

与既有 `EsignOpenApiClientTest`（Mock `RestClient`）相比，本轮覆盖真实 HTTP 栈、请求头编码、路径模板与响应解析。

### 2.2 真实沙箱阻塞条件

| 需要的信息 | 当前状态 |
|---|---|
| 供应商沙箱 `base-url` | 未提供 |
| `app-id` / `app-secret` | 未提供 |
| 供应商真实签名算法、时间戳格式、请求体字段 | 仓库只有「BlueWhale 开放平台协议 v1」通用约定，未见三家厂商真实文档 |
| 回调可达公网地址（用于异步通知验证） | 未提供 |
| 供应商测试账号/测试印章 | 未提供 |

拿到上述信息后，只需设置 `CONTRACT_ESIGN_PROVIDER`、`CONTRACT_ESIGN_<PROVIDER>_BASE_URL/APP_ID/APP_SECRET/NOTIFY_URL`，
按真实文档覆盖 `create/query/download` 路径与签名实现即可；无需改业务编排。

## 3. T6：印章预览水印

### 3.1 后端

| 文件 | 改动 |
|---|---|
| `PdfStampService#previewImage` | 最长边缩至 `240px`（等比、保留 ARGB），叠加旋转 `PREVIEW` 半透明水印（透明度 `0.22`） |
| `SealService#previewImage` | 按印章记录加载图片文件，复用内置印章兜底 |
| `ContractSealController#preview` | `GET /contract/seal/{id}/preview`，`sys:contract:seal:list`，`image/png` + `Cache-Control: no-store` + `nosniff` |

### 3.2 前端

- `SealList` 增加「预览」按钮（仅 `sys:contract:seal:list` 可见），弹窗展示水印预览；
- `client.ts` 新增 `requestBlob`，绕过 `Result` 信封直接取二进制，错误时尽力解析后端消息；
- `esign.ts` 新增 `sealApi.preview`，正确释放 `ObjectURL`。

### 3.3 边界

- 预览图带水印且分辨率受限，但**未实现原图下载接口**；设计中的 `sys:contract:seal:download`
  权限点尚未在权限种子中定义。若后续开放原图导出，需新增权限码、迁移授权与审计，不能复用预览接口。

## 4. T6：P12 证书与数字签名

### 4.1 修复的真实缺陷

1. **依赖版本混用**：`bcpkix 1.77 + bcprov 1.78` 首次 CMS 签名即抛
   `NoClassDefFoundError: org/bouncycastle/asn1/edec/EdECObjectIdentifiers`。
   已统一为 `1.78`，并在 `pom.xml` 注释原因。
2. **签名字节范围错误**：旧实现先对 PDF 计算 CMS，再把 CMS 作为 PDF 附件写入；
   附件写入改变文件字节，最终文件无法用该 CMS 验签。旧 `verify` 只比对证书序列号，
   无法发现文件篡改。
3. **启动期证书缺失**：配置了不可读/口令错误的 PKCS#12 时，应用仍能启动，直到首次签署才失败。

### 4.2 新实现

| 文件 | 改动 |
|---|---|
| `SignatureService#sign` | `PDDocument.addSignature` + `saveIncremental`，生成 `adbe.pkcs7.detached` 标准签名域；CMS 由 PDFBox 按签名域 byte range 计算 |
| `SignatureService#verify` | 优先按签名域取 `getSignedContent(pdf)` 与 CMS，做 **密码学验签** + 序列号匹配 + 签署时证书有效期校验；无签名域时兼容历史附件格式并明示「无法校验最终内容」 |
| `ContractEsignStartupChecker` | 配置 `p12-path` 时校验文件存在/可读、口令可解、含私钥条目、证书未过期；不配置时仅告警（三方渠道由供应商签署） |

### 4.3 验证

```powershell
mvn -B -pl backend/whale-apps/whale-contract -am test
```

- `whale-contract` 29 用例 0 失败；上游依赖模块 91 用例 0 失败；
- 新增 `SignatureServiceTest` 5 用例：
  - 生成 PKCS#12 自签证书 → 签署 → 真实验签通过；
  - 篡改 PDF 头部版本字节 → 验签失败；
  - 证书序列号不匹配 → 验签失败；
  - 未配置证书 → 原样返回 `NONE`；
  - 错误口令 → 签署失败而非静默降级；
- `ContractEsignStartupCheckerTest` 新增 dev 环境不可读 P12 拒绝启动用例（共 7 例）；
- 前端 `whale-contract`：`eslint --max-warnings 0` 与 `vite build` 通过。

## 5. T6：CA / TSA / 实名意愿 / SM2 评估

### 5.1 CA / TSA

- **CA**：P12 仅证明「持有某证书私钥」，法律效力取决于证书是否由持牌 CA 签发。
  平台侧签名链已可工作，但必须由客户提供 CA 签发的 PKCS#12 与证书链，才能满足《电子签名法》可靠电子签名要求。
- **TSA**：RFC 3161 时间戳需要 TSA 服务（地址、账号、证书链）。当前 `tsa-url` 仅占位，
  未实现 `TimeStampRequest/Response` 与 CMS `id-aa-signatureTimeStampToken` 属性注入。
  建议后续以「可选开启」方式接入：TSA 不可用时不得静默降级为无时间戳。
- **验签链**：当前校验签名证书本身，未校验证书链到受信根；生产接入 CA 后应补充信任根配置与 CRL/OCSP。

### 5.2 实名 / 意愿认证

- 三方渠道（esignbao/fadada/ssq）通常在供应商侧完成实名、短信/人脸意愿校验，平台只保存结果与证据编号；
  当前 `ct_sign_record` 没有实名结果、意愿校验方式、证据编号字段，回调也未映射这些字段。
- 本地 `local` 渠道目前只有「操作人身份 + 登录态」，不构成实名意愿认证。
- 需要产品/合规确认：认证方式（短信/人脸/视频双录）、证据留存年限、字段模型、失败重试与人工复核流程，
  再补迁移与接口；本轮不以自定义字段冒充合规证据。

### 5.3 国密 SM2

- BouncyCastle 支持 SM2/SM3，但需要：
  1. CA 签发 SM2 证书（当前 P12 解析按标准 X.509，可承载 SM2 公钥）；
  2. CMS 使用 SM3 摘要与 SM2 签名，PDF `SubFilter` 需评估阅读器兼容性（`adbe.pkcs7.detached` 对 SM2 支持有限）；
  3. 供应商渠道与网关/TLS 是否支持国密套件；
  4. 印章/证书的国密合规认证。
- 结论：技术可行但依赖 CA 与阅读器生态，暂不落地；已记录为需专项评估项。

## 6. 剩余阻塞与下一步

> 2026-10-27 第二轮已把可代码化部分全部落地（见 §7），下表保留仍依赖外部输入的项。

| 项 | 状态 | 阻塞 / 下一步 |
|---|---|---|
| T5 真实沙箱 | **仍阻塞**（代码侧已就绪） | 缺沙箱地址、appId/appSecret、回调公网地址、真实协议文档；凭证到位后按 §2.2 配置并跑契约测试 |
| T6 CA/TSA | **技术接入完成，待凭证** | 已支持 RFC 3161 时间戳、PEM 信任根 PKIX 链校验、CRL、`tsa-required`；仍需持牌 CA 证书链/CRL 与 TSA 地址账号实机联调 |
| T6 实名/意愿 | **字段与本地证据完成，待供应商** | 已补 6 字段、接口层强制意愿确认、回调映射；仍需 ID_CARD_OCR/FACE/VIDEO/SMS 供应商接口与证据字段约定 |
| T6 国密 | **暂不落地** | 缺 SM2 证书与阅读器/渠道支持结论；单独立项评估，不在本轮代码中伪造国密能力 |
| T6 原图导出 | **已完成** | `sys:contract:seal:download`（233）+ 仅授 `contract_manager`/`tenant_admin` + 附件下载 + 审计 + 前端权限按钮 |

## 7. 第二轮补充落地（2026-10-27）

### 7.1 CA 证书链与 RFC 3161 时间戳

- `TimestampService`：向 `tsa-url` 提交签名值 SHA-256 指纹（`TimeStampRequestGenerator`，`certReq=true`，随机 nonce），
  校验 `TimeStampResponse` 后取得 token；可选 Basic 认证。失败语义 fail-closed：HTTP 非 200、令牌缺失、
  响应校验失败、写入失败一律让签署失败，不静默降级。
- `SignatureService.applyTimestamp`：把 token 以 CMS 未签名属性 `id-aa-signatureTimeStampToken` 写入
  （`SignerInformation.replaceUnsignedAttributes` + `CMSSignedData.replaceSigners`）。
- 验签：比对 token 指纹与签名值、校验 TSA 令牌签名与证书有效期；`tsa-required=true` 时缺时间戳判不通过。
- `CertificateChainValidator`：`ca-cert-path` 加载 PEM 信任根；签署时校验 P12 证书链、验签时校验 CMS 证书集合，
  用 PKIX 路径校验；`ca-crl-path`（PEM/DER）校验 CRL 签发者、签名与吊销状态；P12 完整证书链随 CMS 下发。
- 启动校验：`tsa-required` 无 URL 拒绝启动、URL 协议/主机非法拒绝启动、CRL 无信任根拒绝启动、
  信任根不可读拒绝启动、P12 不可链路到信任根拒绝启动。
- 配置：`CONTRACT_ESIGN_TSA_URL`、`TSA_USERNAME/PASSWORD`、`TSA_REQUIRED`、`TSA_TIMEOUT`、
  `CA_CERT_PATH`、`CA_CRL_PATH`。
- 用例：`SignatureServiceTimestampAndChainTest` 5/5（loopback BC TSA：可信链 + 时间戳、TSA 不可用 fail-closed、
  非信任根拒绝、未要求时间戳可通过、要求时间戳时缺失判失败）；`ContractEsignStartupCheckerTest` 11/11。

### 7.2 实名与意愿证据

- `migration-5.67-contract-sign-identity.sql`（幂等）：`real_name_status/real_name_method/real_name_evidence_no`、
  `willingness_method/willingness_evidence_no/willingness_at`，并为存量行补 `UNVERIFIED`。
- 本地签署：`SignExecuteRequest.confirmWillingness` 使用 `@NotNull + @AssertTrue`，缺失或 false 直接 400；
  服务层再兜底校验；记录 `LOGIN_SESSION`（实名未接供应商时 `UNVERIFIED`）与 `SIGN_CONFIRM` 证据编号、意愿时间。
- 三方回调：`EsignCallbackPayload` 增加 `realName*` / `willingness*`，回调落库时缺省 `UNVERIFIED`，
  有意愿证据时默认意愿时间为签署时间。
- 用例：`ContractSignControllerWillingnessTest` 3/3（缺失、false、true）。

### 7.3 印章原图导出

- auth `migration-5.67-contract-seal-download-permission.sql`：新增 233 `sys:contract:seal:download`，
  仅授 `contract_manager` 与 `tenant_admin`（`contract_member`/`contract_signatory` 不授）。
- `GET /contract/seal/{id}/download`：`@RequirePermission` + `@AuditLog(EXPORT)`，附件下载、`no-store`、`nosniff`；
  未上传图片/文件通道失败返回可读错误，不回退内置默认章。
- 前端：`sealApi.download` 走 `requestBlob`，`SealList` 按权限显示「下载原图」并提示审计。
- 用例：`SealServiceImplDownloadTest` 4/4、`ContractSealControllerDownloadTest` 2/2。

### 7.4 回归

- `whale-contract` 全量 47 用例 0 失败（较上轮 +18）；`AuthPermissionCacheInvalidatorTest` 10 用例 0 失败。
- 前端 `whale-contract`、`whale-erp`、`whale-oa`、`whale-mes`、`whale-pdm` lint + build 通过。
