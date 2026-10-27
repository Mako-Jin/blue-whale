# 待修复问题与待办清单（2026-10-27）

> 口径：只登记**当前仓库仍存在**的缺口；已闭环项保留原文并加删除线 + ✅ 完成日期，便于追溯。
> 唯一入口：本文件；每完成一项即划掉一项，并在 [priority-execution-ledger-2026-10-25.md](priority-execution-ledger-2026-10-25.md) 追加一行进度。

## 一、安全与一致性

- [x] ~~**T1 菜单权限过滤**：平台壳/运维台/后台静态菜单未按权限裁剪，出现「菜单可见但接口 403」~~ ✅ 2026-10-27（[报告](../reviews/2026-10-27-p1-menu-permission-filter.md)）
- [x] ~~**T2 埋点归属契约**：`/audit/log/*/batch` 在缺少 `X-User-Id` 时回落请求体 `userId`，匿名可伪造归属~~ ✅ 2026-10-27（[报告](../reviews/2026-10-27-p1-log-attribution-header-only.md)）
- [x] ~~**T3 PM 角色模板化**：`pm_manager` / `pm_member` 仅存在于平台租户，业务租户无 PM 角色可分配~~ ✅ 2026-10-27（代码 + 5.66 迁移 + 本机 MySQL 实机验证 + smoke 通过，[报告](../reviews/2026-10-27-p1-pm-role-template.md)）
- [x] ~~**T3-附 存量租户 tenant_admin 165–167 补授**：核实结论——165/166 随开通下发，5.66 再做一次幂等补齐；167（`sys:user:tenant`）按平台专属设计不向业务租户下发~~ ✅ 2026-10-27
- [x] ~~**S-01 撤销时间秒级精度**：解绑/停用/改密后同一秒内重新登录，新令牌会被误判已撤销（P1-8/9/11 边界）~~ ✅ 2026-10-27（新令牌写毫秒 `iatMs`、撤销键写毫秒；旧秒级键按秒粒度兼容；网关/auth 两侧统一 `TokenTimeSupport`，同秒新令牌可放行，[报告](../reviews/2026-10-27-t7-t15-hardening.md)）
- [x] ~~**S-02 Redis 降级放行**：撤销键、租户状态/到期、权限缓存不可读时按「未撤销/可用」处理，仅告警；需确认安全取舍或改为拒绝~~ ✅ 2026-10-27（新增 `whale.security.degradation.fail-closed` 统一开关，默认仍可用性优先并告警，开启后按拒绝；已接入用户/成员撤销、租户状态、令牌黑名单，权限缓存保持既有回源/告警路径）
- [x] ~~**S-03 租户到期时区**：按 JVM 默认时区换算，跨时区部署会误判过期；建议显式 UTC~~ ✅ 2026-10-27（`TenantExpiryClock` 固定 UTC，域内到期比较、应用可用性判断与 Redis 到期 Unix 秒统一口径；存量到期时间需按 UTC 校准）
- [x] ~~**S-04a 印章预览水印**：印章预览未加半透明水印~~ ✅ 2026-10-27（预览最长边 240px + 半透明水印 + `no-store/nosniff`，前端预览弹窗，[报告](../reviews/2026-10-27-p1-contract-esign-sandbox-and-compliance.md)）
- [x] ~~**S-04b P12 数字签名可用性**：未配置 P12 时 `algorithm=NONE` 仅视觉章~~ ✅ 2026-10-27（启动校验 PKCS#12；修复 BC 1.77/1.78 混用；改为标准 PDF 签名域并做真实验签；未配置时仍 NONE，三方渠道由供应商签署）
- [x] ~~**S-04c 国密 SM2 评估**：未评估~~ ✅ 2026-10-27（BC 可支持 SM2/SM3，但依赖 SM2 证书、PDF 阅读器与渠道支持，暂不落地）
- [x] ~~**S-04d-1 CA/TSA 技术接入**：未接 CA 证书链、RFC 3161 时间戳、信任根与 CRL~~ ✅ 2026-10-27（TSA 客户端 + CMS 未签名属性时间戳、TSA 失败 fail-closed、`tsa-required` 缺失时间戳判不通过；PEM 信任根 PKIX 链校验 + CRL 吊销检查 + CMS 下发完整证书链；启动校验 TSA/CA/P12 配置，9 个新单测通过）
- [ ] **S-04d-2 持牌 CA/TSA 实机联调**：需 CA 证书链 PEM、CRL、TSA 地址与账号口令；配好 `CONTRACT_ESIGN_CA_CERT_PATH` / `CONTRACT_ESIGN_TSA_URL` 后需跑真实签署验签
- [x] ~~**S-04e-1 实名与意愿认证字段/本地证据**：`ct_sign_record` 缺实名结果/校验方式/证据编号，本地渠道仅有登录态~~ ✅ 2026-10-27（5.67 迁移 6 字段 + 实体/VO；本地签署强制勾选意愿并记录 `SIGN_CONFIRM`/`LOGIN_SESSION` 证据；三方回调映射实名/意愿回执；接口层缺省或 false 直接 400）
- [ ] **S-04e-2 真实实名/意愿供应商接入**：需 ID_CARD_OCR/FACE/VIDEO/SMS 供应商接口与回调证据字段约定，接入后把本地 `UNVERIFIED` 升级为 `VERIFIED`
- [x] ~~**S-04f 印章原图导出权限**：设计要求的 `sys:contract:seal:download` 未定义，原图下载接口未实现~~ ✅ 2026-10-27（5.67 权限种子 + 仅授 `contract_manager`/`tenant_admin`；`GET /contract/seal/{id}/download` 附件下载 + `no-store/nosniff` + 审计；前端按权限显示「下载原图」；6 个新用例）

- [x] ~~**S-05 审计历史归属不可追溯**：P1-14 前已落库的伪造归属无法纠正~~ ✅ 2026-10-27（不可回溯，不做数据改写以免误伤真实记录；新入站只信 `X-User-Id`，历史数据仅作参考）
- [x] ~~**S-06 绕过网关直连业务端口**：不受身份头/撤销/租户校验保护（部署边界，需网络层收口）~~ ✅ 2026-10-27（转为上线必检部署边界，清单与示例见 [network-boundary.md](../design/network-boundary.md)）
- [x] ~~**S-07 熔断半开探测**：仅单测，运行态未取证；多实例熔断/动态路由快照为进程内（已接受）~~ ✅ 2026-10-27（补 `CLOSED/OPEN/HALF_OPEN` 状态迁移日志，运行态可取证；真实故障注入取证转 V-14；进程内快照维持已接受）

## 二、功能与最小实现

- [x] ~~**F-01 归档历史补锁不自动**：需逐租户手工 `POST /archive/internal/items/lock-reconcile`；条目停留「待整理」，档号需人工确认~~ ✅ 2026-10-27（新增按租户/游标分批定时补扫，`whale.archive.lock-reconcile.*`，默认关闭、启用后自动执行；档号仍需人工确认，不属本项）
- [x] ~~**F-02 job 广播断连无重对齐**：漏收消息依赖下一次变更；auth 侧已有 `ReconcileJob`~~ ✅ 2026-10-27（`JobScheduler.reconcileWithDatabase` + 5 分钟重对齐任务，取消已停用本地调度、cron/依赖变化自动重注册，异常不阻断调度）
- [x] ~~**F-03 告警通道**：SMS 未接、无 `fingerprint` 去重、入站记录固定租户 1、邮件纯文本~~ ✅ 2026-10-27（指纹窗口去重 + `alert_record.fingerprint` 幂等迁移、按标签 `tenant_id/tenant` 归属租户（租户插件默认启用，落库/去重按 `TenantContext` 过滤）、业务租户无规则时回落平台租户共享规则、HTML 邮件与转义、Webhook 带 `tenantId`；[报告](../reviews/2026-10-27-t7-t15-hardening.md)）
- [ ] **F-03b 告警 SMS 通道**：需短信供应商账号、模板/签名报备与实机联调；当前仅邮件与 Webhook
- [ ] **F-04 whale-pay 最小实现**：仅订单 CRUD + 支付/取消占位，无真实渠道、对账、退款
- [ ] **F-05 whale-config 空壳**：仅启动类，无接口/路由（既定「冻结扩张」，需明确结案或排期）
- [ ] **F-06 whale-monitor 能力边界**：仅服务/实例列表，采集存储由外部 Prometheus 承担
- [ ] **F-07 whale-games-core TODO**：主循环与资源清理未实现，且无游戏前端应用
- [ ] **F-08 whale-common-tools 残留 TODO**：`request.ts` / `date.ts` 占位（`request.ts` 与 `@bluewhale/http` 重复）
- [ ] **F-09 whale-common-three 无消费方**：WebGL 渲染未验收，控制器/后处理/性能监控未封装
- [x] ~~**F-10 业务子应用按钮级权限**：erp/oa/mes/pdm/pm/contract 等子应用内「新增/编辑/删除」未按权限码隐藏（本轮 T1 只收口模块级导航）~~ ✅ 2026-10-27（contract/pm 原有；本轮为 erp/oa/mes/pdm 增加 `utils/permission.ts` 并按 `sys:{app}:*:add/edit/delete` 隐藏操作按钮，4 个子应用 lint+build 通过；接口仍由 `@RequirePermission` 兜底）
- [ ] **F-11 RabbitMQ 管理插件未启用**：死信队列无法从控制台重放

## 三、验证与覆盖

- [ ] **V-01 表单↔工作流跨模块 E2E**：现有 `form-loop` / `workflow-loop` 各自闭环，缺「填表单→发起→审批」串联用例
- [ ] **V-02 form-designer 拖拽人工复核**：8002 指针拖拽自动化不可复现，未人工复核（8003 已复核）
- [ ] **V-03 admin 角色/菜单删除与角色分配 UI 未逐项复验**：API 级已过
- [ ] **V-04 Agent 服务目录重建 <5s 无持续回归**
- [x] ~~**V-05 Redis 双实例广播 + 断连恢复实机联调**：P1-3/5/7 均未实机~~ ✅ 2026-10-27（双实例 RTopic 双向可达；Redis 停机登记待重对齐、恢复后 `清除=1/剩余=0`；重启后订阅自动恢复；待重对齐 Key 已落盘并在重启后自动读回，10 个单测通过，[报告](../reviews/2026-10-27-p1-auth-permission-cache-real-machine.md)、[脚本](../../scripts/smoke/bw-auth-permission-cache-broadcast-smoke.ps1)）
- [ ] **V-06 告警全链路实机取证**：Prometheus 规则触发 → Alertmanager → whale-alert → 邮件/Webhook（HTML 邮件与指纹去重已代码化，邮件服务器/SMS 仍需环境）
- [ ] **V-07 Gitee Go 流水线实机触发**：JDK 21 / Node 24 插件兼容
- [ ] **V-08 GHCR 推送实跑**：需 registry 凭据
- [ ] **V-09 prod compose 覆盖层实跑**：需宿主机 Let's Encrypt 证书
- [ ] **V-10 全服务镜像实机构建**：CI matrix 已覆盖，缺本机实跑
- [ ] **V-11 合同供应商沙箱联调**：协议级 loopback 沙箱已通过（创建 → 查询 → 下载 → 业务错误码，服务端重算 HMAC）；真实 esignbao/fadada/ssq 沙箱仍阻塞于沙箱地址、appId/appSecret、回调公网地址与真实协议文档（[报告](../reviews/2026-10-27-p1-contract-esign-sandbox-and-compliance.md)）
- [x] ~~**V-12 归档历史条目补扫**：需运维逐租户触发~~ ✅ 2026-10-27（新增自动补扫任务，`whale.archive.lock-reconcile.enabled=true` 后按租户游标分批执行；手工接口保留）
- [x] ~~**V-13 执行 `migration-5.66-pm-role-template.sql` 并跑 `bw-auth-business-role-grant-smoke.ps1`**：本机 MySQL 未启动，幂等与授权数量尚未实机验证~~ ✅ 2026-10-27（MySQL 8.0.26 连跑两遍 exit 0；default/acme 均 15 个业务角色、`pm_manager=32`/`pm_member=17`；acme `tenant_admin` PM 覆盖 34；smoke `RESULT: PASSED`）
- [ ] **V-14 熔断半开运行态取证**：网关接入故障服务，观察 `CLOSED→OPEN→HALF_OPEN→CLOSED/OPEN` 日志与指标（代码侧状态迁移日志已补，S-07）

## 四、文档同步

- [x] ~~**D-01 缺 `docs/devlog/2026-10-27.md`**：P1 #1–#14 只有检查报告与台账进度行~~ ✅ 2026-10-27
- [x] ~~**D-02 台账执行队列未含 P1 项**：队列表止于序号 27，P1 #1–#14 只在进度记录~~ ✅ 2026-10-27（队列尾注指向进度记录，明确不双维护）
- [ ] **D-03 P1 #1/#2 无检查报告**：合同签章护栏与开放平台接入只有台账行
- [ ] **D-04 `todo-inventory-2026-10-23.md` 已过期**：多项已闭环，应归档或重生成
- [ ] **D-05 `service-catalog.md` C-06 滞后**：仍写 erp/mes/pdm/oa 未配数据源，实际 10-26 已完成
- [ ] **D-06 合同设计稿 M3.6 遗留过时**：仍写「原件未做文件锁定」，P1 #6 已修复
