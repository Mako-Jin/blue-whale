# Wave 5.80 PDM M3：工艺管理（轻量）+ MES 下发 规格

> 状态：**已实现（2026-11-05）**
> 关联计划：[Wave 5.80 PDM 全景计划](../../plans/2026-11-04-wave5.80-pdm-panorama.md) §九
> 关联需求：REQ-FUNC-055（工艺管理）、REQ-ARCH-020（PDM 上下文边界）、REQ-SEC-030（内网端点与审计）
> 服务边界：`whale-pdm` 9003 / `/pdm/**` / `bluewhale_pdm` / 前端 8106；MES 9002 / `/mes/**`

---

## 一、目标

1. 在 PDM 内建立**轻量工艺路线**：路线 → 工序 → 工装 → SOP，支撑零件/产品的工艺准备。
2. 路线**版本化**：`DRAFT → RELEASED → OBSOLETE`；发布后不可编辑，新版本按零件递增。
3. **SOP 只能引用已发布文档版本**，复用 M0 图文档中心的版本与内容能力。
4. 发布时把路线**幂等下发 MES**；MES 不可用不阻塞 PDM 发布，失败可重试，撤回同步作废。
5. 内网提供 SOP 内容读取端点，供 MES 按 `documentId + versionNo` 拉取，外部 403。

**不做**：车间工序执行、报工、设备采集、排产（归 MES）；审批流（归 OA/Workflow）。

---

## 二、数据模型（`V10__process_route.sql`）

| 表 | 用途 | 关键约束 |
|---|---|---|
| `pdm_prc_route` | 工艺路线主档 | 唯一 `(tenant_id, route_no, version_no)`、`(tenant_id, part_id, version_no)` |
| `pdm_prc_step` | 工序与工时 | 唯一 `(tenant_id, route_id, step_no)`；工时不小于 0 |
| `pdm_prc_tooling` | 工装夹具 | 可挂工序（`step_id`），也可为路线级（`step_id` 为空） |
| `pdm_prc_sop` | 工序 SOP 引用 | 唯一 `(tenant_id, route_id, step_id)`；仅 `RELEASED` 文档版本 |
| `pdm_prc_publish` | MES 下发记录 | 唯一 `(tenant_id, idempotency_key)`、`(tenant_id, route_id)` |

MES 侧新增 `mes_process_route`（`V9__mes_process_route.sql`），唯一 `(tenant_id, pdm_route_no, version_no)`，保存下发 JSON 原文、checksum、状态 `ACTIVE/WITHDRAWN`。

所有迁移仅 `CREATE TABLE IF NOT EXISTS`，无 `DROP/TRUNCATE/DELETE`。

---

## 三、状态机与业务规则

### 3.1 路线

- 新建默认 `V1.0`；同零件已有路线时下一个主版本（`V1.0 → V2.0`）。
- 编号缺省 `RT-{partCode}`；同零件同版本唯一，重复创建返回业务错误。
- 仅 `DRAFT` 可改备注、维护工序/工装/SOP、删除。
- `release`：
  - 仅 `DRAFT`；至少 1 道工序；所有 SOP 仍引用已发布版本。
  - 置 `RELEASED`，同编号旧 `RELEASED` 置 `OBSOLETE`。
  - 写入下发记录并调用 MES；**MES 失败只置 `FAILED`，不抛异常、不回滚发布**。
- `retryPublish`：仅 `RELEASED` 且记录 `FAILED`；累加 `retry_count`；成功置 `SUCCESS`。
- `withdraw`：仅 `RELEASED`；路线置 `OBSOLETE`，下发记录置 `WITHDRAWN`；MES 撤回失败只记录 `last_error`。

### 3.2 工序 / 工装 / SOP

- 工序序号正数且路线内唯一；缺省取最大序号 +1；标准/准备工时不得为负。
- 工序删除级联删除该工序工装与 SOP；路线级工装保留。
- 工装类型 `TOOL/FIXTURE/MOLD/GAUGE`，编码必填，数量不小于 1。
- SOP 保存校验文档版本存在且 `RELEASED`；同工序重复保存为覆盖。

---

## 四、接口清单（`/pdm/process/route`）

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/page` | `sys:pdm:process:list` |
| GET | `/{id}` | `sys:pdm:process:list` |
| POST | `/` | `sys:pdm:process:add` |
| PUT | `/{id}` | `sys:pdm:process:edit` |
| DELETE | `/{id}` | `sys:pdm:process:delete` |
| POST/PUT/DELETE | `/{id}/steps`、`/{id}/steps/{stepId}` | `sys:pdm:process:edit` |
| POST/DELETE | `/{id}/toolings`、`/{id}/toolings/{toolingId}` | `sys:pdm:process:edit` |
| POST/DELETE | `/{id}/sop`、`/{id}/sop/{sopId}` | `sys:pdm:process:edit` |
| POST | `/{id}/release` | `sys:pdm:process:release` |
| POST | `/{id}/publish/retry` | `sys:pdm:process:publish` |
| POST | `/{id}/withdraw` | `sys:pdm:process:withdraw` |

### 4.1 MES 契约

- `POST /mes/internal/process-route`：请求 `routeNo, versionNo, partCode, partName, checksum, steps[]`（步骤含 `toolings[]` 与 `sopDocumentId/sopDocumentVersionNo`）。
- 幂等：`routeNo + versionNo` 且 checksum 一致直接返回既有记录（`idempotent=true`）；checksum 变化覆盖内容。
- `POST /mes/internal/process-route/withdraw`：请求 `routeNo, versionNo`；未接收过也落撤回墓碑，防止乱序回调复活旧路线。

### 4.2 SOP 内容读取

- `GET /pdm/internal/document/{documentId}/versions/{versionNo}/content`。
- 仅 `RELEASED` 版本可读；网关 `internal-deny` 保证外部 403；写审计 `内网读取 SOP 内容`。

---

## 五、权限与菜单

- 权限 970–976（977–979 预留）：`list/add/edit/delete/release/publish/withdraw`。
- 菜单 195 工艺路线、196 工序与工装、197 MES 下发。
- 角色 21106 `pdm_process_engineer`，并按既有租户复制；
  - `tenant_admin/pdm_manager/pdm_engineer/pdm_process_engineer` → 970–976；
  - `pdm_member` → 970。

---

## 六、验收与验证

- [x] 路线 `V1.0 → V2.0` 版本递增，同零件同版本唯一。
- [x] 发布后编辑、增删工序/工装/SOP 均被拒。
- [x] 工序序号重复、工时为负被拒。
- [x] SOP 引用草稿版本被拒。
- [x] MES 不可用时路线仍 `RELEASED`，下发记录 `FAILED` 且可重试成功。
- [x] 撤回后路线 `OBSOLETE`、下发记录 `WITHDRAWN`，MES 失败只记录原因。
- [x] MES 接收同一 `routeNo + versionNo + checksum` 幂等，不重复落库。
- [x] 单测：PDM 新增 9 项、MES 新增 4 项；全量 PDM 161 项通过。
- [x] 前端 `lint`、`tsc -b && vite build` 通过。

---

## 七、遗留与边界

- 路线发布未接入 OA 审批（按计划归 OA/Workflow，M5 再收口）。
- MES 下发为同步调用；未做独立重试调度器，依赖人工/后续任务重试。
- 工装仅登记编码与规格，不关联工具主数据。
- 真实库执行 V10、`migration-5.80-pdm-process.sql` 与 MES V9 后需复核表结构与授权。
