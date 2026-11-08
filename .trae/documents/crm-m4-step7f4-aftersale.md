# Step 7f-4：CRM M4 售后服务实施计划（分 4a / 4b / 4c 三批）

## Context

Wave 5.83 CRM 的 M0–M3 已全部收口（7f-1 合同回款、7f-2 商务订单、7f-3 发票与应收对账，2026-11-07）。按 [CRM 全景计划 §M4](file:///f:/blue-whale/docs/plans/2026-11-04-wave5.83-crm-panorama.md#L171-L177) 与 [设计稿 §3.4 / §6.2](file:///f:/blue-whale/docs/design/crm-module-panorama.md)，M4 范围为：工单 + SLA + 知识引用 + 满意度 + 退换货，迁移 `V10__service.sql` 9 表、权限 1881–1890、菜单 432–436。

**已确认决策**（D7 / D8，2026-11-07 沿用设计稿）：
- SLA：P1 2h 响应 / 8h 解决，P2 4h / 24h，P3 8h / 72h；7×8 工作时段（每日 09:00–18:00，含 1h 休息，不做节假日表）。
- 知识：正文归 `whale-doc`，CRM 只存 FAQ 元数据 + `docId`/版本引用；文档服务不可用降级为 CRM 标题列表。

**分批**（每批独立验证、提交、推送、四段式报告）：
- **7f-4a 工单与 SLA**：`crm_ticket` / `crm_ticket_action` / `crm_ticket_sla` / `crm_service_calendar`，工单状态机、SLA 时限与暂停、处理记录、分派/升级/解决/关闭/重开，前端工单页 + SLA 设置页。
- **7f-4b 知识引用与满意度**：`crm_kb_ref` / `crm_satisfaction_survey` / `crm_satisfaction_response`，FAQ 元数据与文档引用、工单关闭生成一次性令牌问卷、1–5 分 + 评论 + 维度、低分回访动作，前端知识库页 + 满意度页。
- **7f-4c 退换货**：`crm_return_request` / `crm_return_item` + ERP 侧 `V18__scm_crm_return_source.sql` 与内网退货契约，审批 → outbox 幂等下发 ERP 退货过账 → 回读 `erpReturnNo`/状态，前端退换货页。

**硬纪律**：只追加 Flyway；不写 ERP 库存/凭证（CRM 不写库存，ERP 执行）；`/crm/internal/**` 只给 workflow/ERP 回调；精确 `git add`；每批后 `scripts/check-doc-consistency.ps1` PASS；不触碰 §四红线文件（**尤其 `whale-gateway/application.yml` 不得提交**）。

## 现状事实（已核对）

- CRM Flyway 最新 `V9__invoice.sql`，下一版 **`V10__service.sql`**；`service` 包尚不存在（`com.bluewhale.crm.service` 为新建上下文）。
- CRM 权限已用 1874–1880，1881+ 空闲；菜单已用 430，431+ 空闲；错误码已用 41062，41063+ 空闲。
- CRM 内网客户端范式：[WorkflowInvoiceApprovalClient.java](file:///f:/blue-whale/backend/whale-apps/whale-crm/src/main/java/com/bluewhale/crm/commerce/infrastructure/client/WorkflowInvoiceApprovalClient.java)（`InternalTokenSupport` + `RestClient` + `code==0` + 租户头 + 用户/权限/角色透传）。
- outbox 已存在：`crm_common_outbox`（[OrderOutboxEntity.java](file:///f:/blue-whale/backend/whale-apps/whale-crm/src/main/java/com/bluewhale/crm/commerce/infrastructure/persistence/entity/OrderOutboxEntity.java)），7f-2 的扫描 Job + 退避重试可复用。
- ERP 退货能力已存在：`erp_scm_sales_return` / `erp_scm_sales_return_line`（`V9__scm_returns.sql`）；[SalesReturnAppServiceImpl.java](file:///f:/blue-whale/backend/whale-apps/whale-erp/src/main/java/com/bluewhale/erp/scm/application/service/impl/SalesReturnAppServiceImpl.java#L72-L165) 已校验「订单已发货 + 行可退量 ≤ 已发量 − 已退量」，`post` 同事务做库存入库、应收 offset 与红字凭证；内网控制器 [ErpInternalScmController.java](file:///f:/blue-whale/backend/whale-apps/whale-erp/src/main/java/com/bluewhale/erp/controller/ErpInternalScmController.java) 已有 3 个 CRM 订单端点，可顺延。ERP 迁移最新 `V17`，下一版 `V18`。
- whale-doc 仅暴露 `/doc/internal/page/changes`、`/doc/internal/page/{id}`（[DocInternalController.java](file:///f:/blue-whale/backend/whale-services/whale-doc/src/main/java/com/bluewhale/doc/interfaces/controller/DocInternalController.java)），**无内网标题搜索**；CRM 标题/标签落本地元数据，引用有效性用 `page/{id}` 校验。
- 网关白名单仅 `/auth/login`、`/auth/refresh`、`/actuator/**`、`/v3/api-docs/**`、`/doc.html` 与票据类流式路径；`whale-gateway/application.yml` 属 §四红线（含未提交 5.89/5.91 改动），**本批不改不提交**。

## 7f-4a：工单与 SLA

### 数据表（`V10__service.sql`，仅 `CREATE TABLE IF NOT EXISTS`，无种子）
| 表 | 关键字段 | 约束 |
|---|---|---|
| `crm_ticket` | `ticket_no`、`customer_id/code/name`、`contact_id/name`、`contract_id`、`order_id`/`erp_order_no`、`product_code`/`serial_no`、`category`、`priority(P1/P2/P3)`、`title`、`description`、`status`、`assignee_id`/`assignee_name`、`owner_id`/`dept_id`、SLA 快照（`response_deadline`/`resolve_deadline`/`first_responded_at`/`paused_at`/`paused_seconds`/`resolved_at`/`closed_at`）、`solution`、`reopen_count`、`lock_version`、审计字段 | `uk(tenant_id, ticket_no)`；索引 `(tenant_id, status, priority)`、`(tenant_id, customer_id)`、`(tenant_id, assignee_id, status)` |
| `crm_ticket_action` | `ticket_id`、`action_type(ASSIGN/PROCESS/COMMENT/PAUSE/RESUME/ESCALATE/RESOLVE/CLOSE/REOPEN/CANCEL/FOLLOW_UP)`、`from_status/to_status`、`operator_id/name`、`content`、`created_at` | 追加式，无更新/删除；索引 `(tenant_id, ticket_id, created_at)` |
| `crm_ticket_sla` | `priority`、`response_hours`、`resolve_hours`、`enabled` | `uk(tenant_id, priority)`；无行时用 D7 代码默认值 |
| `crm_service_calendar` | `calendar_code`、`workday_start`、`workday_end`、`break_minutes`、`weekdays`、`enabled` | `uk(tenant_id, calendar_code)`；无行时默认 `09:00–18:00 / 60 / 1–7` |

### 领域与口径
- `Ticket` 状态机：`NEW → ASSIGNED → PROCESSING ⇄ WAITING_CUSTOMER → RESOLVED → CLOSED`；`REOPENED → PROCESSING`；任意非 CLOSED/CANCELLED 可 `CANCEL`（需原因）。不可跳转，非法迁移抛 `TICKET_STATE_CONFLICT`。
- 关闭必须携带解决方案（写 `solution` 且追加 `CLOSE` 动作）；重开保留原工单号、`reopen_count+1`。
- SLA 时限在**建单/分派/升级**时按优先级策略 + 工作日历计算（工作时段推进，跨日顺延）；`WAITING_CUSTOMER` 暂停累计、`RESUME` 把两个截止时间顺延已暂停时长；暂停/恢复必须成对。
- 首次响应：首次 `PROCESSING/COMMENT` 动作写入 `first_responded_at`；逾期只读派生（`responseOverdue`/`resolveOverdue`），不落定时任务。
- 升级：`ESCALATE` 升一级（P3→P2→P1，P1 不变），按新优先级重算截止时间并追加动作。
- 数据范围：非 `allDataScope()` 按 `owner_id`/`assignee_id` 过滤；跨租户按不存在。

### 接口与权限
| 方法与路径 | 权限码 | 说明 |
|---|---|---|
| `GET /crm/ticket/page`、`/crm/ticket/{id}` | 1881 `sys:crm:ticket:list` | 分页/详情（含动作时间线、SLA 逾期标记） |
| `POST /crm/ticket` | 1882 `sys:crm:ticket:create` | 建单 |
| `POST /crm/ticket/{id}/assign` | 1883 `sys:crm:ticket:assign` | 分派（指定处理人） |
| `POST /crm/ticket/{id}/process|comment|pause|resume|escalate|resolve|close|reopen|cancel` | 1884 `sys:crm:ticket:handle` | 处理动作，全部追加 `crm_ticket_action` 并挂 `@AuditLog` |
| `GET/PUT /crm/service/sla`、`GET/PUT /crm/service/calendar` | 1885 `sys:crm:service:sla:manage` | 3 档策略与工作日历读写 |

- 错误码：41063 `TICKET_NOT_FOUND`、41064 `TICKET_STATE_CONFLICT`、41065 `TICKET_SLA_UNAVAILABLE`、41066 `TICKET_ASSIGNEE_INVALID`。
- 前端：`pages/ticket`（列表 + 新建 + 详情时间线 + 分派/处理/暂停/恢复/升级/解决/关闭/重开 + SLA 倒计时与逾期 Tag）、`pages/service-sla`（策略与日历）；`api/service.ts`；`App.tsx` 注册菜单 432 工单 / 433 SLA 设置与路由。

## 7f-4b：知识引用与满意度

### 数据表（同一 `V10__service.sql`）
| 表 | 关键字段 | 约束 |
|---|---|---|
| `crm_kb_ref` | `title`、`category`、`product_tags`、`fault_tags`、`visible_scope(PUBLIC/INTERNAL)`、`doc_id`、`doc_version`、`doc_available`、`hit_count`、`status`、`owner_id`、`dept_id` | 索引 `(tenant_id, category)`；`uk(tenant_id, doc_id, doc_version)` 允许空 docId |
| `crm_satisfaction_survey` | `survey_no`、`ticket_id`、`token_hash`、`status(SENT/USED/EXPIRED/REVOKED)`、`sent_at`、`expires_at`、`used_at` | `uk(tenant_id, survey_no)`、`uk(tenant_id, token_hash)` |
| `crm_satisfaction_response` | `survey_id`、`ticket_id`、`score(1–5)`、`comment`、`speed_score`、`attitude_score`、`professional_score`、`low_score`、`follow_up_required`、`submitted_at` | `uk(tenant_id, survey_id)`（一问卷一答卷） |

### 口径
- 知识引用：`title`/标签检索与命中统计落 CRM；保存时调用 whale-doc `/doc/internal/page/{docId}` 校验并写 `doc_available`，不可用只降级不改写历史（`doc_available=false`）；正文不复制。引用动作 `hit_count+1`。
- 满意度：工单 `CLOSED` 时（或手工）生成 `survey_no` 与一次性令牌；`token_hash = HMAC-SHA256(tenantId + surveyNo + nonce, secret)`，仅存 hash，密钥 `whale.crm.survey.secret` 默认随机但生产必须配置；有效期 `whale.crm.survey.expire-days`（默认 7 天）；提交一次后置 `USED`，重复/过期抛 41068，同 IP/同工单简单限流（内存计数，默认每 10 分钟 5 次）。
- **匿名 H5 入口不在本批**：对外 `/crm/public/**` 需改网关白名单，而 `whale-gateway/application.yml` 属红线不得提交；本批先进「令牌提交端点」`POST /crm/satisfaction/response`（由客服在收到客户回复后录入 token + 评分，token 仍一次性/有效期校验），匿名 H5 列为后续项。
- 低分（总分 ≤ 3 或任一维度 ≤ 2）写 `follow_up_required=true`，并向原工单追加 `FOLLOW_UP` 动作（处理人 = 工单处理人/负责人），供 M6 行动计划消费。
- 服务开关：`whale.crm.service.enabled`（默认 true）、`whale.crm.survey.enabled`（默认 false，避免误发）、`whale.crm.docs.enabled`（默认 false）。

### 接口与权限
| 方法与路径 | 权限码 | 说明 |
|---|---|---|
| `GET /crm/kb/page`、`/crm/kb/{id}`、`GET /crm/kb/search` | 1886 `sys:crm:kb:list` | 元数据分页/详情/标题标签检索 |
| `POST/PUT/DELETE /crm/kb`、`POST /crm/kb/{id}/hit` | 1887 `sys:crm:kb:manage` | 维护与命中计数 |
| `GET /crm/satisfaction/page`、`GET /crm/satisfaction/{id}`、`GET /crm/satisfaction/stats` | 1888 `sys:crm:survey:manage` | 问卷与结果/低分统计 |
| `POST /crm/satisfaction/send`（按工单生成或重发）、`POST /crm/satisfaction/response`（token 提交） | 1888 | 提交按令牌校验，不需要额外权限码 |
| `POST /crm/satisfaction/{id}/revoke` | 1888 | 作废问卷 |

- 错误码：41067 `KB_REF_NOT_FOUND`、41068 `SURVEY_TOKEN_INVALID`。
- 前端：`pages/kb`（列表/维护/标签/引用文档/命中）、`pages/satisfaction`（问卷列表、生成/发送/作废、结果与低分统计）；`api/service.ts` 扩展；`App.tsx` 注册菜单 434 知识库 / 435 满意度。

## 7f-4c：退换货（本期仅退货执行；换货字段预留，补发人工在 ERP 建单）

### CRM 迁移（同一 `V10__service.sql`）
| 表 | 关键字段 | 约束 |
|---|---|---|
| `crm_return_request` | `return_no`、`request_type(RETURN/EXCHANGE)`、`order_id`、`erp_order_no`、`customer_id/code/name`、`reason`、`status`、`approval_instance_id`、`erp_return_no`、`erp_status`、`push_attempts`、`last_error`、`owner_id`、`dept_id`、`lock_version` | `uk(tenant_id, return_no)`、`uk(tenant_id, order_id, approval_instance_id)`（空值不冲突） |
| `crm_return_item` | `request_id`、`line_no`、`order_line_no`、`item_code`、`item_name`、`quantity`、`unit_price`、`amount` | `uk(tenant_id, request_id, line_no)` |

### ERP 侧（`V18__scm_crm_return_source.sql`，仅 `ALTER TABLE` + 唯一键）
- `erp_scm_sales_return` 追加 `crm_return_no`、`source_type`、`source_id`，`uk(tenant_id, crm_return_no)`。
- `SalesReturnInternalCreateCommand`（`crmReturnNo`、`salesOrderId` 或 `erpOrderNo`、`docDate`、`remark`、`lines[orderLineNo, quantity]`）；`SalesReturnAppServiceImpl.createInternal`：按 `crmReturnNo` 回查幂等返回原单，缺失则复用 `create` 后立即 `post`，单号固定 `CRM-SR-{crmReturnNo}`。
- 内网端点（`ErpInternalScmController` 追加）：`POST /erp/internal/scm/sales-return`（创建并过账）、`GET /erp/internal/scm/sales-return/by-crm-return-no`（回读状态/金额）。
- 库存入库、应收 offset、红字凭证全部复用既有 `post` 逻辑，CRM 不写库存。

### CRM 口径
- 状态机：`DRAFT → PENDING_APPROVAL → APPROVED/REJECTED → EXECUTING → COMPLETED`；`FAILED` 可重试；`DRAFT/REJECTED` 可取消；审批通过同事务写 `crm_common_outbox`（`event_type=RETURN_REQUEST_EXECUTE`）。
- 前置校验：来源 CRM 订单必须已有 `erp_order_no`；行数量 ≤ 该行可退量（`crm_sales_order_line.quantity` − 已审批/执行中/已完成退货数量），ERP 侧以已发量 − 已退量为最终权威，超退返回并落 `FAILED`。
- 扫描 Job 复用 outbox 退避重试；`POST /crm/return/{id}/retry` 手工重试；`GET/POST` 对账按 `crmReturnNo` 回读 ERP。
- 审批：`whale-workflow` 定义 `RETURN_REQUEST`，回调 `POST /crm/internal/return/approval-callback` 幂等。

### 接口与权限
| 方法与路径 | 权限码 | 说明 |
|---|---|---|
| `GET /crm/return/page`、`/crm/return/{id}` | 1889 `sys:crm:return:list` | 分页/详情 |
| `POST /crm/return`、`PUT /crm/return/{id}`、`POST /crm/return/{id}/submit|retry|cancel|reconcile` | 1890 `sys:crm:return:manage` | 建单/编辑/提交/重试/取消/对账 |
| `POST /crm/internal/return/approval-callback` | 内网 | workflow 回调 |

- 错误码：41069 `RETURN_REQUEST_NOT_FOUND`、41070 `RETURN_STATE_CONFLICT`、41071 `RETURN_QUANTITY_EXCEEDED`、41072 `RETURN_ERP_UNAVAILABLE`、41073 `RETURN_ERP_FAILED`。
- 前端：`pages/return`（列表、按 ERP 订单行退货、提交审批、执行/重试、详情与失败原因）；`App.tsx` 注册菜单 436 退换货。

## Auth：`migration-5.83-crm-m4-service.sql`
- 权限 1881–1890：`ticket:list` / `ticket:create` / `ticket:assign` / `ticket:handle` / `service:sla:manage` / `kb:list` / `kb:manage` / `survey:manage` / `return:list` / `return:manage`。
- 菜单 432 工单、433 SLA 设置、434 知识库、435 满意度、436 退换货（父级 413 CRM）。
- 授权：`tenant_admin`/`crm_admin`/`crm_sales_manager`/`crm_service` 全量；`crm_sales` 工单 list/create/assign/handle + kb list + return list；`crm_analyst` 仅各 list 与 survey:manage 查询；`WHERE NOT EXISTS` 幂等。

## 验证口径（每批）
- 后端：`mvn -o -pl backend/whale-apps/whale-crm test` 全绿；4c 追加 `mvn -o -pl backend/whale-apps/whale-erp test`；Checkstyle 0、ArchUnit（新上下文 `service`）。
- 前端：`pnpm --filter whale-crm lint`（`--max-warnings 0`）/ `build`。
- 迁移：`V10` 空库重放 + 唯一键断言；`V18` 仅 ALTER 不改历史。
- 文档：CRM 全景计划 §14 追加 7f-4 交付段、roadmap/service-catalog/devlog 同步，`check-doc-consistency.ps1` PASS。
- 提交：每批只暂存本批文件，推送 `origin/main`。

## 明确不做 / 后续项
- 换货自动补发、ERP 履约事件订阅、SLA 节假日表、按区域/技能自动分派、匿名 H5 问卷（需网关白名单，受红线约束）、满意度维度自定义、工单附件（先接 `whale-file` 引用字段预留）。
