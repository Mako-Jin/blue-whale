# Wave 5.80 PDM M2 剩余任务实施计划（M2-4..M2-10）

## Context

M2-1/2/3（版本规则、ECR、ECO）已完成并推送（HEAD `7b4e2939`，与 `origin/main` 一致）。
计划 [2026-11-04-wave5.80-pdm-panorama.md §八](../../docs/plans/2026-11-04-wave5.80-pdm-panorama.md)
中 M2-4..M2-10 仍为待做/部分完成。本计划一次补齐 M2 剩余七项，随后按用户要求继续 M3、M4、M5，
最后扫描 OA 计划待办。

目标：ECN 生效（不早于 ECO 审批通过日、生效后旧版本 OBSOLETE）、OA 审批发起与内网回调幂等、
变更追溯只追加、到期生效每日任务（单实例锁）、whale-message 通知（失败落日志可重试）、
前端 ECN/追溯页面、权限 963–969 与菜单 192–194。

## 关键设计决策

1. **一张迁移 V9** 建 4 表并扩 2 表：
   - `pdm_chg_notice`（ECN：notice_no、eco_id、title、content、impact_json、notify_targets、notify_channels、
     effective_from、status、notified_time、effective_time、close_time；`(tenant_id, eco_id)` 唯一，一 ECO 一 ECN）。
   - `pdm_chg_trace`（只追加：biz_type ECR/ECO/ECN、biz_id/biz_no、object_type/object_id/object_code、
     action、from_version/to_version、content、operator_id、trace_time）。
   - `pdm_chg_notify_log`（notice_id、event_type、recipients、title/content、status SUCCESS/FAILED、
     retry_count、last_error、idempotency_key；`(tenant_id, idempotency_key)` 唯一）。
   - `pdm_chg_job_lock`（lock_key 主键、owner、locked_until）。
   - `pdm_chg_request` / `pdm_chg_order` 各加 `approval_instance_id`、`approval_status`、`approval_sync_time`、
     `approval_source`（列扩展用 information_schema 逐列幂等，沿用 V4 写法）。
2. **ECN 状态机** `DRAFT → NOTIFIED → EFFECTIVE → CLOSED`，旁支 `CANCELLED`。
   - 仅 `APPROVED/EXECUTING/COMPLETED/CLOSED` 的 ECO 可建 ECN；
   - `effectiveFrom` 必须 ≥ ECO `approveTime`，否则 `PARAM_ERROR`「生效日期不能早于审批通过日」；
   - 手动 `effective` 要求 `effectiveFrom <= now`，提前生效被拒；
   - 生效时按 ECO 已完成任务（`targetType` + `resultVersionNo`）把同对象更低 `versionSeq` 的 `RELEASED`
     文档版本 / BOM 版本置 `OBSOLETE`；目标版本不存在则跳过并记 trace；
   - 通知在状态更新后执行，异常只落 `pdm_chg_notify_log`，不回滚生效。
3. **OA 集成**：`PdmChgWorkflowClient` 接口 + `DiscoveryClient` 实现，`POST /workflow/instance`
   （body：definitionId（可配 `pdm.change.definition-id`，默认空）、title、businessKey、variables），
   返回 `data.id`。ECR/ECO `submit` 时尝试发起；不可用/未配置则 `approvalSource=MANUAL`，
   人工审批仍由既有 `sys:pdm:change:*:approve` 权限码保护。
   内网回调 `POST /pdm/internal/change/approval-callback`（body：instanceId、status 0/1/2/3、comment）：
   按 `approvalInstanceId` 定位 ECR/ECO；`approvalStatus` 已是目标状态或业务已离开待审批 → 直接返回
   当前 VO（幂等，不重复流转）。
4. **追溯**：`PdmChgTraceRecorder` 统一写点；ECR create/submit/approve/reject/close、
   ECO create/assess/submit/approve/reject/execute/task-complete/complete/cancel/close、
   ECN create/notify/effective/close 均写 trace；按 impact/task 目标展开为 `objectType/objectId` 行。
   仅提供查询端点，无编辑/删除。
5. **到期任务**：`PdmChgEffectiveJob`（`@Scheduled(cron="${pdm.change.effective-cron:0 0 2 * * ?}")`，
   `@ConditionalOnProperty(prefix="pdm.change", name="effective-job-enabled", matchIfMissing=true)`）；
   启动类补 `@EnableScheduling`；用 `pdm_chg_job_lock` 行锁（INSERT IGNORE + 条件 UPDATE）实现
   单实例；逐条调用 `noticeService.effective(id)`，状态已 EFFECTIVE 直接跳过（重复执行不重复生效）；
   失败 `log.error` 并尝试发一条告警消息。
6. **通知**：`PdmChgMessageClient` 接口 + 实现，`POST /message/internal/send`（tenantId/bizType/
   bizId/idempotencyKey/title/content/channels/recipients）；成功/失败都写 `pdm_chg_notify_log`；
   重试端点 `POST /pdm/change/notice/{id}/retry-notify` 重新发送失败记录。
7. **权限/菜单**：新增 `migration-5.80-pdm-ecn.sql`（963–969、菜单 192–194），
   `tenant_admin/pdm_manager/pdm_change_manager` 全量，`pdm_member` 只读 963/968。
   权限码：
   - 963 list、964 add、965 edit、966 notify、967 effective、968 trace:list、969 notify-log:list。

## 后端文件（新增，包 `com.bluewhale.pdm.change`）

- `entity/`：`PdmChgNotice`、`PdmChgTrace`、`PdmChgNotifyLog`、`PdmChgJobLock`
- `mapper/`：对应 4 个 Mapper（JobLock 含 INSERT IGNORE + 条件 UPDATE）
- `support/`：`PdmChgNoticeStatus`、`PdmChgBizType`、`PdmChgTraceAction`、`PdmChgApprovalStatus`、
  `PdmChgApprovalSource`、`PdmChgRecorder`（trace 写点组件）
- `dto/`：NoticeSave/Query、ApprovalCallbackRequest、TraceQuery
- `vo/`：NoticeVO、TraceVO、NotifyLogVO
- `client/`：`PdmChgWorkflowClient`(+Impl)、`PdmChgMessageClient`(+Impl)
- `service/`：`PdmChgNoticeService`、`PdmChgTraceService`、`PdmChgApprovalCallbackService`
- `service/impl/`：上述实现 + `PdmChgEffectiveJob`
- `controller/`：`PdmChgNoticeController`、`PdmChgTraceController`、`PdmChgInternalCallbackController`

## 后端改动（复用既有模式）

- `PdmChgRequest` / `PdmChgOrder` 增 4 个审批快照字段；ECR/ECO `service.impl` 在 create/submit/
  approve/reject/execute/task-complete/complete/cancel/close 处调用 trace recorder，submit 处尝试 OA 发起。
- 复用：`PdmBomRequestContext.requireUserId`、`BizException`、`PageResult`、`MAX_PAGE_SIZE=200`、
  `PdmVersionNo`、`PdmDocVersionMapper`/`PdmBomMapper`（生效置 OBSOLETE）、
  `InternalTokenSupport.restClient()` + `DiscoveryClient`（客户端范式）。
- `WhalePdmApplication` 加 `@EnableScheduling`。

## 单元测试（Mockito + TableInfoHelper）

- `PdmChgNoticeServiceImplTest`：提前生效被拒；通知失败不回滚 EFFECTIVE；生效置旧版本 OBSOLETE；
  重复生效幂等；无效 ECO 状态拒绝建 ECN。
- `PdmChgApprovalCallbackServiceImplTest`：首次回调流转；重复回调幂等；未知实例忽略。
- `PdmChgTraceServiceImplTest`：只追加（无 update/delete 调用）与按对象查询。
- `PdmChgEffectiveJobTest`：锁未获取不执行；已生效跳过；重复执行不重复生效。
- `PdmChgNotifyServiceImplTest`：失败写 FAILED 日志、重试成功更新 SUCCESS。

## 前端（`frontend/platform-apps/whale-pdm`）

- 新增 `src/api/changeNotice.ts`、`src/api/changeTrace.ts`；
- 新增 `src/pages/change/notice.tsx`（列表 + 新建/编辑 + 通知/生效/关闭 + 通知日志抽屉）、
  `src/pages/change/trace.tsx`（按对象类型/ID 查询 + Timeline）；
- `App.tsx` 增 `/change/notice`、`/change/trace` 菜单与路由（权限码 963/968）。

## 文档与提交

- 更新计划 §八状态、`docs/service-catalog.md`、`docs/requirements/register.md`（REQ-FUNC-052）、
  `docs/devlog/2026-11-05.md`、新增 `docs/reviews/2026-11-05-wave5.80-pdm-m2-ecn.md`、
  新增 `docs/specs/2026-11-05-wave5.80-pdm-m2-ecn/{spec,tasks}.md`。
- 验证：`.\mvnw.cmd -pl :whale-pdm test`；`pnpm --dir frontend --filter whale-pdm lint` / `build`。
- 显式 `git add` 本次路径 → `git commit` → `git push origin main`。

## 后续里程碑（同一节奏，逐段提交推送）

- **M3**（工艺：route/step/tooling/sop/publish + MES 契约 + 内网 SOP 内容 + 前端 + 权限 970–979/菜单 195–197）。
- **M4**（交付物、评审、2D/3D 预览任务与查看器、检索增强、权限 980–989/菜单 198–201）。
- **M5**（CAD 契约、ERP 物料同步、BOM/MES/OA 对账、同步运维页、权限 990–999/菜单 202–203）。
- **OA 扫描**：读取 `docs/plans/2026-11-04-wave5.82-oa-panorama.md` 的未完成项并继续实现。

## 验证清单

1. 后端全量测试通过（新增 5 个测试类，既有 133 用例不回归）。
2. 前端 `lint`、`tsc -b`、`vite build` 通过。
3. Flyway 迁移测试通过（V9 命名/递增/无破坏性语句）。
4. 回调幂等、提前生效拒绝、通知失败不回滚、重复生效不重复置 OBSOLETE 均有断言覆盖。
