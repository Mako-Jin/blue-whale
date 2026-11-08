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

**P0 剩余**：CRM 营销发送投递回执 —— 已确认仓库**没有 whale-message 模块**，CRM 的 `whale.crm.marketing.message-url` 默认指向 `127.0.0.1:8085`（该端口现被 `bw-file` 占用）。已决定方案：**先建最小 whale-message 服务**（模块 + 表 + 发送/回执接口 + compose 段，端口避开 8085），再验 CRM 触达状态机与回执幂等。
