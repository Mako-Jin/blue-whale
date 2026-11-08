# 工作区扫描报告（2026-11-08）

> 扫描范围：`backend/`、`frontend/`、`docs/`（devlog / plans / reviews）、`deploy/`、git 工作区状态、代码 TODO/FIXME 标记。
> 口径：只登记**当前仓库仍存在**的缺口；已闭环项见 §六。
> 上一份：[2026-11-07 工作区扫描](2026-11-07-workspace-scan.md)。

## 一、扫描证据

| 项 | 结果 |
|---|---|
| 分支 | `main`，HEAD `f3ccf6e9`（chore: 收录工作区遗留改动与文档） |
| 工作区 | **干净**：0 个已修改/未跟踪文件（11-07 报告中的报销半成品、deploy 部署资产均已入库） |
| stash | 仍存 `pre-pull-local-edits-20261106`（**不可 pop**，含已回滚的抄送截断缺陷） |
| 代码标记 | `whale-games-core/src/engine.ts` 2 处 TODO（F-07 冻结项）；PM 模块 `TODO` 为业务状态码非待办；主代码无未实现 FIXME |
| 近两日提交 | CRM M4-4c 退换货（`e56e4994`）、实机修复 F1–F6（`5a080163`）、CRM M5/M6/M1-4/M1-9（`17f70def`）、扫描缺陷修复（`6a9a177c`）、OA 通知异步化（`16606045`）、审批对账定时任务（`78a58d82`）、网关改密拦截（`adc23cd3`）、遗留改动收录（`f3ccf6e9`） |

## 二、待办

### P0（下一批收口）

| 事项 | 依据 |
|---|---|
| 实机链路联调：CRM 4c 退换货 → ERP 退货过账；CRM 4b/发票对账；ERP ↔ MES 下达/完工链路；CRM M5 营销发送（依赖 whale-message）投递回执 | devlog 2026-11-08 §三.6、§6.3.3 |
| 指标快照与行动计划定时任务（`snapshot-cron` / `action-plan-cron`）在目标环境验证触发与幂等 | devlog 2026-11-08 §6.3.4 |
| 分析导出接 `whale-file`（现暂存 `crm_analytics_export` MEDIUMTEXT，大文件与生命周期需重做） | devlog 2026-11-08 §6.3.1 |
| 客户合并引用重写接入 `whale-contract` 合同台账（需其提供内网重写接口） | devlog 2026-11-08 §6.3.2 |
| OA V12 迁移若本地库有失败记录，需 `flyway repair` 或清理 `flyway_schema_history` 失败行 | devlog 2026-11-08 §三.5 |

### P1（按依赖排序）

| 事项 | 依据 |
|---|---|
| ERP M3 剩余：预测增强、计划订单转单、在途/安全库存占用；ERP M2 批次追溯/财务扩展（待 D5） | devlog 2026-11-08 §三.3 |
| 真实库执行累计迁移并起服冒烟（ERP V13–V16 及 migration、OA V5–V8+、Workflow V5/V6、LMS V1–V9、Party V1–V6、Mall V1–V6、Pay V1–V2） | 11-07 扫描 §四.2（未被后续取代） |
| 商城遗留：退货物流回写/WMS、换货、售后超时关闭、举证图片、退款分摊、券叠加、按件重计运费、报表口径拆分 | Wave 5.96–5.98「待做」 |
| LMS / 党政工会：真实库跑迁移、起服冒烟、试点复盘；课件/封面/附件接 `whale-file` | Wave 5.87/5.88 |
| MES 遗留增强 7 项 + D1–D16 业务口径现场复盘 | 五服务计划 §一 |
| CRM 前端遗留：跟进记录编辑/删除、公海释放免手填 ID、日期字段改 DatePicker（ownerId 筛选已修） | 1a/1b 评审遗留 |

### P2（储备）

| 事项 | 依据 |
|---|---|
| OA M3–M6 剩余、M1A-6 移动端适配 | roadmap 11.5–11.8 |
| PDM 6 项外部依赖（CAD 插件、3D/Office 转换、ngram/OCR、下载票据、DEPT ACL、真实联调） | PDM 计划 §十九 |
| 文档治理 DOC-01–DOC-10、`whale-scene` M3 3D（需 F-09 解冻） | roadmap 8.1/8.5 |
| 权限/菜单号段占用脚本化扫描（现靠人工核对） | 11-07 扫描 §五 观察 |

## 三、阻塞

1. **跨模块依赖阻塞（D 编号不变）**：D2 客户主数据映射、D5 ERP 批次/财务扩展、Workflow 条件表达式求值、auth data-scope、D14 分区/对象存储、PDM 六项外部依赖。
2. **真实库迁移未执行（累计清单见 P1）**——所有「迁移 + 起服冒烟」类验证依赖环境先行。
3. **跨服务契约阻塞**：加班费月汇总推 ERP（待 M2 薪资契约）、报销冲抵预支（待 M2）、PDM 库存预占口径（待 ERP 模型）、OA 报销推 ERP 与用章授权（待内网端点实机验证）。
4. **部署阻塞**：prod compose 覆盖层实跑缺 Let's Encrypt 证书（V-09 唯一未闭环项）。
5. **范围性不做（已决策）**：CA/TSA、真实实名与意愿、真实短信/邮件、合同供应商沙箱、Gitee Go 均按 mock / 不做口径。

## 四、BUG 与风险（现存）

| 级别 | 问题 | 说明 |
|---|---|---|
| 中 | **OA 附件无归属校验**：请假/报销仅登记 `fileId`，未校验上传者与租户，存在跨用户引用 | 建议 `whale-file` 补上传者/租户校验，OA 下载走代理 |
| 中 | **考勤模块测试覆盖过薄**：29 个端点仅 1 个领域测试类，迟到/早退/补卡月上限等核心规则无应用层用例 | 随下批补 6–8 个用例 |
| 低 | JaCoCo「零测试模块即跳过」盲区：`whale-common-core` / `whale-common-domain` / `whale-auth-sdk` | 补最小测试或加测试类存在性校验 |
| 低 | CRM 分析导出大文件走 MEDIUMTEXT 暂存，未接文件服务 | 已列 P0 待办 |
| 观察 | `/auth/refresh` HTTP 200 + 业务码 401 为既有约定（非缺陷，已澄清勿单独改动） | 11-06 扫描登记 |
| 红线 | stash `pre-pull-local-edits-20261106` **不可 pop**；提交前按文件甄别，禁止整目录 `git add`（并行会话可能在途） | 工作区约定 |

## 五、本轮核实已闭环（从旧清单移除）

- ~~whale-oa Checkstyle 存量 325 条~~：`6a9a177c` 清账 322 处 MissingJavadocMethod + 3 处 UnusedImports，`checkstyle:check` 0 违规（MagicNumber 236 处 info 级不阻断）。
- ~~CRM 客户列表负责人筛选静默失效~~：`6a9a177c` 补 `CustomerPageParams.ownerId` 并透传。
- ~~OA 审批通知同步发送~~：`16606045` 监听器异步化。
- ~~workflow 实例办结无回调~~：`78a58d82` 审批结果对账定时任务兜底 + `79607289` 明细行物理删除修复。
- ~~文档一致性门禁 FAIL / 报销半成品游离 / 考勤报销前端未建 / deploy 资产未跟踪~~：`fb1efd55`、`8b113470`、`037b39f7`、`f3ccf6e9` 收口。
- ~~本地起服阻断（权限迁移/保留字/事务传播/Jackson2/Redis 密码）~~：`5a080163` F1–F6 修复，1072 单测 0 失败。
- CRM M0–M6 全部落地（含 M5 营销、M6 分析、M1-4 合并、M1-9 导入导出），CRM 329 单测 + 前端 lint/build 通过。

## 六、建议执行顺序

1. 实机环境就绪后按 §二 P0 顺序联调（退换货→发票对账→ERP↔MES→营销发送→定时任务）。
2. ERP M3/M2 剩余与商城/LMS 遗留按依赖排批。
3. 附件归属校验、考勤测试补强随相邻批次搭车修。

## 七、补记（同日 13:30：本报告 P0/bug 项的处置结果）

| 原结论 | 现状 |
|---|---|
| §四 BUG：OA 附件无归属校验（中） | ✅ 已修复（`AttachmentOwnershipGuard` 四登记点接入 + 内网客户端补租户头），考勤补卡确认无附件能力不涉及 |
| §四 BUG：考勤测试覆盖过薄（中） | ✅ 已补 25 用例（3 个应用层测试类），合计 33 |
| §四 BUG：JaCoCo 零测试盲区（低） | ✅ 三模块补 junit 依赖与最小单测，不再被跳过 |
| §二 P0：分析导出接 whale-file | ✅ 已落地（V13 + `AnalyticsFilePort` + 代理下载端点 + 前端 blob 下载），实机联调待环境 |
| §二 P0：客户合并接 whale-contract | ✅ 已落地（contract 侧 count/rewrite 内网端点 + CRM 适配器，受 `whale.crm.contract.enabled` 门控），实机联调待环境 |
| 新发现 | 存量架构违规（`78a58d82`：审批对账应用层直依赖 infra 客户端）已修复，`CrmArchitectureTest` 9/9 转绿 |
| 新遗留：公文代理下载无调用者可见性 | ✅ 已修复（§八）：`DocReadAccessGuard` + 审批侧 `isParticipant`，草稿/驳回与秘密级公文限拟稿人/承办人/审批参与人 |

## 八、补记（同日 14:15：公文附件可见性 + 架构门禁回归）

- **公文附件下载可见性**：`DocAttachmentController` 原先只比对 `docType/docId`，持 `sys:oa:document:download` 的用户可枚举 `attachmentId` 取到未发布草稿或密级公文附件。新增 `document/application/service/DocReadAccessGuard`：发文 `DRAFT/REJECTED` 与密级=3（秘密）限拟稿人 / 承办人 / 审批参与人；`PENDING` 及之后对持权限用户开放（避免流程查询抖动卡住审批人）；收文登记后开放，秘密级限承办人。
- **审批参与人判定下沉**：`ApprovalInstanceAppService` 新增 `isParticipant(businessType, businessId, userId, username)`，口径 = 发起人 / 审批人快照 / Workflow 实例任务处理人（含 owner、delegateFrom），工作流不可用时 fail-closed。
- **回归修复**：上一批 `AttachmentOwnershipGuard`（application）直依赖 `OaFileMetadataClient`（infrastructure），触发 `OaArchitectureTest.application_must_not_depend_on_infrastructure`（7 处违规，全量测试才暴露）。新增端口 `common/domain/gateway/FileMetadataGateway` + 领域记录 `FileMetadata`，客户端实现端口，守卫改依赖端口 → 架构 8/8 转绿。

**仍未解决**：§三 其余阻塞（真实库累计迁移、跨服务契约、V-09 证书、D 编号依赖）；§二 P0 其余实机项（退换货→发票对账、ERP↔MES、营销发送投递回执、定时任务）。详见 devlog 2026-11-08 §九。

## 九、补记（同日 15:10：B4/B5/B7 实机联调通过）

| 链路 | 结论 |
|---|---|
| CRM 分析导出 → whale-file | ✅ 实机通过：导出经 `fileId/fileName/fileSize` 落库（V13 已在真实库生效），代理下载 235B CSV、`Content-Disposition` 正确 |
| 客户合并 → whale-contract | ✅ 实机通过：`merge/preview` 的 `references` 含「合同服务」，whale-contract 日志出现 `ct_contract` 按 `biz_type/biz_id` 的计数 SQL；重写端点幂等返回 0 |
| 公文附件可见性 | ✅ 实机通过：草稿他人 40305 / 拟稿人 200；提交后他人 200；秘密级他人 40305；跨用户引用附件 40307 |

环境要点：whale-file 需 `SPRING_DATA_REDIS_HOST/PORT`（yml 无 `REDIS_HOST` 占位）；容器内跨服务地址用容器名；验证实例必须 `SPRING_CLOUD_NACOS_DISCOVERY_REGISTER_ENABLED=false`。临时编排 `deploy/backend/docker-compose.verify.yml` 已留存，主栈 8 容器未改动。

## 十、补记（同日 15:50：CRM 4c/4b 实机联调通过 + 4 个存量缺陷修复）

| 链路 | 结果 |
|---|---|
| 4c 退换货 → ERP 退货过账 | ✅ `RET-20261108-0001` → `PENDING_APPROVAL` → 审批回调 → `COMPLETED`，`erpReturnNo=CRM-SR-RET-20261108-0001`、`POSTED`；库存 `+10 → -1 → +1 = 10`；应收 800 → 0/SETTLED；`reconcile` 与 ERP 建单均幂等 |
| 4b 开票全流程 | ✅ `INV-20261108-0001`：DRAFT → APPROVED（workflow 实例）→ ISSUED → SENT → 红冲生成 `INV-20261108-0002`（-800），原单可开票额度回补 800 |
| 4b 应收对账 | ✅ `POST /crm/ar/sync` `synced=1`；快照 `notDue=1500`；改到期日造逾期 68 天后 `overdue61To90=1500`（账龄分桶正确），已还原 |

**本轮修复缺陷（均为联调暴露的存量 bug）**

| 级别 | 问题 | 修复 |
|---|---|---|
| 高 | `AccountEntity.leaf` → 列 `leaf_flag` 映射错，**所有过账路径**（发货/入库）500 | `@TableField("leaf_flag")` |
| 高 | `ArSnapshotEntity` 四个逾期字段映射成 `overdue1_to30` 等，与库列 `overdue_1_30` 不符，应收快照全表不可用 | 四字段显式 `@TableField` |
| 中 | `whale.crm.return-approval` 配置段在 `application.yml` 缺失，退换货审批无法启用 | 补齐配置段与 `WHALE_CRM_RETURN_*` 占位符 |
| 中 | 订单 `last_push_error` / 退货 `last_error` 成功后不落 NULL（MyBatis-Plus `NOT_NULL` 策略），状态已成功却残留旧错误 | 两字段 `updateStrategy = FieldStrategy.ALWAYS` |

**新增扫描方法**：`.workbuddy/tmp/ScanOrmMismatch.java`（解析 `@TableName`/`@TableField` 与 `information_schema` 比对）。结果：ERP 78 实体 1 处、CRM 51 实体 4 处、OA 72 实体 0 处——上述高优两处即由它发现，建议后续纳入 CI 门禁（可作为静态检查脚本）。

**§二 P0 更新**：`CRM 4c 退换货 → ERP 退货过账`、`CRM 4b/发票对账` 两项**已闭环**；剩余实机项为 ERP↔MES 下达/完工、CRM M5 营销发送投递回执、`snapshot-cron`/`action-plan-cron` 定时触发验证。

**新增环境要点**：workflow 首次容器化（宿主 8083 被 Windows 保留端口段占用，映射 18083）；ERP 旧镜像不含 4c 端点导致 `NoResourceFoundException`→500，**联调前必须按当日源码重建涉改模块镜像**；退货前置是原订单已发货（需库存）；D2 客户/物料主数据未映射时订单下发首跳即失败；应收对账仅覆盖「有合同台账的客户」。

**新遗留**：红冲后原单不标记（`redFlushReason/redFlushedAt` 空、状态仍 SENT，额度靠 `sumRedFlushed` 回补）——功能安全、视图无痕迹，低优；`whale.crm.service/docs/survey` 三段配置同样未显式声明（有默认值），建议统一补占位符。详见 devlog 2026-11-08 §十。

## 十一、补记（同日 16:45：ERP↔MES 下达/完工实机联调通过）

**结论：P0「ERP↔MES 下达/完工」闭环。** 订单 `MO-20261109-0002` 实机跑通「建单 → 下达 → 领料 → MES 拆分/派工/开工/报工/完工 → outbox 回传 → ERP 完工入库」，ERP 落 `COMPLETED`、`completedQuantity=5`，对账台账 `completedMismatch=false`。

**本轮修复 3 处缺陷**（提交 `5f1ac01f`）：

1. MES 工单完工数量按工序累加（两道工序各 5 → 10）：改为取末道工序并以计划数封顶。
2. ERP 完工回传的幂等分支写 `DUPLICATE` 流水撞 `uk_mfg_mes_callback_event` → 500，MES 会无限重投：改为重复事件幂等成功返回，写流水冲突同样忽略。
3. 完工回传对 `RELEASED` 的拒绝原因不区分「没开工」与「状态不允许」：给出可执行提示（先在 ERP 领料）。

**环境补齐**：MES 首次容器化（`bw-mes`，宿主 9002，Flyway V1–V9 首个可用版本）；`whale.mes.outbox/integration` 配置段此前完全缺失，已补；compose 增加 `mes` 服务段。

**新识别风险（未处理，记录待评估）**：

1. 进度快照以「最后到达的事件」覆盖，乱序事件会被当作新事件接受（outbox 顺序投递下暂不触发）。
2. 生产订单 `routingStatus=FAILED`（PDM 缺工艺路线）时 MES 需人工 `split`，工艺路线同步链路仍未实机验证。
3. 历史单 `MO-20261109-0001` 保留 `completedMismatch=true` 作为差异识别证据，未做数据修补。

**P0 剩余**：CRM 营销发送投递回执（依赖 `whale-message` 容器，尚未纳入 compose）、`snapshot-cron` / `action-plan-cron` 定时触发验证。

## 十二、补记（同日 16:55：CRM 定时任务 cron 实机验证通过）

**结论：P0「snapshot-cron / action-plan-cron 定时触发验证」闭环。** 用分钟级 cron 起隔离实例（不注册 Nacos，宿主 9108），连续 4 轮触发：

- 快照 job：按昨日生成 12 条指标快照，重复触发只 upsert 不新增（`create_time` 不变、`update_time` 前移）。
- 行动计划 job：首轮自动补建 6 条规则并命中 `CONTRACT_EXPIRE` 生成 1 条 `OPEN` 行动项；重复触发不重复生成；已关闭项不再生成；条件解除后自动关闭（`条件已解除，系统自动关闭`）。
- 手工端点与 cron 口径一致：`POST /crm/analytics/snapshot` 返回 12（upsert）、`POST /crm/action-plan/generate` 同日返回 0；未来日期被拒（`41019`）。

**新识别风险（P1，未修）**：全仓库无 ShedLock/Redisson，`@Scheduled` 共 21 处，**多副本部署会 N 倍执行**。副作用靠业务幂等兜底（快照 upsert、行动项 dedupKey、ERP 建单按 `crm_order_no` 唯一），实测未产生重复数据，但会产生重复 RPC/DB 负载。建议给有副作用的 job 加分布式锁，或加「仅主实例注册调度」开关。

**P0 剩余**：CRM 营销发送投递回执。~~已确认仓库没有 whale-message 模块~~ —— **该判断有误**：`whale-message` 位于 `backend/whale-services/whale-message`（端口 8094，库 `bluewhale_message`，54 个类，含内网 `/message/internal/send`、模板、公告、站内信、四渠道 sender），此前只查了 `whale-apps` 与 compose 才漏判。真实缺口是：① 服务未容器化（compose 无 `message` 段）；② CRM 客户端报文与消息服务契约不一致；③ 平台侧没有异步「送达回执（DELIVERED）」状态，只有提交态 SENT/FAILED/SKIPPED/MOCKED。第 ①② 项已在同日晚间解决，见第十三节。

## 十三、补记（同日 17:15：CRM 营销触达 × whale-message 打通）

**结论：CRM→消息服务的投递链路打通，但「送达回执」仍是平台侧空缺。**

- 修正上一节的漏判：`whale-message` 在 `backend/whale-services/whale-message`（端口 8094、库 `bluewhale_message`、四渠道 sender）。
- 已修：① 服务容器化（compose `message` 段 + 首次 `SPRING_FLYWAY_BASELINE_VERSION=0` 补建 `msg_announcement`）；② **CRM 客户端报文与消息服务契约不一致**（CRM 发扁平单收件人报文，消息服务要 `channels[]/recipients[]/idempotencyKey`）已按平台契约改写；③ 投递结果判定过宽（`code=0` 即 `SENT`）已收紧，`MOCKED/FAILED/SKIPPED` 一律判失败。
- 实测 `MS-VERIFY-0001`：INBOX 明细 `SENT`（真实站内信）、EMAIL 明细 `FAILED / 消息通道处于 mock 模式，未真实投递`；重复执行不产生重复投递（`msg_delivery` 3 条不变）。CRM 334 单测、whale-message 37 单测均 0 失败，Checkstyle 均 0。

**新缺口（P1，需立项）**：平台消息服务只有提交态（`SENT/FAILED/SKIPPED/MOCKED`），**没有 `DELIVERED` 状态、没有渠道回执端点、没有回执表**，所以「投递回执」只能到「已提交渠道」。补齐需在 whale-message 增加回执端点 + 状态机 + 反向通知业务方。

**顺带发现（存量配置冲突）**：`whale.crm.marketing.message-url` 默认 `127.0.0.1:8085`，与 `bw-file` 端口冲突；已通过 `WHALE_CRM_MARKETING_MESSAGE_URL` 覆盖，建议把默认值改成 8094。

**P0 状态**：退换货/发票对账、ERP↔MES、定时任务、营销发送投递（到提交态）均已实机验证；剩余待立项的是「送达回执」能力与「P1 定时任务分布式锁」。

## 十四、补记（同日 18:00：送达回执能力落地，P0「营销发送投递回执」闭环）

**结论：第十三节列的「平台侧没有 DELIVERED、没有回执端点、没有回执表」已补齐并实机验证。**

| 侧 | 落地内容 |
|---|---|
| whale-message | `V2__delivery_receipt.sql`（`msg_record.callback_url`、`msg_delivery.delivered_at/read_at`、新表 `msg_receipt` 与 `msg_callback_task`）；`DeliveryStatus` 增 `DELIVERED/READ/BOUNCED` 且只允许前进；`POST /message/internal/receipt` 批量受理（幂等键 `receipt_key`）；回调 outbox 派发器（指数退避、5 次后 `GAVE_UP`） |
| whale-crm | `V14__marketing_delivery_receipt.sql`（明细 `msg_id/delivered_at/read_flag`、任务 `delivered/bounced/read_count`）；`SendItemStatus` 增三个回执态 + `isReached`；`POST /crm/internal/marketing/delivery-callback`；发送成功回填 `msg_id`，回执推进后按明细重算计数 |

**实机结果**（`bw-message` v2 / `bw-crm` v14）：发送 → `DELIVERED`（`advanced=1, callbackQueued=1`）→ 15s 内回调落地 CRM 明细 `DELIVERED`、`deliveredCount=1` → `READ`（`readCount=1, readFlag=1`）→ 重复回执 `duplicated=1` 全 0 副作用 → 乱序 `DELIVERED` `advanced=0` 不回退 → 未知 `providerMessageId` `unmatched=1` 仅落库。

**本轮修复的 2 个真实缺陷**：

| 级别 | 问题 | 修复 |
|---|---|---|
| 高 | 渠道编码两侧不一致（消息服务 `INAPP`/`IM` vs CRM `INBOX`），回调被 CRM 拒为 `41083 触达渠道非法`，回执静默丢失 | `SendChannel.fromMessageChannel` 归一 `INAPP/IM → INBOX` |
| 高 | 派发器只看 HTTP 状态，业务方「HTTP 200 + 非 0 业务码」被当成回调成功，回执静默丢弃 | 解析响应体 `code`，非 0 抛错走重试（实测 `reason=业务方返回失败: code=500`） |

**顺带修掉的存量隐患**：`existsByCampaignContactChannel` 原来只比 `status=SENT`，明细推进到 `DELIVERED/READ` 后同一活动同一联系人会被**二次触达** → 改为 `in (SENT, DELIVERED, READ)`；`whale.crm.marketing.message-url` 默认值 `8085`（与 `bw-file` 冲突）改为 `8094`。

**门禁**：whale-message 46 单测 / whale-crm 346 单测 0 失败，Checkstyle 0 违规，ArchUnit 全绿。

**P0 状态：§二 P0 全部实机闭环**（退换货→退货过账、发票对账、ERP↔MES、定时任务、营销发送投递回执）。

**剩余 P1**：① `@Scheduled` 无分布式锁（21 处，含新增回执派发器）；② 真实渠道回执接入（`mock=true` 下 EMAIL/SMS 无真实 `providerMessageId`）。

## 十五、补记（同日 19:10：定时任务迁入统一调度中心）

**原 P1「`@Scheduled` 无分布式锁」进入收敛通道**：实测全仓 **37 个** `@Scheduled` 方法（此前记的 21 处偏少），已完成调度中心容器化与执行器协议，并迁入首批 2 个任务。

| 侧 | 落地 |
|---|---|
| 调度中心 | `bw-job` 首次容器化（8082）；`job_executor` 注册表 + `job_info.executor_app/job_code`；注册/心跳/注销/查询内网接口 |
| 执行器 | `HttpJobHandler`：注册表 ∩ Nacos 寻址、按 `job_codes` 做能力校验、解业务码、租户头、强制读超时 |
| SDK | starter `@ScheduledJob` 双模式（`whale.schedule.local-enabled`），内网触发端点与注册器默认关闭，既有服务零感知 |
| 试点 | CRM `crm.analytics.snapshot` / `crm.analytics.action-plan` |

**实机**：注册成功 → 手动触发 `status=1` → cron `18:44:00` 自动成功 → 故意错配任务编码时 `status=-1/retry=1` 且错误信息可读 → 新镜像后「能力校验」直接拦截 → 假地址 90s 自动离线。

**关键判断**：37 个任务里 **10 个「进程内工作泵」不应迁移**（outbox 泵、索引 flush、缓存对账、播放数刷盘、60s 外部轮询），它们需要「每实例各跑」，远程调度只会放大延迟与故障面。**该迁 27 个，已迁 2 个，剩 25 个。**

**剩余 P1**：① 25 个任务分批迁移；② 执行日志回捞；③ 动态分片；④ 生产必须配置 `WHALE_INTERNAL_TOKEN`（dev 下空令牌 fail-open）。

清单见 `docs/plans/whale-job-scheduled-migration.md`。

## 十六、补记（同日 22:57：全量复扫，定时任务迁移已 27/27 收口）

> 本节的 §十五 之后又有 6 个提交（`3e3cdc20` … `89d4da47`），§十五 的「已迁 2/27」已被取代。以下为本轮实测状态。

### 16.1 扫描证据

| 项 | 结果 |
|---|---|
| 分支 / HEAD | `main` / `89d4da47`（feat(job): 执行日志回捞） |
| 工作区 | **干净**：0 个已修改/未跟踪文件 |
| 容器 | **21 个全部 healthy**（19 个服务 + redis + rabbitmq），无重启中的异常实例 |
| 定时任务 | 业务侧 `@ScheduledJob` **27 个**（已全迁）；残留 `@Scheduled` **10 个**，与方案 §2.1 的「进程内工作泵」清单逐一对应，属**有意不迁** |
| 代码标记 | 主代码**无**未实现 `TODO/FIXME`（`whale-pm`/OA 的 `TODO` 为业务状态码与常量名，非待办） |
| 文档门禁 | `check-doc-consistency.ps1` → **DOC-CONSISTENCY-PASS**（boot 4.0.8 / cloud 2025.1.2 / alibaba 2025.1.0.0） |
| 容器化覆盖 | 服务目录 32 个，compose 覆盖 **19 个**；`whale-file`/`whale-contract`/`whale-mall`/`whale-pm`/`whale-mail`/`whale-im`/`whale-map`/`whale-scene`/`whale-agent`/`whale-alert`/`whale-monitor` **未进常态编排** |

### 16.2 待办

**P0（下一批收口）**

| # | 事项 | 依据 |
|---|---|---|
| 1 | 修「调度派发到已死地址」：容器重建后 IP 重排，`bw-job` 在 22:49–22:50 对 8 个任务（CRM 5 / OA 3）派发到旧 IP（`172.18.0.9:9007`、`172.18.0.12:9004`）全部 `Connection refused`，任务各记一次失败。现象已自愈（心跳 90s 覆盖），但**重启窗口内的失败是真的**，需查 Nacos 旧实例摘除延迟或寻址加存活校验 | 本次 `docker logs bw-job` |
| 2 | 执行日志落容器 `${java.io.tmpdir}`，**容器重建即丢**：长期留存需给执行器挂卷 + `WHALE_SCHEDULE_EXECUTOR_LOG_DIR` | 方案 §19.5 |
| 3 | 生产必须配 `WHALE_INTERNAL_TOKEN`（dev 下空令牌 **fail-open**，内网触发端点裸奔） | §15 剩余项 ④ |
| 4 | `whale-file` 与 `whale-contract` 进常态编排：CRM 分析导出、客户合并、OA 网盘/公文、LMS 课件、PDM 附件、商城素材全部依赖 `whale-file`；目前只能靠 `docker-compose.verify.yml` 临时起 | 服务目录 vs compose |
| 5 | 计划文档状态漂移 2 处：`whale-job-scheduled-migration.md` 头部仍写「已迁 8 个」（实为 27/27）；`2026-11-06-five-services-backlog-stepwise.md` 仍写「CRM M5/M6 未开始」（已交付） | 本次比对 |

**P1**

| # | 事项 |
|---|---|
| 1 | 商城 `whale-mall` 从未在编排里起过：M0–5.98（60 端点 / 18 表）**零实机冒烟**；遗留 8 项（退货物流回写/WMS、换货、售后超时关闭、举证图片、退款分摊、券叠加/品类券、按件重计运费、报表口径拆分） |
| 2 | ERP M3 3b（工序报工、倒冲领料、退料/报废）+ M2 批次追溯/财务扩展（待 D5） |
| 3 | MES：数据范围过滤与归档物理清理（依赖 auth `data-scope`）；**工艺路线同步链路未实机验证**（`routingStatus=FAILED` 时靠人工 split） |
| 4 | CRM 前端 3 项：跟进记录编辑/删除、公海释放免手填 ID、日期字段改 DatePicker；后端：红冲后原单不标记（`redFlushReason/redFlushedAt` 空）、`service/docs/survey` 三段配置未显式声明 |
| 5 | `whale-pm`（9005，M1–M5 已交付）与 `whale-mail`（8099）/`whale-im`/`whale-map`/`whale-scene` 均「可启动、未起服」，无容器基线 |
| 6 | LMS / 党政工会：真实库跑迁移 + 起服冒烟 + 试点支部复盘；课件/封面/附件接 `whale-file` 上传控件 |
| 7 | OA M6 剩余（安全/性能加固、试点部门验证）、M1A-6 移动端适配 |

**P2**

PDM 6 项外部依赖（CAD 插件、3D/Office 转换、ngram/OCR、下载票据、DEPT ACL、真实联调）；OA M3–M6 打磨；文档治理 DOC-01–DOC-10；`whale-scene` M3 3D（F-09 冻结）；权限/菜单号段占用脚本化扫描；`whale-games-core` 2 处 TODO（F-07 冻结）。

### 16.3 阻塞

| # | 阻塞 | 影响 |
|---|---|---|
| 1 | **Docker VM 内存 5.79G，已用约 5.1G** | 无法起第二副本做「多实例动态分片 / 广播 / 互斥」实机验证，只能靠单测；任何新服务容器化都要先腾内存 |
| 2 | 跨模块依赖：D2 客户主数据映射、D5 ERP 批次/财务扩展、Workflow 条件表达式求值、auth `data-scope`、D14 分区/对象存储、PDM 六项外部依赖 | 对应功能只能做到「骨架 + 关闭态」 |
| 3 | 跨服务契约：加班费月汇总推 ERP（待 M2 薪资契约）、报销冲抵预支（待 M2）、PDM 库存预占口径（待 ERP 模型）、OA 报销推 ERP 与用章授权（待内网端点实机验证） | 推单链路不通 |
| 4 | 真实渠道回执：`mock=true` 下 EMAIL/SMS 无真实 `providerMessageId`，回执只能验到站内信 | 「送达率」类验证不可信 |
| 5 | prod compose 覆盖层缺 Let's Encrypt 证书（V-09 唯一未闭环项） | 生产部署无法实跑 |
| 6 | 范围性不做（已决策）：CA/TSA、真实实名与意愿、真实短信/邮件、合同供应商沙箱、Gitee Go | 不做，不算欠账 |

### 16.4 BUG 与风险

| 级别 | 问题 | 说明 |
|---|---|---|
| 中 | **调度派发命中旧 IP**（详见 §16.2 P0-1） | 重启窗口内任务失败一次，非静默丢数据；建议排期查服务发现摘除延迟 |
| 中 | 执行器心跳噪音：`job` 重启瞬间 11 个服务各报一条 `StringHttpMessageConverter` 拒绝转换（`application/octet-stream`） | 25s 内自愈；根治要改 starter 心跳为 `exchange` 直读字节流 + 19 服务全量重打包，收益不抵风险，**记录不修** |
| 中 | MES 进度快照按「最后到达的事件」覆盖，乱序事件会被当新事件接受 | outbox 顺序投递下暂不触发 |
| 低 | 红冲不标记原单（额度靠 `sumRedFlushed` 回补） | 功能安全、视图无痕迹 |
| 低 | `MO-20261109-0001` 保留 `completedMismatch=true` 作为差异证据，未修补数据 | 有意保留 |
| 观察 | `/auth/refresh` HTTP 200 + 业务码 401、`Result` 统一包装 | 既有约定，勿单独改动 |
| 红线 | stash `pre-pull-local-edits-20261106` **不可 pop**；提交按文件暂存，禁止整目录 `git add` | 工作区约定 |

### 16.5 与 §二 相比已闭环

§二 P0 全部闭环（P0 1–4 于 §十–§十四实机通过；§二 P0「定时任务验证」升级为整体迁移并 **27/27 收口**）；§四 三个 BUG（OA 附件归属、考勤测试覆盖、JaCoCo 零测试盲区）已修；§二 P1 的 LMS/党政工会「起服冒烟」已由容器化覆盖（`bw-lms`/`bw-party` healthy）。

## 十七、补记（同日 23:30：§十六 P0 修复结果）

### 17.1 P0-1 调度派发命中旧地址 —— 已修并实机验证

**根因**：容器重建后 IP 重排，注册表（靠 90s 心跳判活）与 Nacos（实例摘除有延迟）**两侧同时残留旧地址**，`dispatchableAddresses` 的交集过滤拦不住「刚死 30 秒」的实例；而寻址只选一个地址、连接失败即整个任务失败。

**修法（派发级故障转移，不依赖寻址精度）**：

| 改动 | 内容 |
|---|---|
| `HttpJobHandler#resolveTargets` | 返回**候选列表**（首选在前，其余环形补齐），不再返回单个 URL；直连模式候选数为 1 |
| `HttpJobHandler#dispatch` | 逐个候选尝试，**仅连接层失败**才换下一个 |
| `HttpJobHandler#isConnectFailure` | 沿 cause 链判定 `ConnectException/NoRouteToHostException/UnknownHostException`；**读超时不改派**——请求可能已在对端执行完，改派等于重复副作用 |
| `JobExecutorService#markUnreachable` | 连不上的地址立即置 `status=0` 摘除；心跳 30s 后自动回在线，属临时回避而非判死刑 |
| `JobExecutionContext#resolvedAddress` | 改为「每次尝试后回填」，失败时也记下实际打到的地址，日志回捞仍能定位 |

**新增单测** `HttpJobHandlerTest` 5 例（97 项全绿，原 92）：改派成功并摘除死地址 / 业务失败不改派 / 全部不可达抛最后原因 / 拒连与超时的判定差异 / 空候选抛不可派发。Checkstyle 0。

**实机验证（真事，不是构造）**：23:14–23:18 重建 11 个执行器容器，IP 全部重排。23:16:00 CRM 两个任务（`crm.order.push`、`crm.approval.reconcile`）被打到旧地址 `172.18.0.12:9007`：

- 日志出现 `执行器不可达，改派下一候选（remain=0）` + `执行器连接失败，临时摘除`；
- 单副本场景没有备选，任务失败一次，由调度中心重试在 23:16:45 新实例注册后**补跑成功**（流水 `status=1, retry=1, pushed=0`）；
- 23:18 起全部任务 `status=1`，`job_info` 29 条全部启用。

即：**多副本时可无缝改派；单副本时退化为「失败一次 + 重试成功」，不会再出现连续多轮撞死地址**。

### 17.2 P0-2 执行日志容器重建即丢 —— 已修

`deploy/backend/docker-compose.services.yml` 给 11 个执行器服务（auth / oa / crm / doc / video / music / archive / audit / lms / party / pdm）统一注入 `WHALE_SCHEDULE_EXECUTOR_LOG_DIR=/var/log/whale-job` 并挂**按服务独立的命名卷** `bw-<svc>-joblogs`（不共用卷是刻意的：`JobExecutionLogStore` 按「任务编码保留最近 20 份」轮转，共卷会让两个实例互相删对方的日志）。

实测 `bw-party`：触发 `party.notice.scan` → 卷内落 `20261108/party.notice.scan-20261108231452234-240376d1.log`；`--force-recreate` 重建容器后文件仍在。11 个服务已全部按新编排重建并就位。

### 17.3 P0-5 计划文档漂移 —— 已修

- `whale-job-scheduled-migration.md` 头部状态：实施中（8/27）→ **已收口 27/27**；
- `2026-11-06-five-services-backlog-stepwise.md`：CRM 行与结论段更新为「M5 营销 / M6 分析 / M1-4 合并 / M1-9 导入导出已交付」。

### 17.4 仍未修（有明确原因，非遗漏）

| 项 | 状态 | 原因 |
|---|---|---|
| P0-3 生产 `WHALE_INTERNAL_TOKEN` | 不改代码 | `InternalTokenProperties` 已实现「非 dev 无令牌拒绝启动」fail-fast，属**部署前检查清单**而非代码缺口；在 dev compose 里统一注入需同时重建全部 21 个容器，收益只覆盖本地环境，留给上线时与真实密钥一起配 |
| P0-4 `whale-file` / `whale-contract` 进编排 | 阻塞 | Docker VM 5.79G、当前已用约 5.1G，再加两服务（各 ~300MiB）会把余量压到几百兆，重启窗口有 OOM 风险；需先扩 VM 内存或停用部分非常用服务 |
| 心跳 `String` 转换噪音 | 记录不修 | 要改 starter 心跳为 `exchange` 直读字节流 + 19 服务重打包，收益不抵风险（§18.4） |

## 十八、补记（同日 23:20–次日 00:15：商城 M0–5.98 首次实机运行）

商城此前是「代码齐、从未跑过」的大块：12 个控制器 / 60 个端点 / V1–V6 迁移，建过库但没有一次进程内验证。
本轮把它真正跑起来，暴露出 4 个静态检查与单测都抓不到的缺陷。

### 18.1 启动即失败：Jackson 2 没有 Bean

```
Parameter 1 of constructor in PayInternalClient required a bean of type
'com.fasterxml.jackson.databind.ObjectMapper' that could not be found
```

Spring Boot 4 用 Jackson 3（`tools.jackson.*`），不再自动注册 Jackson 2 的 `ObjectMapper`。
商城是**新代码**，按红线直接迁到 Jackson 3，而不是像历史服务那样补 `Jackson2CompatConfig`：

- `SpuRepositoryImpl`：`ObjectMapper` / `TypeReference` → `tools.jackson.*`；
- `PayInternalClient`：`ObjectMapper` / `JsonNode` → `tools.jackson.*`；Jackson 3 的异常是非受检的，
  原来的 `catch (RuntimeException | JsonProcessingException)` 编译不过，收敛为 `catch (RuntimeException)`。

### 18.2 全量覆盖保存撞唯一键（500）

商品编辑按契约是「提交的 SKU 列表即最终列表」。仓储实现是**先插新行、再删旧行**，
于是调用方只回传编码不回传 ID（复制商品、脚本直调、或前端漏带 id）时，
旧行还在就被 `uk_mall_sku_code` 挡住：`Duplicate entry '1-SMOKE-SKU-A-…'`。

修法：`saveSkus` 插入前先按 `(spu_id, sku_code)` 查本商品已有行并复用其 ID，
语义上真正变成「按编码 upsert」，SKU 主键也保持稳定（历史订单明细引用不漂移）。

### 18.3 售后驳回 / 撤销 / 退款回调必然 500（最严重）

`mall_order_log.to_status` 是 `NOT NULL` 列，但这三处写轨迹时传了 `null`：

| 位置 | 后果 |
|---|---|
| `reject` | 驳回售后整笔事务回滚 → **驳回功能完全不可用** |
| `cancel` | 撤销售后整笔事务回滚 → **撤销功能完全不可用** |
| `markRefunded`（含无售后单兼容分支） | 退款回调回滚 → 支付方按失败重投，**永远退不成功** |

单测全绿却漏了它：Mockito 不校验建库约束。
修法：退款回调在 `applyOrderRefund` 后**回读订单真实状态**写入 `to_status`（部分退款时状态不变，`from == to` 才是事实）；
驳回/撤销不改订单状态，起止状态都取订单当前状态。同时给这两个用例补了「轨迹必须带终态」断言。

### 18.4 客户端错误被兜成 500

`GlobalExceptionHandler` 只处理了 `BizException` / 参数校验 / 404 / 兜底，其余全部落到 500「系统异常」：

- 请求体解析失败（JSON 语法错、日期格式不符）→ 500
- 路径变量类型不符（`/mall/product/abc`）→ 500
- 缺必填参数 → 500
- 方法不支持（GET 打 POST 端点）→ 500

四类都是调用方的错，排障时却被误读成服务端崩了。已在 `whale-common` 统一补齐：
前三类归 `PARAM_ERROR(400)`，方法不支持新增 `METHOD_NOT_ALLOWED(405)`。
这是公共组件，**只有重新打包过的服务才会生效**，其余服务下次构建时自动获得。

### 18.5 冒烟结果：77/77

脚本 `scripts/mall-smoke.py`（本轮入库，可重复执行：数据带运行序号，不依赖上轮残留）：

| 段 | 覆盖 |
|---|---|
| A 元数据/类目 | meta、类目树、增改删 |
| B 商品/库存 | 建档（双 SKU 双价目）、列表、详情、**编辑（不带 id）**、上下架、库存调整 |
| C 运费/渠道 | 模板增查改启停、渠道建档改启停 |
| D 优惠券 | 建档、改、启停、可用券查询、领取、我的券 |
| E 购物车 | 加购、查看、改数量、选中、（另建）删除 |
| F 订单主链路 | 预览、下单、**幂等重放**、我的/运营列表、详情、发起支付、支付回调 + 幂等、发货、**再开单并取消**、签收（内网回调）、完成 |
| G 售后/退款 | 申请、我的/运营列表、详情、通过（真发退款给 whale-pay）、驳回、撤销、整单退款、退款回调 |
| H 清理 | 下架后删除商品、删除类目 |
| I 负向契约 | 畸形 JSON / 非法路径变量 / 缺参 均返回 400 |

结论：**73 项业务断言 + 4 项负向断言全通过**，商城主链路（下单→支付→发货→签收→完成→售后→退款）首次跑通。

### 18.6 事故：VM 内存打满时 `docker build` 把引擎拖死

- 现象：`docker build` 卡住 19 分钟零 CPU，`docker version` / `docker ps` 全部返回
  `500 Internal Server Error`；宿主日志 `apiproxy` 反复 `dialing 192.168.65.7:2376: no route to host`。
- 起因：当时 VM（上限 6GB）只剩 **374MB** 可用，同时 22 个容器在跑，buildkit 一起就撑爆。
- 恢复：`docker-desktop desktop restart` → 5 秒内引擎恢复；19 个 compose 容器凭
  `restart: unless-stopped` **自动全部回来**，`docker-compose.verify.yml` 的 `restart: "no"` 容器需手工拉起。
- 规矩：**`docker build` 前先看 `MemAvailable`，低于 1G 先停掉非必需容器**（本轮验证栈就是这样腾出来的）。

### 18.7 现场与门禁

- 商城 205 项单测全绿（含新增「售后轨迹必须带终态」断言）；`whale-common-core` / `whale-spring-boot-starter` /
  `whale-mall` 三个模块 Checkstyle **0 违规**。
- 收尾已回滚临时改动：`docker-compose.verify.yml` 栈 `down`，`bw-music` / `bw-party` 重新拉起，
  常态编排 **21 个容器**全在线。

### 18.8 仍未做

| 项 | 状态 | 原因 |
|---|---|---|
| 商城进常态编排 | 阻塞 | 与 §17.4 P0-4 同一约束：VM 6GB、常态 21 容器已占约 5G；再常驻 mall/file/contract（约 900MB）会立刻回到 OOM 边缘 |
| 商城前端页面 | 未开始 | 端到端已通，但 `frontend/platform-apps` 下没有商城应用，运营侧仍只能靠接口 |
| 本地复跑冒烟 | 手工步骤 | 需先停 2 个低频服务腾内存 → 起 verify 栈 → `python scripts/mall-smoke.py` → 收尾；建议后续写进 `scripts/` 一键脚本 |

## 十九、补记（2026-11-09 00:20–01:00：收口 §十七/§十八 的剩余 P0 与两个数据一致性缺陷）

### 19.1 前置：Docker VM 6GB → 8GB

发现引擎**整个没在跑**（`dockerDesktopLinuxEngine` 管道不存在，21 个容器全停），宿主反而空出 8.3G。
这是扩容的最好窗口——不会打断任何容器。

- `~/.wslconfig`：`memory=6GB` → `memory=8GB`（备份 `.workbuddy/tmp/wslconfig.bak`），`processors=6`、`swap=2GB` 不变；
- `docker-desktop desktop start` → 5 秒引擎就绪，**21 个容器凭 `restart: unless-stopped` 自动回来了**，145 秒后全 healthy；
- VM 上限实测 `8330022912` 字节（7.76GiB），宿主 `vmmem` 7.82G、宿主剩余仅 0.34G。
  ⇒ **不能再往上加**：宿主机总内存 15.92G，加到 10G 会让 Windows 进交换，用户会在睡梦中被拖慢。

### 19.2 三个服务进常态编排，两个演示模块让位（§17.4 P0-4 收口）

内存算账：8G 装不下 24 个 Spring 服务（每个 RSS 约 330–390MB）。取舍标准是**「谁被别的服务依赖」**：

| 动作 | 服务 | 依据 |
|---|---|---|
| 进默认编排 | `whale-file`(8085)、`whale-contract`(9006)、`whale-mall`(9010) | file 被 4 处配置引用（`WHALE_JOB_FILE_SERVICE_URL` / `WHALE_ARCHIVE_FILE_URL` / `WHALE_AUDIT_EXPORT_FILE_SERVICE_URL` / CRM 营销），此前**全部指向不存在的容器**——CRM 分析导出、客户合并、OA 网盘与公文附件、LMS 课件、PDM 附件、archive 归档、audit 导出、job 附件清理实际都不可用 |
| 改 `profiles: ["demo"]` | `whale-music`(8096)、`whale-party`(9009) | 演示模块，无下游依赖，共约 640MB |

结果：默认 **22 个容器**全 healthy，VM 余量 **891MB**（重建后实测）。要起演示模块：
`docker compose --profile demo -f docker-compose.services.yml up -d music party`。

同批修正：
- `docker-compose.verify.yml` 里原本也定义了 file/contract/mall，与常态容器**撞 `container_name`**，已删掉这三个块，只保留 CRM/OA 第二副本（9107/9104），这两个副本复用网络上的 `bw-file`/`bw-contract`；
- 调度侧同步：`party.notice.scan`、`music.play-history.cleanup` 的 `job_info.status` 置 0（按既有约定「dev 未常驻的服务任务停用」），否则每次 cron 都会记一条「执行器无在线实例」失败。

### 19.3 验证：两个可重复执行的回归基线

- `scripts/mall-smoke.py` 在**常态栈**上重跑 → **77/77**（此前只在临时 verify 栈上跑过）；
- 新增 `scripts/file-smoke.py` → **9/9**：上传（含大小校验）/下载（含 `Content-Disposition`）/preview/预签名/配额/内网上传/不存在文件 404。
  两个坑：① `fileSize` 等长整型按**字符串**序列化，比较前要 `int()`；② `presigned` 在本地存储插件下必然返回 `400 当前存储插件不支持预签名下载: local`，这是配置限制不是缺陷，脚本按「预期跳过」记通过。
- 跨容器可达性实测：`bw-crm` / `bw-oa` / `bw-archive` / `bw-audit` / `bw-job` 均能访问 `http://bw-file:8085/actuator/health`。

### 19.4 修 MES 进度快照被乱序事件覆盖（§十六 BUG 表中级）

`MesIntegrationAppServiceImpl#handleProgress` 对快照是**无条件覆盖**，而进度事件是「累计值快照」且报文里没有事件时间戳：
先收到 T2 再收到迟到的 T1，快照就被写回退（报工 8 → 5），对账页面与「完工数是否一致」的判定全部跟着错。

修法（`MesStatus` 新增 `STALE`）：
- 累计量天然单调，按 **完工 → 报工 → 合格** 的优先级逐级比较，任何一级比已存值小即判定落后，**丢弃并写 STALE 流水**（不是 DUPLICATE——事件键确实不同，只是到达顺序错）；
- 数量完全相等时再看状态：MES 终态（`COMPLETED` / `CLOSED`）不允许被非终态事件降级；
- 正常的进度前进照旧覆盖（已有用例覆盖）。

单测 +3（乱序丢弃 / 终态不降级 / 前进仍覆盖），`MesIntegrationAppServiceImplTest` 11 项全绿。

### 19.5 修 CRM 红冲后原单不标记（§十六 P1）

`redFlush` 只插入一张负数红冲单并 `markRedFlushed` 自己，**原单一个字都没改**：
列表和详情里它仍显示「已开票」，看不出已作废；对账口径也没有「已全额红冲」这个事实。

修法：
- 新增 `InvoiceRequest#markOriginFullyRedFlushed(reason, at)`：置 `RED_FLUSHED` + 红冲原因/时间，**但不动 `issuedAt`**（原单的实际开票时间必须留作审计口径，`markRedFlushed` 会把它覆盖成红冲时间，所以不能复用）；
- `redFlush` 在插入后判断「历史已冲 + 本次 ≥ 开票额」→ 标记原单并 `update`；**部分红冲不改原单状态**（剩余余额仍可继续冲，`sumOccupiedAmount` 的净额算法已经把负数红冲单算进去了，不需要额外调额度）。

单测 +2（全额冲完标记且保留 issuedAt / 部分冲减不改状态），`InvoiceAppServiceImplTest` 11 项全绿。

### 19.6 门禁与现场

- `whale-crm` 全量单测 **362 项**、`whale-erp` 全量通过；`whale-crm`/`whale-erp`/`whale-job` Checkstyle **0 违规**；
- CRM/ERP 已重新打包、重建镜像并重启（`target/` 里的旧 jar 不作验收依据这条红线）；
- 现场 **22 个容器全 healthy**，2 个演示模块容器 stopped（profile 外），`job_info` 29 条中 2 条停用。

### 19.7 这批没做，以及为什么

| 项 | 原因 |
|---|---|
| 生产 `WHALE_INTERNAL_TOKEN`（§17.4 P0-3） | 出站拦截器 `InternalTokenSupport` 需要业务侧全部改用它的 `restClient()`，仓里仍有直接 `RestClient.create()` 的内网调用；夜里一次性开启会让 CRM→pay、mall→pay、MES→ERP、调度→执行器**同时**开始带令牌，任何一处漏改就是断链。保留现有「非 dev 无令牌拒绝启动」的 fail-fast，按上线清单处理 |
| 心跳噪音（11 服务各一条 `StringHttpMessageConverter`） | 根治要改 starter 的心跳读取方式并**重打包全部服务**，收益是一个自愈 25 秒的 WARN |
| 红冲 / MES 乱序的**实机**验证 | dev 库没有 `ISSUED` 状态的发票（`/crm/invoice/page?status=ISSUED` 返回 total=0），造一张要先走审批+开票；MES 链路要真工单。两者都由单测覆盖，实机留到有数据时 |
| 商城前端、ERP M3 3b、MES 数据范围/归档清理、CRM 前端 3 项 | 都是功能开发，不是「修」；需要产品口径确认，不宜半夜自己拍 |

### 19.8 补修：连接阶段超时未判为可改派（§十七 P0-1 的残留）

重建容器窗口里 `bw-job` 日志暴露了上一轮故障转移的一个漏判：

```
执行器响应超时，请求可能已在对端执行，不做改派:
  http://172.18.0.20:8093/internal/job/archive.sync/run ... Connect timed out
```

`Connect timed out` 是**连接阶段**超时（对端端口还没监听），一个字节都没发出去，
改派完全安全；但 `isConnectFailure` 只认 `ConnectException` / `NoRouteToHost` / `UnknownHost`，
把它当成了「读超时」，于是既不改派也不摘除死地址。

补法：`HttpConnectTimeoutException`（JDK HttpClient）+ `SocketTimeoutException` 且消息含 `connect`
（`SimpleClientHttpRequestFactory` 的 `Connect timed out`）都按连接层失败处理；
读超时（`Read timed out`）保持不改派。单测 +1（`HttpJobHandlerTest` 6 项全绿，whale-job 98 项全绿）。
`bw-job` 已重打包重建，重建后 3 分钟内调度零异常、流水全部 `status=1`。

## 二十、补记（2026-11-09 01:00–02:00：权限号段撞号，两处静默失效）

上一轮把「权限/菜单号段占用脚本化扫描」列在 P2，做的时候先写扫描器，扫完发现**不是待办，是两个正在生效的缺陷**。

### 20.1 为什么会静默失效

权限种子全部写成这种形态（176 份迁移脚本、800+ 权限点）：

```sql
INSERT INTO sys_permission (...) SELECT 1900, 0, 'sys:crm:analytics:funnel', ...
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1900 OR perm_code = '...');
```

幂等条件同时判 `id` 和 `perm_code`：**只要 ID 被别的模块占了，这一行就整条跳过，脚本照样返回成功**。
于是撞号的后果不是报错，而是「权限点根本不存在」——接口一路 403，建表与单测全绿，日志里一个字都没有。

### 20.2 扫出两个真缺陷（都在库里可复现）

| # | 撞号 | 实际后果（已对 `bluewhale_auth` 核实） |
|---|---|---|
| 1 | `migration-5.83-crm-m6-analytics.sql` 的 1899–1905 vs `migration-5.87-lms-m0.sql` 的 1900–1949 | 库里 1900–1910 全是 `sys:lms:*`，**CRM 的 7 个分析权限一个都没进去**（`sys:crm:analytics%` 查询 0 行），crm_analyst 打开销售看板/漏斗/排行全部 403；菜单 441–448 也从未登记（CRM 前端菜单树里没有分析入口） |
| 2 | `migration-5.55-pm.sql` 的 172/173 vs `V3__tenant_password_policy.sql` | 库里 172/173 是 `sys:tenant:pwdpolicy:list/edit`，**`sys:pm:project:list` / `:add` 从未入库**；而 5.57/5.66 的授权按「172–202 区间」发放，于是 **pm_manager / pm_member 拿到了「租户密码策略查看」，pm_manager 还拿到了「编辑」**——项目经理能改全租户密码策略，同时看不到项目列表 |

第 2 条是**跨模块越权**：不是权限缺失，是别人家的权限被发给了不该拿的角色。

### 20.3 修法

| 改动 | 内容 |
|---|---|
| `migration-5.83-crm-m6-analytics.sql` | 权限 1899–1905 → **2300–2306**（2200–2224 归商城，2300 起空闲）；菜单 441–448 保持；**授权语句从「按 ID 区间」改为「按 perm_code」**——同名 `sys:crm:export` 在存量库里是 352（Wave 5.77），按 ID 授权必然漏 |
| `migration-5.55-pm.sql` | 两项改 **2320/2321（PM 续段）**，其余 174–202 不动；头部写明来龙去脉 |
| `migration-5.57-pm-role-grant.sql` / `migration-5.66-pm-role-template.sql` | 授权区间 172–202 → 174–202 + 2320–2321（含 tenant_admin / pm_member 三处） |
| `migration-2026-11-09-pm-perm-regrant.sql`（新） | 修既有库：**先删误授**（pm_* ↔ 172/173），再补 2320/2321，最后按 perm_code 重新授权 |
| `scripts/security/scan-id-ranges.py`（新） | 静态扫 176 份迁移脚本：同 ID 被不同编码争用 = **ERROR**；同编码多 ID = WARN（提示授权要按编码）；同 ID 同编码多文件 = INFO（幂等重复声明的正常形态） |
| `.github/workflows/code-quality.yml` | 新增 `permission-id-ranges` job，ERROR 即阻断（`--strict` 可把 WARN 也升级为失败） |

### 20.4 验证

- 扫描器：修前 `ERROR 18` / 修后 **`ERROR 0`**（827 权限点、17 权限菜单脚本、56 角色声明、122 份脚本）；
- 库里复核（与 auth 的鉴权查询同口径 `role → sys_role_permission → sys_permission`）：
  - `crm_analyst` 现有 `analytics:{dashboard,funnel,customer,ranking,collection}` 5 项（导出与行动计划另由 `sys:crm:export` / `sys:crm:action:plan` 承载）；
  - `pm_manager` 现有 `project:{list,add,edit,delete}`，`pm_member` 有 `project:list`；
  - `pm_manager`/`pm_member` 对 `sys:tenant:pwdpolicy%` 的授权 **0 行**（误授已清）。
- 中文落库用 `HEX()` 核过：菜单 441 = `E99480E594AEE79C8BE69DBF`（销售看板）、448 = `E695B0E68DAEE5AFBCE587BA`（数据导出），不是 `?`。

### 20.5 同批修复：CRM 三段配置未显式声明

`whale.crm.service`（工单/SLA 时限）、`whale.crm.docs`（知识库正文引用）、`whale.crm.survey`（满意度问卷）三个 `@ConfigurationProperties`
在 `application.yml` 里一个字都没写，全靠 Java 默认值——**运维看不到、也改不了**。已在 `application.yml` 显式声明并给全部键留环境变量口
（`WHALE_CRM_SERVICE_P1_RESPONSE_HOURS` / `WHALE_DOC_URL` / `WHALE_CRM_SURVEY_SECRET` 等）。
时间键必须加引号：YAML 1.1 把裸 `09:00` 当六十进制数解析成 `540`。

### 20.6 仍未处理（有意）

- `migration-5.77-crm.sql` 的 337–354 与 5.83/5.84 的 1810–1849 是**同编码两套 ID**（存量保留、后者 no-op）。已在扫描器里标 WARN 而非 ERROR；
  要彻底消掉得把存量行改号并同步 `sys_role_permission`，属数据迁移，需单独窗口，不在本次「修缺陷」范围。
- 心跳噪音、`WHALE_INTERNAL_TOKEN`、红冲/MES 实机验证：见 §19.7，理由未变。

### 20.7 顺着同一条线又查出两类「代码有、库里没有」

号段写完不过瘾，又做了一次**权限码覆盖检查**：把 `backend/**/src/main` 里所有
`@RequirePermission("sys:...")` 字面量抓出来，和 `sys_permission` 台账比。878 个编码里 29 个查无此项。

| 类型 | 明细 | 判读 |
|---|---|---|
| 种子从未执行（28 项） | CRM M1 导入导出、M3 发票 6 项、M4 工单/知识库/SLA/问卷、M5 营销/培育/伙伴 —— `migration-5.83-crm-m{1-merge-import,3-invoice,4-service,5-marketing}.sql` 等 9 份脚本**从未在这个库上跑过** | 不是撞号，是部署漏跑。已按顺序补跑 9 份脚本（全部 `executed` 无 ERR），CRM 发票/工单/营销端点对 `crm_*` 角色从 403 恢复 |
| 权限码拼错（1 项） | `ProjectController#addTransition` 写的是 `sys:pm:transition:edit`，种子里只有 `transition:add/delete/list` | 改成 `sys:pm:transition:add`（该端点是「新增状态流转」）。拼错的后果同样是「除超管外全 403」，而编译、启动、单测都正常 |

补跑后覆盖检查：**代码 878 个编码 / 库 893 个 → 缺失 0**。

同时新增 `scripts/security/check-perm-coverage.py`（吃库里 `SELECT perm_code` 的导出文件做比对），
与号段扫描分工：一个查「ID 是否被抢」，一个查「编码是否真的存在」。

**这类缺陷的共同点**：都可能只表现为某个按钮点了 403，而且**静态编译、服务启动、全量单测三条线全绿**。
两个脚本合起来才算把「权限配置」纳入可验证范围。
