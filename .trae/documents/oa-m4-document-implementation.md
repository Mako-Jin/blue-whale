# OA M4 公文与档案实施计划（7c）

## Context

OA M0–M3 已全部交付（M3 P2 于 2026-11-07 推送 `ceb20b98`）。按 [OA 全景计划 §7.4](../../docs/plans/2026-11-04-wave5.82-oa-panorama.md) 与 [五服务分步计划 Step 7](../../docs/plans/2026-11-06-five-services-backlog-stepwise.md)，下一里程碑为 **M4 公文与档案**：发文（拟稿→审核→签发→下发→归档）、收文（登记→批阅→转发→办理→归档）、公文模板、文号规则、附件与 Archive 归档映射。本批沿用既有四层结构与审批/序列/文件能力，不新增服务、不新增库。

## 现有可复用能力（已核实）

| 能力 | 位置 | 复用方式 |
|---|---|---|
| 审批登记与结果事件 | `oa.approval`：`ApprovalInstanceAppService.register`、`ApprovalResultSyncedEvent`、`ApprovalBusinessDetailProvider` | 发文提交登记 `DOC_OUTGOING`；监听结果回写状态；审批详情提供者补充字段 |
| 单号序列 | `oa.common`：`SequenceAppService` / `SequenceRepository.nextValue(bizType, period)` | 文号按规则取号（自定义期间与位数，直接复用 `SequenceRepository`） |
| 文件上传/下载 | 前端 `api/file.ts`；后端 `DriveFileGateway` 同款内网客户端模式 | 附件只存 `fileId` 与元数据快照；下载走 whale-file 内网接口 |
| Archive 入档 | `whale-archive`：`POST /archive/internal/items`（`ArchiveIngestRequest`/`ArchiveIngestResultVO`，`(sourceType, sourceId)` 幂等） | 新增 `OA_DOC` 白名单；OA 推送快照并记录 `archiveItemId` |
| 内网调用模板 | `DriveFileGatewayImpl` / `ContractSealGatewayImpl`：`InternalTokenSupport.restClient()` + `DiscoveryClient` | Archive 客户端同款实现 |
| 迁移与权限脚本模板 | `V11__drive.sql`、`migration-5.82-oa-m3p2-directory-workbench.sql` | 幂等 DDL + `NOT EXISTS` 权限/菜单/授权 |
| 前端页面模板 | `pages/collab/directory.tsx`、`api/drive.ts`、`App.tsx` | 查询页 + 弹窗表单 + 权限过滤菜单 |

## 一、数据模型（`V12__document.sql`，6 张表）

| 表 | 关键列 | 约束 |
|---|---|---|
| `oa_doc_template` | code/name/doc_type(OUTGOING,INCOMING)/version/status(DRAFT/RELEASED/DISABLED)/header_text/footer_text/body_template/remark | UK(tenant_id, code, version) |
| `oa_doc_no_rule` | code/name/doc_type/prefix/period_format(YEAR,MONTH,NONE)/serial_length(3–6)/separator/enabled | UK(tenant_id, code) |
| `oa_doc_outgoing` | doc_no/title/doc_type/security_level(1–3)/urgency(1–3)/template_id/template_name/drafter_id/drafter_name/dept_id/main_recipients/copy_recipients/content/signer/sign_date/issued_at/status/approval_instance_id/archive_item_id/remark | UK(tenant_id, doc_no)；IDX(status, create_time) |
| `oa_doc_incoming` | incoming_no/source_unit/source_no/title/security_level/urgency/received_date/due_date/assignee_id/assignee_name/instruction/handled_at/status/archive_item_id/remark | UK(tenant_id, incoming_no)；IDX(status, received_date) |
| `oa_doc_attachment` | doc_type(OUTGOING,INCOMING)/doc_id/file_id/file_name/file_size/content_type/sort_no | IDX(tenant_id, doc_type, doc_id) |
| `oa_doc_archive_ref` | doc_type/doc_id/source_type/source_id/archive_item_id/status(PENDING,ARCHIVED,FAILED)/error_message/pushed_at | UK(tenant_id, doc_type, doc_id) |

种子：默认文号规则 `OUT_DEFAULT`（`BW-OUT-` + 年 + `-`）、`IN_DEFAULT`（`BW-IN-`）；`DOC_OUTGOING` 审批配置（`OA_DOC_OUTGOING`，URL `/oa/document/outgoing`）与 `dept_manager` 审批人规则。

状态机：
- 发文 `DRAFT → PENDING → APPROVED → SIGNED → ISSUED → ARCHIVED`，`REJECTED` 可回退编辑/删除；未 `APPROVED` 不可签发，未 `ISSUED` 不可归档。
- 收文 `REGISTERED → REVIEWED → FORWARDED → COMPLETED → ARCHIVED`。

## 二、后端实现（`com.bluewhale.oa.document`）

```
domain/model       DocTemplate/DocNoRule/DocOutgoing/DocIncoming/DocAttachment/DocArchiveRef/DocStatus/DocQuery
domain/repository  DocumentRepository
domain/gateway     ArchiveGateway
infrastructure/    entity×6 + mapper×6 + DocumentRepositoryImpl + ArchiveGatewayImpl
application/command  DocumentCommands（模板/规则/发文/收文/状态动作）
application/view     DocumentViews（PageResult 行视图 + 详情 + 归档引用）
application/service  DocTemplateAppService、DocNoAppService、DocOutgoingAppService、DocIncomingAppService、DocArchiveAppService + impl×5
application/listener DocumentApprovalResultListener（DOC_OUTGOING）
application/service/impl/DocumentApprovalBusinessDetailProvider
interfaces/controller DocumentTemplateController、DocNoRuleController、DocOutgoingController、DocIncomingController、DocArchiveController
```

要点：
1. **文号**：`DocNoAppService.nextNo(ruleCode)` 用 `SequenceRepository.nextValue("DOC:"+ruleCode, period)`；`period_format` 决定 `yyyy`/`yyyyMM`/空，序号按 `serial_length` 补零（默认 3），格式 `prefix + period + separator + serial`；规则不存在时回落 `BW-DOC-yyyyMM-###`。
2. **发文**：拟稿落 `DRAFT` 并分配文号；更新/删除仅限 `DRAFT`（`REJECTED` 可编辑后重新提交）；提交时状态 `DRAFT→PENDING` 并 `ApprovalInstanceAppService.register(DOC_OUTGOING)`；审核端点 `PENDING→APPROVED/REJECTED`（供无流程环境与管理员复核）；签发 `APPROVED→SIGNED`，下发 `SIGNED→ISSUED`，归档 `ISSUED→ARCHIVED` 且调用 Archive。
3. **收文**：登记分配收文号 `REGISTERED`；批阅记录批示与承办人→`REVIEWED`；转发→`FORWARDED`；办结→`COMPLETED`；归档（仅 `COMPLETED`）→`ARCHIVED`。
4. **归档幂等**：`oa_doc_archive_ref` UK 兜底；已 `ARCHIVED` 且已有 `archive_item_id` 直接返回；Archive 不可用则记录 `FAILED` 并返回 503 `DOC_ARCHIVE_UNAVAILABLE`，可重试；`sourceType=OA_DOC`，`sourceId=OUT-{id}` / `IN-{id}`。
5. **附件**：随发文/收文命令整体替换（`doc_type + doc_id` 全删重建，逻辑删除）；下载端点校验 `sys:oa:document:download` 后经 whale-file 内网取流。
6. **错误码**（追加 `OaErrorCode`）：`DOC_INVALID 40023`、`DOC_NOT_FOUND 40435`、`DOC_STATUS_CONFLICT 40941`、`DOC_NO_RULE_NOT_FOUND 40436`、`DOC_NO_RULE_EXISTS 40942`、`DOC_TEMPLATE_NOT_FOUND 40437`、`DOC_TEMPLATE_EXISTS 40943`、`DOC_FORBIDDEN 40305`、`DOC_ARCHIVE_UNAVAILABLE 50307`、`DOC_FILE_UNAVAILABLE 50308`。
7. **ArchUnit**：新包沿用`interfaces→application→domain→infrastructure`；`document` 已存在 `package-info.java`，补充包说明。

接口（`/oa/document/**`）：
- 模板：`GET /templates/page`、`GET /templates/all`、`POST /templates`、`PUT /templates/{id}`、`DELETE /templates/{id}`
- 规则：`GET /no-rules`、`POST /no-rules`、`PUT /no-rules/{id}`
- 发文：`GET /outgoing/page`、`GET /outgoing/{id}`、`POST /outgoing`、`PUT /outgoing/{id}`、`DELETE /outgoing/{id}`、`POST /outgoing/{id}/submit`、`POST /outgoing/{id}/audit`、`POST /outgoing/{id}/sign`、`POST /outgoing/{id}/issue`、`POST /outgoing/{id}/archive`
- 收文：`GET /incoming/page`、`GET /incoming/{id}`、`POST /incoming`、`PUT /incoming/{id}`、`DELETE /incoming/{id}`、`POST /incoming/{id}/review`、`POST /incoming/{id}/forward`、`POST /incoming/{id}/complete`、`POST /incoming/{id}/archive`
- 附件：`GET /attachments/{docType}/{docId}/{attachmentId}/download`
- 归档：`GET /archive/refs/page`、`POST /archive/refs/{id}/retry`

## 三、Archive 服务改动

- `ArchiveConstants` 增加 `SOURCE_OA_DOC = "OA_DOC"`。
- `ArchiveIngestService.ALLOWED_SOURCE_TYPES` 加入 `SOURCE_OA_DOC`。
- 不新增表/端点，复用 `POST /archive/internal/items`。

## 四、权限与菜单（`migration-5.82-oa-m4-document.sql`）

| ID | 权限码 | 说明 |
|---|---|---|
| 1760 | `sys:oa:document:list` | 发文/收文/归档查询 |
| 1761 | `sys:oa:document:add` | 拟稿/收文登记 |
| 1762 | `sys:oa:document:edit` | 编辑草稿/被驳回单据 |
| 1763 | `sys:oa:document:delete` | 删除草稿 |
| 1764 | `sys:oa:document:submit` | 提交审核 |
| 1765 | `sys:oa:document:audit` | 审核通过/驳回 |
| 1766 | `sys:oa:document:sign` | 签发/下发 |
| 1767 | `sys:oa:document:incoming` | 收文批阅/转发/办结 |
| 1768 | `sys:oa:document:template` | 模板维护 |
| 1769 | `sys:oa:document:archive` | 归档/重试 |
| 1770 | `sys:oa:document:download` | 附件下载 |
| 1771 | `sys:oa:document:no-rule` | 文号规则维护 |

菜单 391 发文管理 `/oa/document/outgoing`、392 收文管理 `/oa/document/incoming`、393 公文模板 `/oa/document/template`、394 公文归档 `/oa/document/archive`。
授权：`tenant_admin/oa_admin/oa_doc_admin(25126)` 全量；`oa_manager` 除 1768/1771；`oa_member` 1760/1761/1762/1764/1767/1770。

## 五、前端实现

- `api/document.ts`：类型（模板/规则/发文/收文/附件/归档引用）与上述接口函数，复用 `http`、`buildQuery`、`downloadBlob`；附件上传复用 `api/file.ts`。
- `pages/document/outgoing.tsx`：筛选（关键字/状态/密级）、分页表格、拟稿/编辑弹窗（模板、标题、主送/抄送、密级、紧急度、正文、附件）、动作按钮（提交/审核/签发/下发/归档/删除）、详情抽屉（字段 + 审批实例 + 归档结果）。
- `pages/document/incoming.tsx`：登记/编辑弹窗、批阅（批示+承办人）、转发、办结、归档、详情抽屉。
- `pages/document/template.tsx`：模板 CRUD 表格（编码/名称/类型/版本/状态/红头/版记/正文占位符）。
- `pages/document/archive.tsx`：归档引用分页 + 状态筛选 + 失败重试 + 已归档条目链接。
- `App.tsx`：新增 4 个菜单项（权限 `sys:oa:document:list` / `template` / `archive`）与 4 条路由。

## 六、测试与验收

1. 新增 `DocumentAppServiceImplTest`（Mockito，参照 `DriveAppServiceImplTest`）：
   - 文号格式（年/月/无期间、补零、默认回落、同规则递增）
   - 发文状态机（仅草稿可编辑、未审核不可签发、未下发不可归档）
   - 提交登记审批实例、结果监听回写 APPROVED/REJECTED
   - 归档幂等（重复归档不重复推送、失败记录 FAILED 可重试）
   - 收文全链路（登记→批阅→转发→办结→归档）
   - 模板/规则 CRUD 与唯一性冲突
2. `OaFlywayMigrationScriptTest` 增加 V12 表集合与关键列/索引断言。
3. Archive 模块测试：白名单新增 `OA_DOC` 的入档用例（若已有 ArchiveIngestServiceTest 则补一条，否则随 OA 客户端测试）。
4. 命令：
   - `mvn -o -pl whale-apps/whale-oa test "-Dcheckstyle.skip=true"`（cwd `backend`）
   - `mvn -o -pl whale-services/whale-archive test "-Dcheckstyle.skip=true"`（改 Archive 时）
   - `pnpm --filter whale-oa lint` / `pnpm --filter whale-oa build`（cwd `frontend`）
   - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-doc-consistency.ps1`

## 七、文档与交付

- 回填 `docs/plans/2026-11-04-wave5.82-oa-panorama.md`（状态头部 + 变更记录 v0.6：M4 交付、`V12__document.sql`、权限 1760–1771、菜单 391–394）。
- 回填 `docs/plans/2026-11-06-five-services-backlog-stepwise.md`（Step 7 增加 7c 已完成）。
- 回填 `docs/service-catalog.md`（whale-oa 新增 V12 6 表、端点、权限/菜单、前端 4 页）。
- 追加 `docs/devlog/2026-11-07.md`。
- 精确暂存本批文件并提交推送 `origin/main`；**不提交** `.trae/documents/**` 等红线文件。

## 八、开工前需核实的两个签名

- `SequenceRepository` 的取号方法名与参数（计划按 `nextValue(bizType, period)` 使用；若实际为 `next`/`nextVal` 则在实现首步对齐）。
- `ArchiveConstants` 的常量命名（计划按 `SOURCE_OA_DOC` 追加，并同步 `ArchiveIngestService` 白名单）。

## 九、红线

- 只追加迁移，不改历史；脚本无 `DROP/TRUNCATE/DELETE`。
- 不跨库、不跨服务 JOIN；Archive/File 只走内网接口。
- 不提交：`.trae/documents/**`、`docs/reviews/2026-11-07-workspace-scan.md`、`backend/whale-apps/whale-mall/**`、`frontend/platform-apps/whale-mall/**`、whale-pay/5.89 邮箱/5.91 地图未提交文件。
