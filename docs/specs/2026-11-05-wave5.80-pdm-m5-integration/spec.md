# Wave 5.80 PDM M5：集成收口与对账 规格

> 状态：已实现（2026-11-05）
> 关联计划：[Wave 5.80 PDM 全景计划 §十一](../../plans/2026-11-04-wave5.80-pdm-panorama.md)
> 关联需求：REQ-FUNC-055、REQ-FUNC-056、REQ-ARCH-020、REQ-SEC-030
> 关联服务：[服务清单](../../service-catalog.md)

## 一、目标

把 CAD、ERP、MES、OA 四条外部链路的契约、幂等、失败重试与对账收口到一个可运维的闭环：

1. 外部 CAD 工具可按契约检入图纸、导入装配 EBOM，重复提交不产生重复版本或 BOM。
2. PDM 零件可按 `pdmPartNo` 幂等推送到 ERP，重复同步只更新、不重建。
3. ERP 拉取 BOM、MES 路线下发、OA 审批回调的状态可对账，差异可见并可补偿。
4. 运维人员在前端可查看同步记录、失败原因并自助重试或重推。
5. 内网端点不得被外部访问。

## 二、约束

| 编号 | 约束 |
|---|---|
| C-1 | 不新增服务、不新增数据库；集成能力留在 `whale-pdm` 的 `integration` 上下文 |
| C-2 | 文件字节仍归 `whale-file`；PDM 只存 `fileId` 与元数据 |
| C-3 | ERP 物料主数据归 ERP，PDM 只推送与记录，不读写 ERP 业务表 |
| C-4 | MES 路线执行归 MES，PDM 只记录下发状态与版本号 |
| C-5 | OA 仍是审批引擎；PDM 只保存审批状态快照，补偿按快照回写并审计 |
| C-6 | 对账只读本地发布记录与同步日志，不直接改写外部系统数据 |
| C-7 | 迁移只允许 `CREATE TABLE IF NOT EXISTS`，禁止 `DROP/TRUNCATE/DELETE` |
| C-8 | 内网端点 `/pdm/internal/**` 与 `/erp/internal/**` 必须被网关 internal-deny |

## 三、数据模型

### 3.1 `pdm_int_sync_log`

| 字段 | 说明 |
|---|---|
| `channel` | CAD / ERP / MES / OA |
| `action` | CAD_CHECKIN / CAD_EBOM_IMPORT / PART_UPSERT / BOM_PULL / BOM_NOTIFY / ROUTE_PUSH / OA_CALLBACK / OA_COMPENSATE |
| `direction` | OUTBOUND / INBOUND |
| `biz_type` | PART / DOCUMENT / BOM / ROUTE / CHANGE |
| `biz_id` / `biz_no` / `version_no` | 业务主键与快照 |
| `idempotency_key` | 幂等键，`(tenant_id, channel, idempotency_key)` 唯一 |
| `status` | SUCCESS / FAILED |
| `retry_count` / `last_error` | 失败可见、可重试 |
| `payload` / `checksum` / `external_ref` | 请求快照、内容校验和、外部引用 |
| `operator_id` / `requested_at` / `finished_at` | 审计信息 |

### 3.2 `pdm_int_reconcile`

| 字段 | 说明 |
|---|---|
| `channel` / `biz_type` / `biz_no` / `biz_id` / `version_no` | 对账对象 |
| `local_status` / `remote_status` | 本地与外部状态 |
| `local_checksum` / `remote_checksum` | 内容校验和 |
| `match_status` | MATCH / DIFF / MISSING |
| `diff_summary` / `checked_at` | 差异说明与时间 |

## 四、契约

### 4.1 CAD

| 端点 | 权限 | 说明 |
|---|---|---|
| `POST /pdm/integration/cad/checkin` | `sys:pdm:int:cad:checkin` | multipart：`documentId`、`externalKey`、`file`、`changeNote`；`externalKey` 幂等，重放返回原版本 |
| `POST /pdm/integration/cad/ebom-import` | `sys:pdm:int:cad:import` | JSON：`externalKey`、`partNo`、`bomNo`、`view`、`lines[]`；按编码解析子件，创建 EBOM 草稿；任一子件不存在则整体回滚 |

### 4.2 ERP

| 端点 | 权限 | 说明 |
|---|---|---|
| `POST /pdm/integration/erp/part/{partId}/sync` | `sys:pdm:int:erp:sync` | 按零件编码推送，幂等键 `ERP:PART:{code}` |
| `POST /erp/internal/item/upsert` | 内网 | ERP 侧按 `pdmPartNo` upsert，返回 `{id, code, created}` |
| `GET /pdm/internal/bom/{bomNo}/versions/{versionNo}` | 内网 | M1A 既有契约；M5 增加拉取日志 `ERP:BOM_PULL:{bomNo}:{version}` |

### 4.3 MES

沿用 M3 的 `POST /mes/internal/process/route/**`；对账读取 `pdm_prc_publish`，失败可重推。

### 4.4 OA

沿用 M2 的审批回调；新增一致性检查与人工补偿：

| 端点 | 权限 | 说明 |
|---|---|---|
| `POST /pdm/integration/reconcile/oa/run` | `sys:pdm:int:reconcile:bom` | 对比 `approvalStatus` 与业务状态 |
| `POST /pdm/integration/reconcile/oa/{bizType}/{bizId}/compensate` | `sys:pdm:int:oa:compensate` | `bizType` 仅 REQUEST/ORDER；按审批快照回写，终态拒绝 |

### 4.5 运维

| 端点 | 权限 | 说明 |
|---|---|---|
| `GET /pdm/integration/sync/page` | `sys:pdm:int:sync:list` | 同步日志分页 |
| `POST /pdm/integration/sync/{id}/retry` | `sys:pdm:int:sync:retry` | PART_UPSERT / ROUTE_PUSH / BOM_NOTIFY 可重试 |
| `GET /pdm/integration/reconcile/page` | `sys:pdm:int:reconcile:list` | 对账结果分页 |
| `POST /pdm/integration/reconcile/bom/run` | `sys:pdm:int:reconcile:bom` | BOM 对账 |
| `POST /pdm/integration/reconcile/route/run` | `sys:pdm:int:reconcile:bom` | MES 对账 |
| `POST /pdm/integration/reconcile/bom/{bomNo}/versions/{versionNo}/notify` | `sys:pdm:int:reconcile:bom` | 重新通知 ERP |
| `POST /pdm/integration/reconcile/route/{routeId}/repush` | `sys:pdm:int:reconcile:bom` | 重推 MES |

## 五、幂等与状态规则

| 场景 | 规则 |
|---|---|
| CAD 检入 | `CAD:CHECKIN:{externalKey}` 已成功则直接返回原版本，不再生成新版本 |
| CAD EBOM 导入 | `CAD:EBOM:{externalKey}` 已成功则返回原 BOM；失败时删除半成品 BOM 并记录失败 |
| ERP 物料 | `ERP:PART:{code}` 覆盖写日志；ERP 侧按 `pdmPartNo` upsert |
| BOM 拉取 | `ERP:BOM_PULL:{bomNo}:{version}` 覆盖写日志，携带 checksum |
| BOM 对账 | 无日志 → MISSING；checksum 不等 → DIFF；相等 → MATCH |
| MES 对账 | `pdm_prc_publish` 为 FAILED → DIFF，其余 MATCH |
| OA 对账 | APPROVED→业务 APPROVED、REJECTED→REJECTED、WITHDRAWN→DRAFT/CANCELLED、RUNNING→SUBMITTED/APPROVING；终态 CLOSED/COMPLETED 视为 MATCH |
| OA 补偿 | 仅 DRAFT/SUBMITTED（ECR）或 ASSESSING/APPROVING（ECO）可补偿；补偿后写同步日志与审计 |

## 六、验收

- [x] CAD 重复导入同一 `externalKey` 返回原 BOM，不重复建单
- [x] CAD 子件缺失时整体回滚并记录失败日志
- [x] ERP 重复同步同一零件只更新物料，不重复创建
- [x] BOM 对账可区分 MATCH/DIFF/MISSING，差异可重新通知
- [x] MES 失败下发可重推，对账可见差异
- [x] OA 状态不一致可检测，补偿有审计且终态拒绝
- [x] 同步日志与对账结果可前端分页查看，失败可重试
- [x] `/erp/internal/**` 与 `/pdm/internal/**` 在网关 internal-deny
- [x] 迁移可重放，PDM 全量测试通过

## 七、不在本片范围

- SolidWorks/AutoCAD 插件本体、安装包与签名（另行交付，本仓库只提供契约与接口）。
- 审批引擎、撤回能力（归 OA）。
- ERP 只读回传价格/周期/库存的展示页（接口契约保留，前端留待 M1B ERP 只读）。
- `whale-message` 告警推送（当前以同步日志与对账结果承载，消息通道留待后续）。
