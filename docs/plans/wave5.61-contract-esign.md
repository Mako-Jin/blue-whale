# Wave 5.61 合同管理与电子签章 —— 设计稿（M1/M2/M3 已实现）

> 状态：**M1 已实现（2026-10-26）；M2 已实现（2026-10-26，印章/授权/本地盖章/签名/证据链/验签/作废）；M3 已实现（2026-10-26，第三方签章适配/回调验签与幂等/流程推进/生效归档）**
> 来源：用户提问「合同管理和电子签章，电子签章流程是不是也可以兼容」（2026-10-26）
> 需求登记：REQ-FUNC-020 / REQ-FUNC-021 / REQ-ARCH-009 / REQ-SEC-010（见 [requirements/register.md](../requirements/register.md)）
> 结论：**流程编排层可完全复用现有工作流引擎；印章、PDF 盖章、数字签名与证据链需新建**。

---

## 一、目标与非目标

### 1.1 目标

1. 合同全生命周期：起草 → 审批 → 签署 → 生效 → 归档，支持版本与多方相对方。
2. 审批与签署**复用现有工作流引擎**（用户任务 / 会签 / 排他网关 / 并行网关 / BPMN 导入导出）。
3. 电子签章能力：印章管理、用章授权、PDF 盖章（含骑缝）、数字签名、验签、证据留存。
4. 对接第三方电子签平台（e签宝 / 法大大 / 上上签），平台侧只做编排与证据管理。
5. 合同签署完成后可按既有约定进入 `whale-archive` 归档。

### 1.2 非目标（本波不做）

- 不自建 CA（根证书 / 签发服务）；自建签名仅用于 PoC，不具备《电子签名法》可靠电子签名效力。
- 不做合同条款智能审查 / 大模型比对（后续可接 `whale-agent`）。
- 不做计费、开票、应收应付（属 ERP / 财务域）。
- 不做可视化审批矩阵配置（先由流程变量 + 条件网关表达金额分级）。

---

## 二、兼容性评估（结论先行）

| 合同/签章诉求 | 复用现有实现 | 兼容方式 | 缺口 |
|---|---|---|---|
| 合同审批（金额分级、法务复核） | `EXCLUSIVE_GATEWAY` + `ConditionGuard` | 流程变量 `amount/category` 走 SpEL 条件 | 无 |
| 顺序签署 | 多个 `USER_TASK` | 每签署人一个节点 | 无 |
| 并行签署（甲方/乙方同时） | `PARALLEL_GATEWAY` fork/join | 两个分支各一 `USER_TASK` | 无 |
| 多人会签 | `COUNTER_SIGN` + `ALL`/`ANY`/`RATIO:n` | assignees = 签署人 | 无 |
| 加签 / 转办 / 委派 / 回退 | `POST /workflow/task/{id}/add-sign`、`/transfer`、`/delegate`、`/rollback` | 合同侧透传 | 无 |
| 合同要素表单 | 表单引擎 + `FormRenderer` | 合同字段模板化 | 需新增合同表单模板 |
| 正文与签章件存储 | `whale-file`（`/file/upload`、`/file/{id}/download`、租户前缀） | 只存 `file_id` 引用 | 需 `file_info.status=2` 锁定（已有能力） |
| 权限 / 菜单 / 审计 / 待办 | `sys:contract:*` + `@AuditLog` + `api_log` | 与 ERP/MES/PDM 同范式 | 需新增权限点与菜单 |
| 审批结果回调 | `GET /workflow/internal/instance/{id}/detail`、`/approved` | 合同侧读实例详情推进状态 | 需新增**系统完成签署任务**的内部接口（见 §5.3） |
| 印章 / 用章授权 | 无 | — | 新建 |
| PDF 盖章 / 数字签名 / 验签 | 无 | — | 新建（PDFBox + BouncyCastle 或第三方） |
| 时间戳 / 存证 / 证据链 | 无 | — | 新建 |

**复用度估算**：编排层 ≈ 90%（节点、待办、轨迹、会签、加签、归档触发均已具备）；签章能力层 ≈ 0%，是本波主要工作量。

---

## 三、服务与模块落位

| 项 | 取值 | 说明 |
|---|---|---|
| 后端模块 | `whale-backend/whale-apps/whale-contract` | 与 erp/mes/pdm/oa/pm 同级业务应用；不塞进 OA，合同被 OA/ERP/PM 共同引用 |
| 端口 | **9006** | 9001–9005 已占用 |
| 库 | **`bluewhale_contract`** | 9 张表，见 §4 |
| 包 | `com.bluewhale.contract` | 分包 `entity/mapper/dto/vo/service/impl/controller/esign` |
| 对外前缀 | **`/contract/**`** | 网关 `lb://whale-contract`，按 REQ-ARCH-003 不剥离前缀 |
| 内网前缀 | `/contract/internal/**` | 网关 `internal-deny` 屏蔽 |
| 前端 | `whale-frontend/platform-apps/whale-contract` | **8109**，qiankun 子应用，base `/contract` |
| 权限点 | **215–233** | ERP 203–206 / MES 207–210 / PDM 211–214 之后 |
| 菜单 | **28–31** | 合同管理 / 印章管理 / 签署中心 / 证据中心 |

---

## 四、领域模型与表结构（DDL 草案）

公共字段沿用平台约定：`id BIGINT`（雪花）、`tenant_id`、`create_time`、`update_time`、`deleted`；业务唯一键一律 `(tenant_id, xxx)`；表间逻辑外键。

| # | 表 | 关键字段 | 阶段 |
|---|---|---|---|
| 1 | `ct_contract` | `code`(uk)、`title`、`category`、`amount`、`currency`、`owner_id`、`dept_id`、`status`、`current_version`、`file_id`、`effective_date`、`expire_date`、`archive_item_id` | M1 |
| 2 | `ct_contract_party` | `contract_id`、`party_type`(A/B/OTHER)、`name`、`credit_code`、`contact`、`phone`、`signer_user_id`/`signer_name`（外部签署方留空） | M1 |
| 3 | `ct_contract_version` | `contract_id`、`version_no`、`file_id`、`sha256`、`change_note`、`status`(DRAFT/SUPERSEDED/EFFECTIVE) | M1 |
| 4 | `ct_contract_flow` | `contract_id`、`version_id`、`process_instance_id`、`definition_id`、`stage`(APPROVAL/SIGN)、`status` | M1 |
| 5 | `ct_seal` | `name`、`seal_type`(COMPANY/CONTRACT/LEGAL/FINANCE)、`image_file_id`、`keeper_user_id`、`valid_from/to`、`status` | M2 |
| 6 | `ct_seal_grant` | `seal_id`、`grantee_user_id`、`contract_category`、`max_amount`、`valid_from/to`、`status`、`granted_by` | M2 |
| 7 | `ct_sign_task` | `contract_id`、`version_id`、`seq_no`、`signer_user_id`、`signer_party`、`provider`、`provider_task_id`、`status`(PENDING/SIGNING/SIGNED/REFUSED/EXPIRED)、`signed_at`、`signed_file_id`、`refuse_reason` | M2（第三方字段 M3 启用） |
| 8 | `ct_sign_record` | `sign_task_id`、`signer_user_id`、`seal_id`、`cert_serial`、`signed_at`、`original_sha256`、`signed_sha256`、`algorithm`、`evidence_no`、`chain_hash`、`client_ip` | M2 |
| 9 | `ct_esign_callback` | `provider`、`provider_event_id`(uk)、`contract_id`、`payload_hash`、`payload`、`status`(RECEIVED/PROCESSED/FAILED)、`retry_count` | M3 |

索引建议：`ct_contract` 建 `(tenant_id, status, expire_date)`；`ct_sign_task` 建 `(contract_id, seq_no)`、`(provider, provider_task_id)`；`ct_sign_record` 建 `(contract_id, signed_at)`、`(evidence_no)`。

---

## 五、状态机与流程兼容设计

### 5.1 合同状态机

```
DRAFT ──提交──► APPROVING ──审批通过──► APPROVED ──发起签署──► SIGNING ──全部签署──► SIGNED ──生效──► EFFECTIVE ──到期/手动──► ARCHIVED
   ▲               │                                                                 
   │               └──驳回──► REJECTED ──修订重提──► DRAFT（version+1）
   └──────────────────────撤回 WITHDRAWN（仅 APPROVING）
任意可终止：TERMINATED / VOID（需权限 + 审计，已签署件保留证据）
```

- 状态由**流程事件驱动**，不允许前端直接改 `status`。
- 每次修订生成 `ct_contract_version` 新行，`version_no+1`；旧版本置 `SUPERSEDED`。
- 重签：作废未完成 `ct_sign_task`，基于新版本新建流程实例（`businessKey` 带版本号）。

### 5.2 流程模板（JSON 工作流模型，节选）

审批阶段与签署阶段可放在**同一条流程**中（推荐），也可拆两条流程（`stage=APPROVAL` / `stage=SIGN`）。推荐同一条，减少一次实例与回调。

```json
{
  "type": "stateMachine",
  "nodes": [
    { "key": "start",   "type": "START_EVENT" },
    { "key": "dept",    "type": "USER_TASK", "name": "部门负责人", "assignee": "${deptLeader}" },
    { "key": "amount",  "type": "EXCLUSIVE_GATEWAY", "name": "金额分级" },
    { "key": "legal",   "type": "USER_TASK", "name": "法务审核", "assignee": "${legal}" },
    { "key": "finance", "type": "USER_TASK", "name": "财务审核", "assignee": "${finance}" },
    { "key": "sealUse", "type": "USER_TASK", "name": "用章审批", "assignee": "${sealKeeper}" },
    { "key": "signA",   "type": "USER_TASK", "name": "甲方签署", "assignee": "${partyASigner}" },
    { "key": "signB",   "type": "PARALLEL_GATEWAY", "name": "双方并签" },
    { "key": "signB1",  "type": "USER_TASK", "name": "乙方签署", "assignee": "${partyBSigner}" },
    { "key": "signB2",  "type": "USER_TASK", "name": "见证签署", "assignee": "${witness}" },
    { "key": "end",     "type": "END_EVENT" }
  ],
  "flows": [
    { "from": "start", "to": "dept" },
    { "from": "dept", "to": "amount" },
    { "from": "amount", "to": "legal", "condition": "#amount > 100000", "isDefault": true },
    { "from": "amount", "to": "sealUse", "condition": "#amount <= 100000" },
    { "from": "legal", "to": "finance" },
    { "from": "finance", "to": "sealUse" },
    { "from": "sealUse", "to": "signA" },
    { "from": "signA", "to": "signB" },
    { "from": "signB", "to": "signB1" },
    { "from": "signB", "to": "signB2" },
    { "from": "signB1", "to": "end" },
    { "from": "signB2", "to": "end" }
  ]
}
```

> **引擎字段约束（M1 实机校验修正）**：`type` 仅接受 `stateMachine`（其它值报 `UNKNOWN_DEFINITION_TYPE`）；
> 节点主键字段为 `key`（不是 `id`），普通 `USER_TASK` 用单值 `assignee`（必填），
> `COUNTER_SIGN` 才使用 `assignees` 数组 + `counterSignPolicy`；流转键可选。

- 变量：`contractId`、`version`、`amount`、`category`、`partyType`、签署人账号；由合同服务启动实例时注入。
- `businessKey` 约定：`contract:{contractId}:v{version}`，用于合同侧反查与归档关联。
- 会签替代方案：把 `signA/signB1/signB2` 合并为一个 `COUNTER_SIGN` 节点，`counterSignPolicy=ALL`（全员签完）或 `RATIO:n`（N 人签完）。

### 5.3 关键补充：外部签章回调如何推进流程

现状：引擎只有 `START_EVENT / END_EVENT / USER_TASK / EXCLUSIVE_GATEWAY / PARALLEL_GATEWAY / COUNTER_SIGN` 六类节点，**没有服务任务或消息事件**；`/workflow/task/{id}/approve` 需要签署人身份与 `sys:workflow:task:approve` 权限。

第三方签章是**异步**的（用户在第三方 H5/小程序完成签署），回调到达时引擎不能自己前进。三个方案：

| 方案 | 做法 | 评价 |
|---|---|---|
| A 代签完成 | 合同服务用签署人令牌/系统账号调 `/workflow/task/{id}/approve` | 需保管用户凭据、审计责任人错位，**不推荐** |
| B 内部完成接口（**推荐**） | 新增 `POST /workflow/internal/task/{id}/complete`：`{operatorId, action(APPROVE/REJECT), comment, variables}`，内部鉴权（网关 `internal-deny` + 服务间 token），并在 `process_task` 记录 `operator_type=SYSTEM` 与回调来源 | 引擎改动小（1 控制器 + 1 命令类 + 单测），语义清晰、审计可追 |
| C 新增节点类型 | 引入 `SERVICE_TASK` / `MESSAGE_EVENT`，由事件总线唤醒 | 改动面大（解析器/校验/状态机/设计器/BPMN 映射），**本波不做** |

方案 B 同时覆盖：第三方回调、定时催办、系统自动通过（如小额合同）。需在 `bluewhale_workflow` 追加幂等键（`taskId + action + eventId`），防止回调重放重复推进。

### 5.4 与归档的衔接

合同 `SIGNED → EFFECTIVE` 后：
- 若走本平台审批流：归档服务按既有「审批通过实例」同步规则自动可见（`REQ-FUNC-014`）；
- 若为外部签署（无本平台审批轨迹）：合同服务调 `whale-archive` 的内部归档接口，附签署轨迹快照与证据摘要；归档条目 `source=CONTRACT`。

---

## 六、电子签章能力层

### 6.1 印章管理（M2）

- 印章资产：图片存 `whale-file`（`file_info.status` 由用章策略锁定），元数据落 `ct_seal`。
- 用章授权 `ct_seal_grant`：按人 + 合同类别 + 金额上限 + 有效期授权；**无授权不可用章**。
- 用章审批：`USER_TASK`（印章保管人），与流程模板 `sealUse` 节点对应。
- 预览统一加半透明水印 + 低分辨率，原图需 `sys:contract:seal:download` 权限并留审计。

### 6.2 PDF 盖章与数字签名

分两层，可分别启用：

| 层 | 实现 | 说明 |
|---|---|---|
| 视觉盖章 | Apache PDFBox 叠加印章/签名图片 | 坐标、页码、缩放、旋转；支持骑缝章（按页数均分切图） |
| 数字签名 | BouncyCastle CMS/PAdES（`adbe.pkcs7.detached`） | 需 PKCS#12 证书；国密 SM2 需 BouncyCastle GM 提供者（后续评估） |

签署顺序：`原始 PDF` →（追加视觉印章）→（追加数字签名）→ `signed_file_id` 入库并锁定；`ct_sign_record` 记录 `original_sha256` / `signed_sha256`。

验签：`POST /contract/{id}/verify` 校验 ① PDF 内签名有效性（证书链、内容未被修改）② 数据库 `signed_sha256` 与文件实际哈希一致 ③ `chain_hash` 连续。

### 6.3 第三方平台适配（M3）

```java
/** 电子签平台适配接口：本地 PoC 与三方实现同契约。 */
public interface EsignProvider {
    String name();                                             // local / esignbao / fadada / ssq
    CreateTaskResult createTask(SignTaskCommand command);      // 创建签署任务，返回 providerTaskId + 签署链接
    TaskStatusResult queryTask(String providerTaskId);         // 轮询兜底
    byte[] downloadSignedFile(String providerTaskId);          // 下载签章件
    boolean verifyCallback(String rawBody, Map<String,String> headers); // 回调验签（RSA/HMAC + 时间戳）
}
```

- 选择：`whale.contract.esign.provider=local|esignbao|fadada|ssq`，默认 `local`（PoC 自签，禁止生产）。
- 回调：`POST /contract/sign/callback/{provider}`（外网可达，**仅此路径**放行），验签 + 时间戳 ±300s + `ct_esign_callback` 幂等（`provider_event_id` 唯一键）→ 落 `ct_sign_task` → 按 §5.3 方案 B 完成引擎任务。
- 外部签署方：短信/刷脸实名 + H5 签署页由三方提供，平台只保存 `evidence_no` 与签署结果。

### 6.4 证据链

每条签署记录计算 `chain_hash = sha256(prev_chain_hash || contractId || versionId || signerId || signedAt || original_sha256 || signed_sha256 || evidence_no)`，合同维度线性串联；证据快照（实例轨迹 + 表单值 + 签署记录 + 文件哈希）可在归档时导出为 JSON，复用档案服务的快照机制。

---

## 七、接口与权限设计

### 7.1 合同主流程（M1）

| 方法 | 路径 | 权限点 | 说明 |
|---|---|---|---|
| GET | `/contract/page` | `sys:contract:list`(215) | 分页（编号/标题/类别/状态/金额区间/到期提醒） |
| GET | `/contract/{id}` | `sys:contract:list` | 详情（含相对方、版本、流程状态） |
| POST | `/contract` | `sys:contract:add`(216) | 新建（草稿） |
| PUT | `/contract/{id}` | `sys:contract:edit`(217) | 编辑（仅 DRAFT） |
| DELETE | `/contract/{id}` | `sys:contract:delete`(218) | 逻辑删除（仅 DRAFT） |
| POST | `/contract/{id}/submit` | `sys:contract:submit`(219) | 提交审批（启动流程实例） |
| POST | `/contract/{id}/withdraw` | `sys:contract:withdraw`(220) | 撤回（APPROVING） |
| POST | `/contract/{id}/terminate` | `sys:contract:terminate`(221) | 终止 |
| POST | `/contract/{id}/archive` | `sys:contract:archive`(222) | 归档（EFFECTIVE） |
| GET | `/contract/{id}/versions` | `sys:contract:list` | 版本列表 |
| POST | `/contract/{id}/versions` | `sys:contract:edit` | 基于当前版本发起修订（version+1） |

### 7.2 印章 / 签署 / 证据（M2–M3）

| 方法 | 路径 | 权限点 | 说明 |
|---|---|---|---|
| GET/POST/PUT/DELETE | `/contract/seal/**` | `sys:contract:seal:list/add/edit/delete`(223–226) | 印章管理 |
| POST | `/contract/seal/{id}/grant` | `sys:contract:seal:grant`(227) | 用章授权 |
| POST | `/contract/{id}/sign/start` | `sys:contract:sign:start`(228) | 发起签署（本地/三方） |
| POST | `/contract/sign-task/{id}/sign` | `sys:contract:sign:execute`(229) | 本地签署（提交坐标+印章） |
| POST | `/contract/sign-task/{id}/refuse` | `sys:contract:sign:execute` | 拒签（必填原因） |
| POST | `/contract/sign/callback/{provider}` | 无（验签放行） | 三方回调 |
| GET | `/contract/{id}/evidence` | `sys:contract:evidence:read`(230) | 证据链查询 |
| POST | `/contract/{id}/verify` | `sys:contract:evidence:read` | 验签 |
| POST | `/contract/{id}/void` | `sys:contract:sign:void`(231) | 作废（保留证据） |
| GET | `/contract/internal/expiring` | 内网 | 到期提醒（任务调度调用） |

菜单：28 合同管理 `/contract`、29 印章管理 `/contract/seal`、30 签署中心 `/contract/sign`、31 证据中心 `/contract/evidence`（component 键 `contract/index` 等）。

---

## 八、前端设计（whale-contract，8109）

| 页面 | 关键交互 |
|---|---|
| 合同列表 | ProTable + 状态筛选 + 到期提醒标签 + 行内「提交/撤回/归档」 |
| 合同编辑 | 要素表单（FormRenderer）+ 正文上传（`/file/upload`）+ 相对方子表 |
| 审批详情 | 流程轨迹（复用既有实例详情）+ 表单快照 + 附件只读下载 |
| 签署页 | pdf.js 预览 + 拖拽签署位置（印章/签名）+ 骑缝章开关 + 确认签署 |
| 印章管理 | 列表 + 上传印章图 + 授权弹窗（人/类别/金额上限/有效期） |
| 证据中心 | 时间线 + `chain_hash` 连续性标识 + 一键验签结果 |

工程约定：`storage` 前缀 `whale_ct_`、`Id = string` 禁 `Number(id)`、请求走 `@bluewhale/http`、基座注册 qiankun 子应用与 `/contract` 旁路（参考 `/im` 的 `Accept: text/html` 处理）。

---

## 九、安全与合规

1. **法律效力**：《电子签名法》要求专有性、可控性、可检测篡改；自签证书仅 PoC。生产必须接持牌 CA + TSA（或三方平台），并在合同上体现「可靠电子签名」。
2. **印章安全**：仅授权人可用；原图下载单独权限 + 审计；前端预览加水印；禁止通过 `/file/{id}/download` 绕过（需在服务层校验文件归属与用途）。
3. **回调安全**：仅 `/contract/sign/callback/*` 外网放行；验签 + 时间戳窗口 + `provider_event_id` 幂等；失败落 `FAILED` 可重放。
4. **内网隔离**：`/contract/internal/**` 与新增 `/workflow/internal/task/{id}/complete` 必须在网关 `internal-deny` 名单内。
5. **租户隔离**：9 张表全部 `tenant_id` + 租户行插件；跨租户详情按 404/403 语义。
6. **审计**：合同提交/撤回/终止/归档、印章新增授权、每一次签署/拒签/作废全部 `@AuditLog`；金额等敏感字段走既有脱敏。
7. **数据留存**：签章件与证据不可物理删除（逻辑删除仅对草稿），归档件走 `file_info.status=2` 锁定。

---

## 十、分期与验收

### M1 合同主流程（约 1 波）—— **已完成（2026-10-26）**

- 交付：`whale-contract` 骨架（pom/yml/启动类）+ 4 表 DDL + 合同 CRUD/提交/撤回/终止/归档 + 流程启动与实例状态同步 + 文件引用 + 权限 215–222 + 菜单 28 + 网关路由 + 前端列表/编辑/审批详情。
- 验收：① `mvn package` 通过且 9006 healthy；② 提交后生成流程实例，`businessKey` 正确；③ 审批全部通过后合同转 `APPROVED`，驳回转 `REJECTED` 且可修订重提；④ 网关 CRUD 与状态流转冒烟全 `code=0`；⑤ 前端 `tsc` + `build` 通过；⑥ 跨租户不可见。
- 落地记录：后端 [whale-contract](../../whale-backend/whale-apps/whale-contract)、权限迁移 [migration-5.61-contract.sql](../../whale-backend/whale-services/whale-auth/src/main/resources/db/migration-5.61-contract.sql)、网关 `/contract/**`、前端 [whale-contract](../../whale-frontend/platform-apps/whale-contract)（8109）、冒烟 [bw-contract-m1-smoke.ps1](../../scripts/smoke/bw-contract-m1-smoke.ps1)；验收报告见 [2026-10-26-p3-wave5.61-contract-m1.md](../reviews/2026-10-26-p3-wave5.61-contract-m1.md)。
- M1 简化：列表用 antd `Table`（未引入 ProTable），编辑用弹窗表单（未接 `FormRenderer`），正文文件仅存 `file_id` 引用（上传 UI 留待 M2 随签署页一并接入）；签署阶段状态（`SIGNING/SIGNED/EFFECTIVE`）由 M2 驱动。

### M2 印章与本地盖章（约 1 波）

- 交付：`ct_seal`/`ct_seal_grant`/`ct_sign_task`/`ct_sign_record` 4 表 + 印章/授权接口 + PDFBox 视觉盖章 + BouncyCastle 数字签名 + 验签 + 签署页 + 权限 223–227/229–231 + 菜单 29–30。
- 验收：① 未授权用章被拒（403 + 审计）；② 签章后 PDF 哈希与 `signed_sha256` 一致，篡改后验签失败；③ 骑缝章页数与配置一致；④ 并发签署幂等（同一任务重复提交只产生 1 条记录）；⑤ 拒签记录原因并使合同停在 `SIGNING`。

### M3 第三方签章与证据链（约 1 波）

- [x] 交付：`EsignProvider` 三实现 + 回调接口 + `ct_esign_callback` + 证据链 + `/workflow/internal/task/{id}/complete` + 归档衔接 + 菜单 31。
- [x] 验收：① 回调重放 10 次仅推进一次；② 验签非法/超时窗口一律拒绝并落审计；③ 证据链中断可被 `verify` 检出；④ 签署完成后归档可见（若启用归档）；⑤ provider 切换只改配置不改代码。
- 落地记录：provider 适配层 [`esign/provider`](../../whale-backend/whale-apps/whale-contract/src/main/java/com/bluewhale/contract/esign/provider)、回调 [`EsignCallbackServiceImpl`](../../whale-backend/whale-apps/whale-contract/src/main/java/com/bluewhale/contract/service/impl/EsignCallbackServiceImpl.java)、workflow 内部接口 [`WorkflowInternalTaskController`](../../whale-backend/whale-services/whale-workflow/src/main/java/com/bluewhale/workflow/controller/WorkflowInternalTaskController.java)、迁移 `migration-5.63-contract-esign-callback.sql` / `migration-5.63-workflow-task-operator.sql` / `migration-5.63-contract-esign-m3.sql`、前端证据中心 `EvidenceCenter.tsx`、冒烟 [bw-contract-m3-smoke.ps1](../../scripts/smoke/bw-contract-m3-smoke.ps1)；验收报告见 [2026-10-26-p3-wave5.61-contract-m3.md](../reviews/2026-10-26-p3-wave5.61-contract-m3.md)。
- M3 简化（后续迭代）：2026-10-27 已补三方开放平台 HTTP 接入（创建/查询/下载 + 请求签名 + 合同快照上传），替换原 HMAC 骨架；仍待接实名意愿认证与持牌 CA/TSA，供应商直连协议差异需按真实 API 文档配置路径/签名或经适配网关。

### M3.5 回调乱序补偿（2026-10-26）—— **已完成**

- [x] 交付：`EsignCallbackSigner`（统一验签/补偿签名口径）+ `SignTaskReconcileJob`（超时 `SIGNING` 非 local 任务定时 `queryTask`，终态时自签内部回调交既有 `EsignCallbackService`）+ `whale.contract.esign.reconcile.{enabled,interval-ms,initial-delay-ms,stale-seconds,batch-size}` 配置（环境变量 `CONTRACT_ESIGN_RECONCILE_*` 可覆盖）。
- [x] 验收：① `package` 通过且 Checkstyle 0；② 单测 4/4（终态映射 / 非终态不推进 / 拒签带原因 / 缺密钥跳过）；③ 实机三类任务（过期三方 / LOCAL / 未超时）仅过期三方被轮询（当时骨架渠道恒 `SIGNING`，任务不被误推进）；④ 幂等键 `RECONCILE-<taskId>-<action>`，同任务重复补偿不重复推进。
- 落地记录：补丁位于 [`esign/reconcile`](../../whale-backend/whale-apps/whale-contract/src/main/java/com/bluewhale/contract/esign/reconcile) 与 [`EsignCallbackSigner`](../../whale-backend/whale-apps/whale-contract/src/main/java/com/bluewhale/contract/esign/provider/EsignCallbackSigner.java)；验收报告见 [2026-10-26-p3-esign-callback-reconcile.md](../reviews/2026-10-26-p3-esign-callback-reconcile.md)。
- 已补（2026-10-27）：三方 `queryTask` 改为调用开放平台并解析真实状态；真实供应商账号联调仍待凭证与 API 文档；本地/CI 通过 Mock HTTP 单测覆盖创建/查询/下载/错误响应。

### M3.6 归档快照衔接（2026-10-26）—— **已完成**

- [x] 交付：archive 新增 `POST /archive/internal/items`（复用 `ArchiveItemStore` + `archive_source_record` 台账，上传快照到 `whale-archive` 桶并计算 SHA-256，条目落「待整理」）；contract 归档时推送合同快照（核心字段 + 相对方清单 + 原件引用）并回填 `ct_contract.archive_item_id`，配置 `whale.contract.archive.*`（`CONTRACT_ARCHIVE_URL` 等可覆盖）。
- [x] 验收：① `package` 通过且 Checkstyle 0，contract 单测 7/7；② M3 冒烟 00–27 全通过（含 `25.effective`/`26.archive`）；③ 实机 `archive_item`（`source_type=CONTRACT`、`item_status=0`、`snapshot_hash` 可回读复算一致）+ `archive_file`（`SNAPSHOT` + `ORIGINAL`）+ `archive_source_record(sync_status=1)` 齐备；④ 同 `(sourceType, sourceId)` 重复推送返回既有条目 `created=false`；⑤ `/archive/item/page` 可见该合同档案。
- 落地记录：[`ArchiveIngestService`](../../whale-backend/whale-services/whale-archive/src/main/java/com/bluewhale/archive/service/ArchiveIngestService.java)、[`ArchiveInternalClient`](../../whale-backend/whale-apps/whale-contract/src/main/java/com/bluewhale/contract/client/ArchiveInternalClient.java)；验收报告见 [2026-10-26-p3-contract-archive-snapshot.md](../reviews/2026-10-26-p3-contract-archive-snapshot.md)。
- 遗留：条目停在「待整理」需档案人员确认归档分配档号；归档失败为同步可重试语义，无异步补偿队列。
- 已补（2026-10-27）：原件/快照入档锁定已实现——`ArchiveIngestService` 新入档先锁定快照与原件再落库，重复推送补锁既有条目，`ArchiveItemService#confirm` 确认归档时兜底锁定全部文件；新增内网 `POST /archive/internal/items/lock-reconcile` 供运维按租户游标补扫历史条目，并提供默认关闭的 `ArchiveFileLockReconcileScheduler`（`whale.archive.lock-reconcile.enabled=true` 时按 cron 逐租户补扫，单轮有批次上限）。边界：历史已归档条目默认不自动补锁，需运维手工触发或显式开启调度；文件服务不可用时按 fail-closed 处理。详见 [P1 合同归档原件锁定检查报告](../reviews/2026-10-27-p1-contract-archive-original-lock.md)。



---

## 十一、风险与开放问题（待评审确认）

| # | 风险/问题 | 处置建议 |
|---|---|---|
| R1 | 可靠电子签名合规 | 生产必须接持牌 CA/TSA 或三方平台；自建仅 PoC |
| R2 | 引擎无异步事件节点 | 采用 §5.3 方案 B 内部完成接口，改动小且可审计 |
| R3 | 外部签署方实名与意愿认证 | 交由三方平台；平台侧只存证据编号 |
| R4 | 印章图片泄露 | 预览水印 + 原图权限 + 全部用章留痕 |
| R5 | 回调乱序/重复 | 幂等表 + 状态机单向流转 + 定时 `queryTask` 兜底（**已落地**：`SignTaskReconcileJob`，见 M3.5） |
| R6 | 合同金额分级规则频繁变化 | 先用流程变量 + 条件网关；若需运营配置再评估规则表 |
| Q1 | 是否需要骑缝章、多页跨页章？ | 建议 M2 支持骑缝，跨页签章按页坐标 |
| Q2 | 是否需要外部相对方在平台内注册？ | 建议不注册，走三方 H5；平台仅存签署人姓名/手机号（脱敏） |
| Q3 | 合同编号规则（按类别/年份流水）？ | 建议 `HT-{类别码}-{yyyy}-{6位流水}`，与 OA 单号风格一致 |
| Q4 | 是否复用 PM/ERP 的合同引用关系？ | 先提供 `bizType+bizId` 弱关联字段，后续按需建关联表 |

---

## 十二、变更记录

| 日期 | 版本 | 说明 |
|---|---|---|
| 2026-10-26 | v0.1 | 初稿：兼容性评估、9 表模型、流程模板与异步回调方案、分期与验收；待评审后进入 M1 |
| 2026-10-26 | v1.0 | M1 落地：whale-contract 9006 + 4 表 + `/contract/**` + 权限 215–222 + 菜单 28 + 前端 8109；修正流程模板字段（`type=stateMachine`、节点 `key`、`USER_TASK.assignee`） |
| 2026-10-26 | v1.1 | M2 落地：印章/授权/签署任务/签章记录 4 表 + PDFBox 骑缝盖章 + PKCS#7 签名验签 + 链式哈希证据链 + 权限 223–231 + 菜单 29/30 + 前端印章管理与签署中心 |
| 2026-10-26 | v1.2 | M3 落地：`EsignProvider` 适配层与配置切换 + `ct_esign_callback` 幂等 + HMAC 验签/时间窗 + workflow 内部完成接口 + 生效接口（权限 232）+ 菜单 31 证据中心 + 前端证据中心页；Wave 5.61 三阶段闭环 |
