# P0 外部凭据与供应商接入作业单

- 日期：2026-10-27
- 覆盖：S-04d-2（CA/TSA）、S-04e-2（实名/意愿）、V-11（合同供应商沙箱）、F-03b（告警短信）、V-06（告警全链路）
- 关联：[待办清单](../plans/pending-issues-2026-10-27.md)、[电子签设计](../plans/wave5.61-contract-esign.md)、
  [P1 合同签章检查报告](../reviews/2026-10-27-p1-contract-esign-sandbox-and-compliance.md)

## 1. 结论先行

> **2026-10-28 口径更新**：经业务确认，短期无真实实名/意愿供应商、CA/TSA 账号、合同供应商沙箱、短信/SMTP 账号。上述五项**不再等待外部凭据**：代码侧保留本地模拟、loopback 契约测试与部署侧网关适配，真实联调转为「商务条件成熟后另立任务 / 部署侧验收」。详见 [架构决策](../plans/2026-10-28-crm-srm-and-message-service-decisions.md)。

| 项 | 代码侧 | 外部输入 | 结论 |
|---|---|---|---|
| S-04d-2 CA/TSA | ✅ 已完成 | ⛔ 持牌 CA PEM/CRL、TSA 地址与账号 | **仅模拟**：保留本地自签 P12/CA/TSA 与 fail-closed 校验 |
| S-04e-2 实名/意愿 | ✅ 字段、本地证据、回调映射已完成 | ⛔ 供应商接口协议、证据字段、测试账号 | **仅 mock/本地证据**：`LOGIN_SESSION`/`SIGN_CONFIRM` 保留 |
| V-11 三方沙箱 | ✅ 协议 v1 loopback 沙箱与渠道配置已完成 | ⛔ 三家供应商沙箱地址、appId/appSecret、公网回调 | **不做真实沙箱**：保留 loopback 协议沙箱 |
| F-03b 告警短信 | ✅ 统一 HTTP 网关适配已完成 | ⛔ 短信网关/供应商账号、模板、签名、手机号 | **mock/契约测试**：真实送达转部署侧验收 |
| V-06 告警全链路 | ✅ 邮件/Webhook/SMS/去重已具备 | ⛔ SMTP 或短信真实账号、Alertmanager 令牌 | **mock/契约覆盖**：真实全链路转部署验收清单 |

## 2. 代码侧已具备什么

### 2.1 合同电子签

- 渠道：`local / esignbao / fadada / ssq`，通过 `CONTRACT_ESIGN_PROVIDER` 切换，业务流程不变。
- 渠道配置非空校验：非 dev 环境缺 `base-url/app-id/app-secret` 直接拒绝启动。
- CA：`CONTRACT_ESIGN_CA_CERT_PATH` 支持 PEM 信任根，签署/验签做 PKIX 链校验。
- CRL：`CONTRACT_ESIGN_CA_CRL_PATH` 支持 PEM/DER 吊销检查。
- P12：启动时校验文件、口令、私钥、有效期、证书链。
- TSA：`CONTRACT_ESIGN_TSA_URL` 支持 RFC 3161，`TSA_REQUIRED=true` 时缺时间戳判失败。
- 回调：`CONTRACT_ESIGN_CALLBACK_SECRET` HMAC、时间窗、幂等、拒签与证据链。
- 实名/意愿：签章记录已有 `real_name_status/method/evidence_no` 与
  `willingness_method/evidence_no/willingness_at`；回调 payload 已映射这些字段。

### 2.2 告警通知

- `EMAIL`：Spring Mail，未配置时跳过。
- `WEBHOOK`：JSON POST。
- `SMS`：本轮新增统一 HTTP 网关，配置项见 §3.3；供应商专有签名算法放在网关，平台不持有短信 AccessKey。
- 失败语义：任何通道失败只记日志，不影响告警落库。

## 3. 需要向供应商/运维索取的信息

### 3.1 合同电子签渠道（V-11）

任选一家先接，另外两家按同样格式补齐。

| 信息 | 变量 | 说明 |
|---|---|---|
| 沙箱 Base URL | `CONTRACT_ESIGN_<PROVIDER>_BASE_URL` | 必须可从平台出网访问 |
| App ID / App Secret | `..._APP_ID` / `..._APP_SECRET` | 测试账号，明确有效期与权限范围 |
| 回调地址 | `..._NOTIFY_URL` | 公网 HTTPS，供应商白名单可达 |
| 签署页地址模板 | `..._SIGN_URL` | 如供应商返回签署短链则留空 |
| 真实协议文档 | 不入库 | 创建/查询/下载字段、签名算法、时间戳格式、错误码 |
| 测试印章/证书 | 不入库 | 供应商测试账号下的可用印章 |
| 回调签名算法 | 不入库 | 现有平台按 BlueWhale 协议 v1 HMAC；与供应商不一致时需适配 |

**关键**：如果供应商真实协议与 BlueWhale 开放平台协议 v1 不同，不能只改环境变量，需要按真实文档覆盖
`create/query/download` 路径、请求签名与响应映射；这部分必须在拿到文档后实现并由契约测试覆盖。

### 3.2 CA / TSA（S-04d-2）

| 信息 | 变量 | 说明 |
|---|---|---|
| CA 信任根 PEM | `CONTRACT_ESIGN_CA_CERT_PATH` | 可包含多张证书；不能是自签测试根 |
| CRL/OCSP | `CONTRACT_ESIGN_CA_CRL_PATH` | PEM/DER；至少提供可更新的 CRL |
| 平台 P12 | `CONTRACT_ESIGN_P12_PATH` / `P12_PASSWORD` | 三方签署可留空；本地签署必需 |
| TSA 地址 | `CONTRACT_ESIGN_TSA_URL` | RFC 3161，生产必须可达 |
| TSA Basic 认证 | `TSA_USERNAME` / `TSA_PASSWORD` | 供应商要求时成对配置 |
| TSA 证书链 | 不入库 | 用于核验 TSA 令牌签名 |

### 3.3 告警短信（F-03b）

平台只要求部署侧提供一个统一 HTTP 网关，避免在业务服务内绑定某家短信 SDK。

请求示例：

```json
{
  "phones": ["13800000000"],
  "templateCode": "BLUEWHALE_ALERT",
  "signName": "蓝鲸",
  "content": "[BlueWhale][WARN] CPU 使用率过高: CPU 使用率 92% (target=whale-gateway, status=FIRING)",
  "ruleCode": "cpu_high",
  "ruleName": "CPU 使用率过高",
  "level": "WARN",
  "target": "whale-gateway",
  "status": "FIRING",
  "tenantId": 1,
  "triggerTime": "2026-10-27T19:00:00"
}
```

- 认证：配置 `ALERT_SMS_TOKEN` 时发送 `Authorization: Bearer <token>`。
- 成功：HTTP 2xx；非 2xx 只记日志，不阻断告警落库。
- 配置：`ALERT_SMS_ENABLED/URL/TOKEN/TO/TEMPLATE_CODE/SIGN_NAME/TIMEOUT`。
- 供应商模板中未使用的字段可忽略，但 `phones/templateCode/signName/content` 必须能映射。

### 3.4 实名 / 意愿（S-04e-2）

需要供应商提供以下内容后才能写适配器：

1. 实名方式：`ID_CARD_OCR` / `FACE` / `VIDEO` / `SMS` 中的哪一种或组合；
2. 发起接口、查询接口、回调报文与签名算法；
3. 证据编号规则、证据留存地址/有效期；
4. 失败重试、人工复核、拒签与撤回语义；
5. 回调中可稳定映射到以下字段的路径：

```text
realNameStatus / realNameMethod / realNameEvidenceNo
willingnessMethod / willingnessEvidenceNo / willingnessAt
```

在协议未确认前，不得用自定义字段或前端勾选冒充实名/意愿证据。

### 3.5 告警邮件与全链路（V-06）

| 信息 | 变量 |
|---|---|
| SMTP 主机/端口 | `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` |
| SMTP 账号/口令 | `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` |
| 发件人/收件人 | `ALERT_EMAIL_FROM` / `ALERT_EMAIL_TO` |
| Alertmanager 入站令牌 | `ALERT_WEBHOOK_TOKEN`（不能是默认 `bluewhale-alert-token`） |
| 出站 Webhook | `ALERT_WEBHOOK_ENABLED/URL`（可选） |

## 4. 接入步骤

1. 复制 `.env.external.example` 为部署机私有文件，填值，禁止提交。
2. 先选择一家电子签渠道，填 `CONTRACT_ESIGN_PROVIDER` 与该渠道四项凭据。
3. 填 CA、CRL、TSA、P12 与回调密钥；`CONTRACT_ESIGN_TSA_REQUIRED=true`。
4. 填短信或邮件至少一个真实通道；短信网关先确认模板与签名已报备。
5. 跑预检：

```powershell
# 直接加载部署机私有 env 文件（推荐；脚本只报告是否设置，不打印值）
pwsh -NoProfile -File scripts/smoke/bw-external-integration-preflight.ps1 -EnvFile .env.external

# 或使用当前进程已导出的环境变量
pwsh -NoProfile -File scripts/smoke/bw-external-integration-preflight.ps1

# 离线只查配置，不发起 TCP 连接
pwsh -NoProfile -File scripts/smoke/bw-external-integration-preflight.ps1 -EnvFile .env.external -SkipNetwork
```

6. 预检无 `BLOCKED` 后，再按供应商要求跑真实签署/回调/告警发送；任何一项失败都不得用 loopback 结果替代。
7. 把预检 JSON、供应商回调日志、签署任务号、验签结果、告警送达截图/日志归档到验收记录。

## 5. 各待办的关闭证据

| 待办 | 关闭证据 |
|---|---|
| S-04d-2 | 持牌 CA 证书链校验通过 + TSA 时间戳验签通过 + 一份真实签署验签报告 |
| S-04e-2 | 供应商实名/意愿回调落库，`realNameStatus=VERIFIED` 且证据编号可追溯 |
| V-11 | 真实沙箱 create/query/download/callback 全链路通过，记录任务号与错误码 |
| F-03b | 真实短信网关收到告警并回 2xx，手机实际收到短信 |
| V-06 | Prometheus 规则触发 → Alertmanager → whale-alert → 邮件/短信实际送达 |

## 6. 当前阻塞

- 仓库没有供应商沙箱地址、appId/appSecret、回调公网地址、真实协议文档、测试印章。
- 仓库没有持牌 CA PEM/CRL、TSA 地址账号、P12 正式证书。
- 仓库没有短信网关/供应商账号、模板签名与接收手机号。
- 仓库没有 SMTP 账号与 Alertmanager 生产令牌。
- 实名/意愿供应商协议未提供，代码侧只能保留证据字段，不能伪造认证能力。

以上任一输入到位后，先跑预检，再按 §4 推进，不需要改业务流程。
