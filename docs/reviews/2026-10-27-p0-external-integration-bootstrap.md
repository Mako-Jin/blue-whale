# P0 外部接入启动检查报告

- 日期：2026-10-27
- 范围：S-04d-2、S-04e-2、V-11、F-03b、V-06 的代码侧准备、凭据预检与联调入口
- 结论：**未关闭任何外部待办**。已将“缺凭据时无法判断缺什么”收敛为可执行预检；SMS 从“跳过”改为可联调网关。

## 1. 本轮改动

### 1.1 告警短信通道（F-03b）

- `AlertNotifyProperties` 新增 `whale.alert.notify.sms`：`enabled/url/token/to/templateCode/signName/timeoutMillis`。
- `AlertNotifyServiceImpl` 新增 `SMS` 通道：
  - JSON POST 到部署侧统一网关，携带 `phones/templateCode/signName/content` 与规则/记录元数据；
  - 配置 token 时发送 `Authorization: Bearer`；
  - 正文超过 480 字符截断，日志中手机号脱敏；
  - 非 2xx 或网络失败只记日志，不阻断告警落库。
- `application.yml` 接 `ALERT_SMS_*` 环境变量。
- 新增 4 个单测：正常发送/超长截断/未启用跳过/缺手机号跳过。

### 1.2 外部接入预检

新增 `scripts/smoke/bw-external-integration-preflight.ps1`：

- 覆盖合同渠道、CA、CRL、P12、TSA、回调密钥、实名/意愿、短信、邮件、Alertmanager 入站与出站；
- 只输出变量是否设置，绝不回显密钥、口令、手机号；
- 支持 `-EnvFile` 直接加载部署机私有 env（只报告加载项数，不打印值）；
- 支持 `-SkipNetwork`（只查配置）与 `-Json`（归档验收）；
- 退出码 `0`=无 BLOCKED，`2`=存在 BLOCKED。

### 1.3 凭据模板与作业单

- `.env.external.example`：P0 所需变量空值模板，明确禁止提交真实值；`.gitignore` 新增 `.env.external` / `.env.external.local`，防止私有凭据误入库。
- `docs/ops/external-integration-onboarding.md`：逐项列出供应商需提供的信息、网关协议、接入步骤与关闭证据。

### 1.4 待办状态

`pending-issues-2026-10-27.md` 中 S-04d-2/S-04e-2/F-03b/V-06/V-11 仅补充代码侧进度与作业单链接，**保持未勾选**。

## 2. 验证

| 验证 | 结果 |
|---|---|
| `mvn -pl backend/whale-ops/whale-alert -am test` | 28 用例 0 失败；`AlertNotifyServiceImplTest` 9/9 |
| Checkstyle | 0 violations |
| 预检空环境 `-SkipNetwork` | 7 BLOCKED：渠道、CA、TSA、回调密钥、实名、意愿、Alertmanager 令牌 |
| 预检合成环境（临时自签 CA/P12 + 假 URL） | CA/P12/TSA/渠道/回调/短信/邮件/Alertmanager 全部 READY；仅实名/意愿 2 项 BLOCKED |
| 预检本地 TCP Listener | 渠道 Base URL、TSA、SMS 网关均 `TCP 可达` |
| 预检 `-EnvFile`（临时文件 11 项） | 成功加载 11 项且未输出值；仅渠道相关项转 READY，剩余 6 项 BLOCKED |
| 预检 `-Json` | 可解析，summary/results 字段完整 |

## 3. 仍未闭环

1. 无真实供应商沙箱地址/appId/appSecret/公网回调/协议文档，V-11 只能保留 loopback 结论。
2. 无持牌 CA PEM/CRL、TSA 账号、正式 P12，S-04d-2 不能实机签署验签。
3. 无实名/意愿供应商协议，S-04e-2 不能写适配器；现有字段不能替代真实认证。
4. 无真实短信网关/模板签名/手机号，F-03b 不能证明实际送达。
5. 无 SMTP 账号与 Alertmanager 生产令牌，V-06 不能完成实机取证。

## 4. 下一步

1. 业务/采购提供 `.env.external.example` 中首批凭据，优先选择一家电子签渠道。
2. 运行预检，直到仅剩实名/意愿 BLOCKED。
3. 对拿到协议的渠道补真实请求/响应映射与契约测试，再跑 create/query/download/callback。
4. 短信网关联调时先发一条测试告警，保留网关回执与手机截图。
5. 全部通过后按作业单 §5 归档证据并勾掉对应待办。
