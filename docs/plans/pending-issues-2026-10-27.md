# 待修复问题与待办清单（2026-10-27）

> 口径：只登记**当前仓库仍存在**的缺口；已闭环项保留原文并加删除线 + ✅ 完成日期，便于追溯。
> 唯一入口：本文件；每完成一项即划掉一项，并在 [priority-execution-ledger-2026-10-25.md](priority-execution-ledger-2026-10-25.md) 追加一行进度。
> 2026-10-28 范围决策：真实实名/意愿、CA/TSA、合同供应商沙箱、电子签凭据均**仅 mock/模拟**；Gitee Go **不做**；供应商管理**不归 CRM**，按用户指令落 **ERP 供应商主数据**（F-13）；统一消息服务落 `whale-message` M0（F-12）并完成 M1 模板/重试/站内信（F-14）。详见 [架构决策](2026-10-28-crm-srm-and-message-service-decisions.md)。

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
- [x] ~~**S-04d-2 持牌 CA/TSA 实机联调**：需 CA 证书链 PEM、CRL、TSA 地址与账号口令；配好 `CONTRACT_ESIGN_CA_CERT_PATH` / `CONTRACT_ESIGN_TSA_URL` 后需跑真实签署验签（凭据清单与预检见 [外部接入作业单](../ops/external-integration-onboarding.md)）~~ ✅ 2026-10-28（**范围决策：短期无持牌机构账号，仅模拟**；保留本地自签 P12/CA/TSA 与 fail-closed 校验，真实联调待商务条件成熟后另立任务）
- [x] ~~**S-04e-1 实名与意愿认证字段/本地证据**：`ct_sign_record` 缺实名结果/校验方式/证据编号，本地渠道仅有登录态~~ ✅ 2026-10-27（5.67 迁移 6 字段 + 实体/VO；本地签署强制勾选意愿并记录 `SIGN_CONFIRM`/`LOGIN_SESSION` 证据；三方回调映射实名/意愿回执；接口层缺省或 false 直接 400）
- [x] ~~**S-04e-2 真实实名/意愿供应商接入**：需 ID_CARD_OCR/FACE/VIDEO/SMS 供应商接口与回调证据字段约定，接入后把本地 `UNVERIFIED` 升级为 `VERIFIED`（字段/回调已就绪，协议与证据清单见 [外部接入作业单](../ops/external-integration-onboarding.md)）~~ ✅ 2026-10-28（**范围决策：短期无供应商，仅 mock/本地证据**；保留 `LOGIN_SESSION`/`SIGN_CONFIRM` 与回调字段映射，真实 `VERIFIED` 升级待供应商协议到位）
- [x] ~~**S-04f 印章原图导出权限**：设计要求的 `sys:contract:seal:download` 未定义，原图下载接口未实现~~ ✅ 2026-10-27（5.67 权限种子 + 仅授 `contract_manager`/`tenant_admin`；`GET /contract/seal/{id}/download` 附件下载 + `no-store/nosniff` + 审计；前端按权限显示「下载原图」；6 个新用例）

- [x] ~~**S-05 审计历史归属不可追溯**：P1-14 前已落库的伪造归属无法纠正~~ ✅ 2026-10-27（不可回溯，不做数据改写以免误伤真实记录；新入站只信 `X-User-Id`，历史数据仅作参考）
- [x] ~~**S-06 绕过网关直连业务端口**：不受身份头/撤销/租户校验保护（部署边界，需网络层收口）~~ ✅ 2026-10-27（转为上线必检部署边界，清单与示例见 [network-boundary.md](../design/network-boundary.md)）
- [x] ~~**S-07 熔断半开探测**：仅单测，运行态未取证；多实例熔断/动态路由快照为进程内（已接受）~~ ✅ 2026-10-27（补 `CLOSED/OPEN/HALF_OPEN` 状态迁移日志，运行态可取证；真实故障注入取证转 V-14；进程内快照维持已接受）

## 二、功能与最小实现

- [x] ~~**F-01 归档历史补锁不自动**：需逐租户手工 `POST /archive/internal/items/lock-reconcile`；条目停留「待整理」，档号需人工确认~~ ✅ 2026-10-27（新增按租户/游标分批定时补扫，`whale.archive.lock-reconcile.*`，默认关闭、启用后自动执行；档号仍需人工确认，不属本项）
- [x] ~~**F-02 job 广播断连无重对齐**：漏收消息依赖下一次变更；auth 侧已有 `ReconcileJob`~~ ✅ 2026-10-27（`JobScheduler.reconcileWithDatabase` + 5 分钟重对齐任务，取消已停用本地调度、cron/依赖变化自动重注册，异常不阻断调度）
- [x] ~~**F-03 告警通道**：SMS 未接、无 `fingerprint` 去重、入站记录固定租户 1、邮件纯文本~~ ✅ 2026-10-27（指纹窗口去重 + `alert_record.fingerprint` 幂等迁移、按标签 `tenant_id/tenant` 归属租户（租户插件默认启用，落库/去重按 `TenantContext` 过滤）、业务租户无规则时回落平台租户共享规则、HTML 邮件与转义、Webhook 带 `tenantId`；[报告](../reviews/2026-10-27-t7-t15-hardening.md)）
- [x] ~~**F-03b 告警 SMS 通道**：代码侧已新增统一 HTTP 短信网关（`SMS` 通道、Bearer 鉴权、内容截断、手机号脱敏，4 个单测）；仍缺真实网关/供应商账号、模板签名报备、接收手机号与实机送达证据（[外部接入作业单](../ops/external-integration-onboarding.md)）~~ ✅ 2026-10-28（**范围决策：无真实供应商**；统一 HTTP 网关 + loopback 契约测试已覆盖协议，真实送达转部署侧验收，不再作为代码待办）
- [x] ~~**F-04 whale-pay 最小实现**：仅订单 CRUD + 支付/取消占位，无真实渠道、对账、退款~~ ✅ 2026-10-28（补齐金额/渠道校验、幂等撞键、mock 渠道网关、PAID→REFUNDED 全额退款、`pay_transaction` 流水、本地/渠道对账；`pay_order` 补 `tenant_id` 与租户内唯一键、补 `audit_log`；权限 244–245 并给 `tenant_admin` 补齐 70–73；17 个单测通过；本地起服经网关 8 项实机检查 PASS，详见 [检查报告](../reviews/2026-10-28-wave5.71-pay-flow.md)）
- [x] ~~**F-12 无统一消息服务**：IM/邮件/短信/站内信各自为政，缺模板、幂等、投递记录与多渠道编排~~ ✅ 2026-10-28（新增 `whale-message` M0：模板渲染、INAPP/EMAIL/SMS/IM 渠道适配、幂等键、投递记录、mock 默认开关；9 个单测通过；[检查报告](../reviews/2026-10-28-wave5.68-message-m0.md)、[架构决策](2026-10-28-crm-srm-and-message-service-decisions.md)）
- [x] ~~**F-13 供应商管理归属未落地**：供应商主数据缺 CRUD/权限/菜单/前端页面，且不能塞进 CRM~~ ✅ 2026-10-28（按 [架构决策](2026-10-28-crm-srm-and-message-service-decisions.md) 落 ERP：`erp_supplier` + 租户内编码唯一 + 审计 + 权限 235–238 + 菜单 32 + 物料/供应商双页前端；4 个单测通过，ERP 前端 lint/build 通过；本地起服执行建表与权限迁移，网关 `/erp/supplier/**` 5 项实机检查 PASS；[检查报告](../reviews/2026-10-28-wave5.69-erp-supplier.md)）
- [x] ~~**F-14 消息服务仅有发送内核**：缺模板管理、失败重试、站内信收件箱~~ ✅ 2026-10-28（M1：模板 CRUD（权限 239–242）、`POST /message/record/{id}/retry`（权限 243，mock 拒绝重试）、`/message/inapp/**` 本人收件箱/未读数/标记已读（`read_flag` 幂等迁移）；20 个单测通过；本地起服完成建表/迁移并通过模板 CRUD、站内信发送/收件箱/已读/未读与 mock 重试拒绝的实机检查；[检查报告](../reviews/2026-10-28-wave5.70-message-m1.md)）
- [x] ~~**F-15 消息/支付前端缺口**：模板管理页、站内信消息中心、支付单管理页均只有后端接口~~ ✅ 2026-10-28（admin 新增「消息模板管理」（权限 239–242）与「支付单管理」（权限 70–73、244–245，列表/新增/支付/取消/退款 + 流水/对账抽屉）两页；portal 新增「消息中心」（未读角标 30s 轮询 + 分页列表 + 标记已读）；菜单 33/34 由 `migration-5.72-admin-frontend-menus.sql` 落库并经 `/auth/me` 实机确认可见；lint/build 全过，网关接口与页面渲染均 PASS；[检查报告](../reviews/2026-10-28-wave5.72-admin-frontend-message-pay.md)）
- [x] ~~**F-05 whale-config 空壳**：仅启动类，无接口/路由~~ ✅ 2026-10-28（**Wave 5.73 解冻并完善**：定位为业务配置管理服务，与 Nacos 互补——`bluewhale_config` 库（config_item/config_item_history + audit/api_log）、CRUD + 版本历史 + 回滚（回滚产生新版本）+ 租户隔离、权限 246–250 + 菜单 35 + admin「业务配置管理」页；恢复网关 `/config/**` 路由、CI matrix、Prometheus job；17 个单测通过，网关实机 smoke 与浏览器全流程 PASS；[检查报告](../reviews/2026-10-28-wave5.73-whale-config-and-v06-alert-evidence.md)、[冻结清单（已移出）](../design/frozen-optional-modules.md)）
- [x] ~~**F-06 whale-monitor 能力边界**：仅服务/实例列表，采集存储由外部 Prometheus 承担~~ ✅ 2026-10-28（**边界结案**：只读聚合 Nacos 实例，不采集/不落库/不告警；[能力边界](../design/whale-monitor-boundary.md)）
- [x] ~~**F-07 whale-games-core TODO**：主循环与资源清理未实现，且无游戏前端应用~~ ✅ 2026-10-28（**冻结**：无消费方，不升级/不扩张，代码保留；[冻结清单](../design/frozen-optional-modules.md)）
- [x] ~~**F-08 whale-common-tools 残留 TODO**：`request.ts` / `date.ts` 占位（`request.ts` 与 `@bluewhale/http` 重复）~~ ✅ 2026-10-27（确认无消费方后删除两文件及导出，移除 `dayjs` 依赖；`pnpm --filter @bluewhale/whale-common-tools build` 与 `lint` 通过）
- [x] ~~**F-09 whale-common-three 无消费方**：WebGL 渲染未验收，控制器/后处理/性能监控未封装~~ ✅ 2026-10-28（**冻结**：无消费方，不升级/不扩张，代码保留；[冻结清单](../design/frozen-optional-modules.md)）
- [x] ~~**F-10 业务子应用按钮级权限**：erp/oa/mes/pdm/pm/contract 等子应用内「新增/编辑/删除」未按权限码隐藏（本轮 T1 只收口模块级导航）~~ ✅ 2026-10-27（contract/pm 原有；本轮为 erp/oa/mes/pdm 增加 `utils/permission.ts` 并按 `sys:{app}:*:add/edit/delete` 隐藏操作按钮，4 个子应用 lint+build 通过；接口仍由 `@RequirePermission` 兜底）
- [x] ~~**F-11 RabbitMQ 管理插件未启用**：死信队列无法从控制台重放（2026-10-28 核实：`infrastructure/docker/docker-compose.yml` 已使用 `rabbitmq:4-management-alpine` 并映射 15672，容器部署路径已具备插件；本机既有 RabbitMQ 节点控制面不可用，需管理员 cookie/服务账号或迁移到容器节点）~~ ✅ 2026-10-28（**结案**：容器路径使用 `rabbitmq:4-management-alpine` + 15672 映射 + `rabbitmq-diagnostics ping` 健康检查，`docker compose config` exit 0；本机旧节点控制面不作为交付路径，生产统一使用容器节点）

## 三、验证与覆盖

- [x] ~~**V-01 表单↔工作流跨模块 E2E**：现有 `form-loop` / `workflow-loop` 各自闭环，缺「填表单→发起→审批」串联用例~~ ✅ 2026-10-28（新增 `form-workflow-loop.spec.ts`：API 造表/表单/流程，UI 填报→发起→审批通过，API 核验 `businessKey` 与字段值；三条新 E2E 合跑 3 passed/51.5s；详见 [检查报告](../reviews/2026-10-28-local-verification-batch.md)）
- [x] ~~**V-02 form-designer 拖拽人工复核**：8002 指针拖拽自动化不可复现，未人工复核（8003 已复核）~~ ✅ 2026-10-28（新增 `form-designer-drag.spec.ts`：真实鼠标 `dragTo` 调色板→画布，保存后 API 核验字段；截图人工复核通过；详见 [检查报告](../reviews/2026-10-28-local-verification-batch.md)）
- [x] ~~**V-03 admin 角色/菜单删除与角色分配 UI 未逐项复验**：API 级已过~~ ✅ 2026-10-28（新增 `admin-role-menu-ui.spec.ts`：角色权限分配、用户角色分配、角色删除、菜单删除全 UI 闭环 + API 核验；详见 [检查报告](../reviews/2026-10-28-local-verification-batch.md)）
- [x] ~~**V-04 Agent 服务目录重建 <5s 无持续回归**~~ ✅ 2026-10-28（`FactCollectorTest` 新增 5s 预算回归，实跑 57ms；详见 [检查报告](../reviews/2026-10-28-local-verification-batch.md)）
- [x] ~~**V-05 Redis 双实例广播 + 断连恢复实机联调**：P1-3/5/7 均未实机~~ ✅ 2026-10-27（双实例 RTopic 双向可达；Redis 停机登记待重对齐、恢复后 `清除=1/剩余=0`；重启后订阅自动恢复；待重对齐 Key 已落盘并在重启后自动读回，10 个单测通过，[报告](../reviews/2026-10-27-p1-auth-permission-cache-real-machine.md)、[脚本](../../scripts/smoke/bw-auth-permission-cache-broadcast-smoke.ps1)）
- [x] ~~**V-06 告警全链路实机取证**：Prometheus 规则触发 → Alertmanager → whale-alert → 邮件/短信/Webhook（HTML 邮件、指纹去重与 SMS 网关已代码化；仍需真实 SMTP/短信账号与 Alertmanager 令牌，[外部接入作业单](../ops/external-integration-onboarding.md)）~~ ✅ 2026-10-28（**Wave 5.73 重开并实机取证完成**：本机实跑 Prometheus 2.54 规则评估（`up==0` for 1m，ServiceDown 对真实下线服务 firing）→ Alertmanager 0.27（group_wait 10s、Bearer 令牌、send_resolved）→ whale-alert 9103 webhook → `alert_record` 落库 14 条 FIRING（Alertmanager 真实指纹、租户回落 1）→ WEBHOOK 通道 mock 接收器（127.0.0.1:9999）收到投递；同 fingerprint 重放去重生效（data=0）；标准 payload resolved → RECOVERED + recover_time（恢复不通知为既定设计）；**第二轮补充**：真实 Alertmanager `resolve_timeout` resolved 闭环（可控目标 + 真实停/起 whale-config 全闭环，recover_time 恰为 +60s）、`tenant_id=2` 标签归属落库、规则禁用不通知/启用恢复通知对照、up 服务无误报复验，alert_record 17→38、投递 16→33 行；EMAIL/SMS 真实账号仍按 mock，见 [取证报告](../reviews/2026-10-28-wave5.73-whale-config-and-v06-alert-evidence.md)）
- [x] ~~**V-07 Gitee Go 流水线实机触发**：JDK 21 / Node 24 插件兼容~~ ✅ 2026-10-28（**决策：不做（won't do）**；完全依赖 GitHub Actions，[CI/CD 方案](../design/cicd.md) 已记录冻结口径）
- [ ] **V-08 GHCR 推送实跑**（**外部条件阻塞，2026-10-28 核对更正口径**）：`docker-deploy.yml` 仅由 `push tag v*.*.*` 或 `workflow_dispatch` 触发，`push main` 不产出镜像；登录使用 `GITHUB_TOKEN` + `permissions: packages: write`，**无需额外 registry 凭据**（原「需 registry 凭据」不准确）。真实阻塞 = ①仓库无任何 `v*.*.*` 语义化 tag（唯一 tag 为 `legacy-github-main-20261023`）；②跑 Actions 的 `ci` 仓（`Mako-Jin/blue-whale-platform`）当日 push/fetch 均失败（连接重置、443 超时，HTTP/2 与 HTTP/1.1 同）；③`workflow_dispatch` 的 `image_tag` 输入声明后未被 `docker/metadata-action` 引用，手动触发只能得 `sha-xxxxxxx`。详见 [devlog §16](../devlog/2026-10-28.md)；剩余动作 = 网络恢复后推 `ci` 仓并打 tag 或手动 dispatch 取证
- [ ] **V-09 prod compose 覆盖层实跑**：需宿主机 Let's Encrypt 证书（**外部条件阻塞**；`docker compose -f docker-compose.yml -f docker-compose.prod.yml config` 已 exit 0，实际启动待证书挂载）
- [ ] **V-10 全服务镜像实机构建**：CI matrix 已覆盖，缺本机实跑（**环境条件阻塞**；本机构建需拉取 Maven 基础镜像并全量下载依赖，交付以 CI `docker-deploy.yml` matrix 为准）
- [x] ~~**V-11 合同供应商沙箱联调**：协议级 loopback 沙箱已通过（创建 → 查询 → 下载 → 业务错误码，服务端重算 HMAC）；真实 esignbao/fadada/ssq 沙箱仍阻塞于沙箱地址、appId/appSecret、回调公网地址与真实协议文档（[报告](../reviews/2026-10-27-p1-contract-esign-sandbox-and-compliance.md)、[凭据预检](../ops/external-integration-onboarding.md)）~~ ✅ 2026-10-28（**决策：不接真实沙箱，仅 mock**；保留 loopback 协议沙箱与契约测试，真实供应商联调待商务条件成熟后另立任务）
- [x] ~~**V-12 归档历史条目补扫**：需运维逐租户触发~~ ✅ 2026-10-27（新增自动补扫任务，`whale.archive.lock-reconcile.enabled=true` 后按租户游标分批执行；手工接口保留）
- [x] ~~**V-13 执行 `migration-5.66-pm-role-template.sql` 并跑 `bw-auth-business-role-grant-smoke.ps1`**：本机 MySQL 未启动，幂等与授权数量尚未实机验证~~ ✅ 2026-10-27（MySQL 8.0.26 连跑两遍 exit 0；default/acme 均 15 个业务角色、`pm_manager=32`/`pm_member=17`；acme `tenant_admin` PM 覆盖 34；smoke `RESULT: PASSED`）
- [x] ~~**V-14 熔断半开运行态取证**：网关接入故障服务，观察 `CLOSED→OPEN→HALF_OPEN→CLOSED/OPEN` 日志与指标（代码侧状态迁移日志已补，S-07）~~ ✅ 2026-10-28（新增 `bw-gateway-circuit-breaker-smoke.ps1`，真实 `whale-audit` 无实例故障：20 次 503 → `CLOSED→OPEN`，短路 503，11s 后 `OPEN→HALF_OPEN→OPEN`；详见 [检查报告](../reviews/2026-10-28-local-verification-batch.md)）
- [ ] **V-15 镜像矩阵覆盖缺口（新发现，2026-10-28）**：`docker-deploy.yml` 后端矩阵 20 项已逐一路径核对存在，但缺 `whale-apps/whale-contract`（端口 9006）；前端仅构建 `whale-web`（`whale-admin`），`whale-portal` 及 erp/oa/mes/pdm/pm/contract 子应用、form-designer(8002)、workflow-designer(8003)、ops 均无镜像。二者使「全服务镜像」口径不成立（V-10 的「CI matrix 已覆盖」需修正）。文档/`cicd.md` 需同步

## 四、文档同步

- [x] ~~**D-01 缺 `docs/devlog/2026-10-27.md`**：P1 #1–#14 只有检查报告与台账进度行~~ ✅ 2026-10-27
- [x] ~~**D-02 台账执行队列未含 P1 项**：队列表止于序号 27，P1 #1–#14 只在进度记录~~ ✅ 2026-10-27（队列尾注指向进度记录，明确不双维护）
- [x] ~~**D-03 P1 #1/#2 无检查报告**：合同签章护栏与开放平台接入只有台账行~~ ✅ 2026-10-27（已补 [检查报告](../reviews/2026-10-27-p1-contract-esign-startup-and-openapi.md)；当前 47 用例全通过、Checkstyle 0，并明确真实供应商沙箱仍未验证）
- [x] ~~**D-04 `todo-inventory-2026-10-23.md` 已过期**：多项已闭环，应归档或重生成~~ ✅ 2026-10-27（已移入 [archive/todo-inventory-2026-10-23.md](archive/todo-inventory-2026-10-23.md) 并标注冻结，所有历史引用同步更新；当前入口仍为本清单）
- [x] ~~**D-05 `service-catalog.md` C-06 滞后**：仍写 erp/mes/pdm/oa 未配数据源，实际 10-26 已完成~~ ✅ 2026-10-27（服务清单主表 4 行与 C-06 同步为已配数据源、已建库，各 2 表；核对 `application.yml`、compose 初始化 SQL 与各应用 `db/init.sql`）
- [x] ~~**D-06 合同设计稿 M3.6 遗留过时**：仍写「原件未做文件锁定」，P1 #6 已修复~~ ✅ 2026-10-27（M3.6 已补锁定实现、确认归档兜底锁、历史补扫接口与默认关闭调度；保留“待整理/无异步补偿队列”两项真实遗留）
