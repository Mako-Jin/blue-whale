# 工作区扫描报告（2026-11-09）

> 扫描范围：`backend/`、`frontend/`、`docs/`（plans / reviews / devlog / service-catalog）、`scripts/`、git 工作区状态、关键端口。
> 口径：只登记**当前仓库仍存在**的缺口；已完成项不再重复登记。
> 上一份：[2026-11-07 工作区扫描](2026-11-07-workspace-scan.md)。

## 一、扫描证据

| 项 | 结果 |
|---|---|
| 拉取 | `git pull origin main` 成功，`main` 与 `origin/main` **0/0 同步** |
| HEAD | `17f70def`（2026-11-08 CRM M5 营销 + M6 分析 + M1-4 客户合并 + M1-9 CSV 导入导出） |
| 工作区 | **干净**，无已修改、无未跟踪文件；`git stash` 为空 |
| 文档门禁 | `scripts/check-doc-consistency.ps1` → **DOC-CONSISTENCY-PASS**（父 POM：Spring Boot 4.0.8 / Spring Cloud 2025.1.2 / SCA 2025.1.0.0） |
| 端口 | 3306 / 6379 / 8848 / 8080 / 9001 / 9002 / 9003 / 9004 / 9005 / 9010 **全部 CLOSED** |
| 代码标记 | 主代码无未实现 `TODO/FIXME`；检索到的 `TODO` 均为 PM 业务状态码（`TODO_STATUS_ID`），非待办 |
| 新增服务 | `whale-mail`、`whale-map` 已入 `backend/whale-services/pom.xml` 与网关 `lb://` 路由；权限迁移 `migration-5.89`–`migration-5.99` 均已入库 |

## 二、进度快照

| 波次 | 状态 |
|---|---|
| ERP MRP / CRP / 生产增强 / ERP↔MES | 已交付（V13–V16，517 单测） |
| 商城 Wave 5.94–5.98 | 已交付（V1–V6，M0 内核 + 支付闭环 + 售后 + 计价 + 物流） |
| OA M0–M6 | 代码已交付（V7–V15，权限 1600–1799，后端 234 单测 + 前端多页） |
| CRM M0–M6 | 已交付（V1–V12，前端 8113，329 单测） |
| PDM M0–M5 + 收口 | 无仓库内代码缺口，剩 6 项外部依赖 |
| MES M0–M5 + Step 6a/6b | 已交付，剩数据范围过滤与归档清理（依赖未就绪） |
| LMS M0–M6 | 代码已交付（27 表 / 92 端点），真实库与试点复盘待环境 |
| 党政工会 M-P0–M-P4 | 代码已交付（20 表 / 95 端点 / 前端 14 页 / 158 单测） |
| 邮箱 5.89/5.90、地图 5.91–5.93 | 已落地并入库，实机与 MTA 待环境 |

## 三、任务

### 3.1 代码已完成、待验收

| 事项 | 说明 |
|---|---|
| 真实库迁移 + 起服冒烟 | ✅ 部分完成（2026-11-09）：auth 132 迁移全量重放、OA V1–V15 / CRM V1–V12 起服自动迁移成功，三服务冒烟 UP（见 §四-1/2 与 devlog 第四批）；**全链路联调（2026-11-09 第六批）：19/19 服务 UP + Nacos 注册，17 库期望表 vs 实际表比对一致（bluewhale_message 半截 schema 已补 V1），mall V1–V8 迁移 success** |
| 商城 5.95–5.98 实机冒烟 | ✅ 大幅完成（2026-11-09 第六批）：下单→支付（MOCK）→回调 PAID、退货退款全链（pay_refund SUCCESS / pay_order REFUNDED / 售后 APPROVED 结单）、换货全链（EXCHANGING→补发→EXCHANGED）经网关 8080 实测通过；并修复 V8 引入的 7 处订单流水 `to_status` 空 BUG（264 单测全绿）。剩余：前端页面联调、券叠加/品类券 E2E 造数验证 |
| CRM 联调 | ✅ 深链路实机验证（2026-11-09 第七批）：客户+联系人→活动→人群锁定（乐观锁）→触达任务 EMAIL→执行 COMPLETED/3 SENT→`bluewhale_message.msg_record`（biz_type=CRM_MARKETING）+ `msg_delivery` MOCKED 回执，全链经网关实测通过。**分析 cron 已迁移 whale-job 并实机验证（2026-11-09 第八批）**：`snapshot-cron`/`action-plan-cron` 进程内 `@Scheduled` 迁出为 job_info 1001/1002（`httpInvokeJobHandler` 经 Nacos 直连 `/crm/internal/analytics/**`），手动触发快照 12 条落库、幂等复测通过。**其余 @Scheduled 已全量迁移（2026-11-09 第十一批）**：退换货/商务订单下发与对账、应收快照、合同投影、公海回收 7 个触发点迁出为 job_info 1003–1009（V4 种子），MANUAL 7/7 SUCCESS + 每分钟任务 CRON 自动派发实测通过，CRM `@EnableScheduling` 移除、进程内调度清零。剩余：分析导出暂存 `MEDIUMTEXT`，待接 `whale-file`；**客户合并→whale-contract 重写确认双向未接入**（CRM 合并仅重写本地台账并实测通过；CRM 未调用 contract、contract 亦无「按客户重写」内网端点，功能缺口待立项，见 devlog 第七批遗留） |
| ERP↔MES 链路 | ✅ 深链路实机验证（2026-11-09 第七批）：ERP 建单→release→issue（领料转 IN_PROGRESS）→MES 收单→拆分/派工/开工/报工/完工→outbox 3 事件 SENT→ERP 4 回调全 APPLIED→订单 COMPLETED / completed_quantity=100 / mesSyncStatus=PUSHED→对账 page 与 `/{orderId}` 正常，全链经网关实测通过 |
| LMS / 党政工会 | 真实库跑迁移、起服冒烟、试点支部与学员复盘 |
| 邮箱 MTA | Mailcow 部署、域名/PTR/SPF/DKIM/DMARC、Auth 建用户自动开户；方案文档仍标「待评审」 |

### 3.2 规划中、未开工

| 来源 | 事项 |
|---|---|
| [roadmap.md](../plans/roadmap.md) 8.1 / 8.5 | 文档治理 DOC-01–10；`whale-scene` M3 3D（需 F-09 解冻四条件） |
| roadmap 11.3 | OA 出差预算超限条件分支（依赖 Workflow 条件表达式运行期求值） |
| roadmap 13.10 | 新域动工前先完成 OA M1A-6 移动端适配与 OA 真实用户试点 |
| [五服务总账](../plans/2026-11-06-five-services-backlog-stepwise.md) | MES 数据范围上下文过滤（依赖 auth `data-scope`）、归档物理清理（依赖 D14）；ERP M3 剩余（计划订单转单/联产品副产品/MES 联动）、M2 批次追溯/财务扩展（待 D5） |
| PDM 计划 §十九 | CAD 插件、3D/Office 转换、ngram/OCR、下载票据、DEPT ACL、真实联调 |
| 商城 | ~~售后超时自动关闭、举证图片、券叠加/品类券、按件重算运费、报表口径拆分、退货物流回写/WMS、换货~~ 前四项+换货+日报表已落地（2026-11-09 第五批，V7/V8）并实机联调通过（第六批）；剩余：券+活动+会员价组合计价、商品券/店铺券、重量运费/区域运费、WMS 入库联动、前端售后上传控件 |
| LMS / 工会 | 课件与封面接 `whale-file` 上传控件；工会会费逐笔流水与收缴率 |
| OA M6 | 安全/性能加固与试点部门验证（运营事项） |

## 四、阻塞

1. ~~**实机验证全阻塞**：本机 MySQL/Redis/Nacos/网关/业务端口实测全部关闭，所有「真实库 + 起服」动作无法执行。~~ ✅ 已解除（2026-11-09）：MySQL 8.0.26（3306）/ Redis（6379）/ RabbitMQ（5672）本机服务 + Nacos 2.4.0 standalone（8848，工作空间 `.toolchains/nacos`）就绪；auth（8081）/ OA（9004）/ CRM（9007）起服冒烟通过并注册 Nacos。注意：切换电脑遗留的用户级环境变量 `SPRING_DATA_REDIS_PASSWORD` 会使 Redisson 对无密码本机 Redis 发 AUTH 而启动失败，启动前需在会话内移除该变量（见 devlog 第四批）。
2. ~~**真实库迁移未执行（累计）**：见 §3.1 首行，覆盖 8 条产品线。~~ ✅ 大幅推进（2026-11-09）：auth 132 个手写迁移全部重放成功（含 5.87 path 扩宽 + 5.77-video / 5.79-mes-m3/m4 重放）；OA V1–V15、CRM V1–V12 已由 Flyway 在起服时自动执行（74 / 55 张表）；其余 whale-apps / whale-services 均启用 Flyway，起服即自动迁移；另补建 `bluewhale_im`、`bluewhale_scene` 空库。
3. **决策阻塞**：D5（ERP 批次/财务扩展）、D2（CRM→ERP 客户主数据映射）、Workflow 条件表达式求值、auth `data-scope`、D14（分区/对象存储）。
4. **外部依赖**：PDM 六项；生产 compose 覆盖层缺 Let's Encrypt 证书（`pending-issues` 中 **V-09 唯一未闭环项**）；Mailcow 需公网 25/465/587 与域名 PTR。
5. ~~**CI 门禁阻塞**：`whale-oa` Checkstyle 325 条 error，`checkstyle:check` 实测失败。~~ ✅ 已修复（2026-11-09），见 §五 首行。

## 五、BUG 与风险

| 级别 | 问题 | 证据 | 建议 |
|---|---|---|---|
| **高** | `whale-oa` Checkstyle **325 条 error**，门禁无法转绿；集中于 `collab` / `administrative` / `document` / `culture` 的 `MissingJavadocMethod` 与少量 `UnusedImports` | 本机 `mvnw -pl backend/whale-apps/whale-oa checkstyle:check` → BUILD FAILURE | ✅ 已修复（2026-11-09）：57 文件补 Javadoc 325 处、清理 3 处未用 import，`checkstyle:check` 转绿 |
| **中** | CRM 客户列表「负责人」筛选静默失效：`CustomerPageParams` 无 `ownerId`，前端传参被丢弃 | `whale-crm/.../interfaces/support/CustomerPageParams.java` 仅 keyword/status/level/source/industry/createdFrom/To | ✅ 已修复（2026-11-09）：补 `ownerId` 并绑定到 `CustomerQuery`，前端注释同步订正 |
| **中** | OA 审批通知同步发送：`ApprovalNotificationListener` 三处 `@EventListener`，消息服务抖动直接拉长审批接口耗时 | `whale-oa/.../approval/application/listener/ApprovalNotificationListener.java` | ✅ 已修复（2026-11-09）：改 `@TransactionalEventListener(AFTER_COMMIT)` + `oaNotificationExecutor` 异步投递 |
| 中 | OA 附件仅登记 `fileId`，未校验上传者与租户，存在跨用户引用 | 11-07 扫描登记，本次未复核 | ✅ 已修复（2026-11-09）：`FileRefAppServiceImpl.register` 收口校验（文件存在、未删除、状态正常、上传人 = 当前用户，元数据以服务端为准）；租户校验待 whale-file 聚合暴露 tenantId 后补 |
| 中 | JaCoCo「零测试模块即跳过」盲区：`whale-common-core` / `whale-common-domain` / `whale-auth-sdk` | `pending-issues` N-11 观察项 | ✅ 已修复（2026-11-09）：三模块补 5 个测试类 / 20 个用例（`BizExceptionTest`、`IdentifierTest`、`BaseValueObjectTest`、`AbstractAggregateTest`、`UserContextTest`） |
| 低 | `/auth/refresh` 返回 HTTP 200 + 业务码 401，属接口约定差异 | 2026-11-06 扫描登记，未变更 | 先定口径再改，勿单独改动 |
| 观察 | 权限/菜单号段靠人工核对：5.89 邮箱菜单曾撞工会 481，被 `WHERE NOT EXISTS` 静默吞掉 | Wave 5.88 M-P3 / 5.89 §7 | ✅ 已落地（2026-11-09）：新增 `scripts/check-permission-id-ranges.ps1`，首扫登记 11 处历史撞号至基线；并修复 CRM M6 1900–1905 与 LMS 5.87 的 1900–1949 撞号（回收至 1834–1839） |
| 中（联调发现） | whale-mall 售后动作写订单流水 `to_status` 传 null（7 处），`mall_order_log` 非空列 500，V8 全部售后操作不可用 | devlog 第六批 #6 | ✅ 已修复（2026-11-09）：from=to 记订单当前状态，264 单测全绿 |
| 中（联调发现） | `map/pdm/lms/mall` Spring Boot 4 下启动失败：Jackson 2 `ObjectMapper` Bean 不再自动装配 | devlog 第六批 #1 | ✅ 已修复（2026-11-09）：四服务补 `Jackson2CompatConfig` |
| 高（深链路发现） | CRM 人群锁定 500：`Parameter 'MP_OPTLOCK_VERSION_ORIGINAL' not found`——`.m2` 中 `whale-spring-boot-starter-1.0.0-SNAPSHOT` 是 11-02 旧快照，缺 11-04 加入的乐观锁拦截器 | devlog 第七批 #1 | ✅ 已修复（2026-11-09）：`-pl starter -am install` 刷新快照 + 重打业务 jar；**系统性教训：`-pl` 打包从 `.m2` 解析兄弟模块快照，公共模块改动后必须先 install 再打包，且需解压嵌套 jar 验证字节** |
| 中（深链路发现） | OA 审批中心 50301（Workflow 内网调用失败）：`WorkflowApprovalGatewayImpl.get()` 传相对路径不拼 baseUrl，"Target host is not specified" | devlog 第七批 #2 | ✅ 已修复（2026-11-09）：新增 `absoluteUri()` 统一补全 |
| 中（深链路发现） | CRM→message 投递契约错配：messageUrl 误配 file 端口 8085，且请求体为旧契约 | devlog 第七批 #3/#4 | ✅ 已修复（2026-11-09）：messageUrl 改 8094；`MessageInternalClient` 按新契约重建（bizType/bizId/templateCode/channels/recipients） |
| 中（深链路发现） | MES 报工 500：outbox 幂等键 65 字符超 `source_event_id` VARCHAR(64)（`Data too long`） | devlog 第七批 #5 | ✅ 已修复（2026-11-09）：V10 迁移扩宽 `mes_common_outbox`/`mes_eq_status_event`/`mes_cl_metric_sample` 三表至 VARCHAR(128) |
| 中（深链路发现） | MES outbox 事件永远 PENDING：`MesOutboxDispatcher` 默认 disabled 且 erp-base-url 未配置 | devlog 第七批 #6 | ✅ 已修复（2026-11-09）：application.yml 补 `whale.mes.outbox.enabled` 与 `whale.mes.integration.erp-base-url` |
| 中（深链路发现） | 15 个库缺 `audit_log` 表，审计切面写库失败放大为业务 500 | devlog 第七批 #7 | ✅ 已临时补齐（手工 `CREATE TABLE LIKE`）；迁移缺口登记 §六-7 |
| 中（迁移验证发现） | 实机 `bluewhale_job.job_log` 缺 `run_id`/`trigger_type` 列（半截 schema 被 baseline 跳过，V1 本就定义）→ `insertRunningLog` 失败，所有任务触发无执行记录 | devlog 第八批 | ✅ 已修复（2026-11-09）：实机 ALTER 补两列 + `idx_run_id`；环境 schema 缺口，仓库 V1 无需改动 |

## 六、待办（文档与工程卫生）

1. ~~**roadmap 复选框陈旧**：8.7 / 8.8 仍为未勾选，但 CRM 后端与前端 8113 均已完成。~~ ✅ 已订正（2026-11-09）：8.7 / 8.8 勾选并更新说明。
2. ~~**五服务总账 §四红线过期**：仍写「5.89 邮箱 / 5.91 地图未提交不得 `git add`」，实际已在 pom、网关与 `migration-5.89/5.91` 中。~~ ✅ 已订正（2026-11-09）：红线标记闭环，并补号段扫描要求。
3. ~~**5.89 文档自相矛盾**：§8 写「不支持附件发送/接收（M1）」，§10 又把「附件收发」划为已完成。~~ ✅ 已订正（2026-11-09）：§8 该行标记「M1 已落地，见 §12」。
4. ~~**Wave 5.98 无 devlog 章节**，mall 端点与测试总数未回填 `service-catalog.md`。~~ 复核（2026-11-09）：`docs/devlog/2026-11-07.md` Step 6 已存在；`service-catalog.md` 记 18 表 / 60 端点，与迁移脚本及 12 个 Controller 的 60 个映射一致，无需回填。
5. ~~**OA V12 迁移**：若本地库留有失败记录，执行前需 `flyway repair` 或清理 `flyway_schema_history` 失败行。~~ ✅ 已核实（2026-11-09 第十批）：实查实机 `flyway_schema_history`，OA V11–V15 全部 success、无失败行，无需 repair。
6. ~~**上线必检**：`docs/design/network-boundary.md`（直连业务端口收口）。~~ ✅ 应用层两道防线已实测（2026-11-09 第十批）：网关 internal-deny 通配收口后 403 矩阵 8/8（含 `/audit/log/ingest/**`，非 internal 路径不误伤）；服务侧 `InternalTokenFilter` 直连 9007 无令牌 403 / 带令牌 200；启动脚本注入本地联调共享令牌。部署边界（K8s NetworkPolicy / 安全组）与 prod compose 证书（V-09）仍为上线前部署动作。
7. ~~**audit_log 迁移缺口（2026-11-09 第七批登记）**：15 个服务库（oa/mes/pdm/pm/workflow/job/file/form/i18n/mail/map/video/music/im/scene）的 baseline 均未含审计表 DDL，本次为联调手工补表。~~ ✅ 已闭环（2026-11-09 第九批）：14 个服务各补 `V{n}__audit_log.sql`（IF NOT EXISTS 幂等），13 服务脚本约定测试全绿，job/map 实机重放 success；i18n 无独立服务模块，其库保持手工表。

## 七、本轮核实已闭环（从旧清单移除）

- `service-catalog.md` V7/V8/Mall V6 补记：门禁已 PASS。
- OA M2 报销/考勤/用车/用章/采购后端与前端 10 页：已提交（`8b113470`、`037b39f7`、`fb1efd55`）。
- CRM M5 营销、M6 分析、M1-4 客户合并、M1-9 导入导出：已交付（`17f70def`）。
- 邮箱 / 地图服务与前端子应用：已入库并注册网关路由。
- `pending-issues-2026-10-27.md` 的 T/S/F/V/N 系列：全部闭环，仅 V-09 待证书。
- 工作区未跟踪文件与 stash 红线：已消除（本次 `git status` 与 `git stash list` 均为空）。

## 八、建议执行顺序

1. 修 `whale-oa` Checkstyle 325 条 → 全量 `checkstyle:check` 转绿（唯一实测红灯）。
2. 修 CRM `ownerId` 筛选 + OA 通知改事务后异步（两处小改，收益明确）。
3. 订正 5 处文档失真（roadmap 8.7/8.8、五服务 §四、5.89 §8、5.98 devlog、catalog 数字）。
4. 起 MySQL/Redis/Nacos + 按 §3.1 清单跑迁移与冒烟，解除实机阻塞。
5. 再开功能缺口：ERP M3 剩余 / MES 两项 / Mall 售后增强。
