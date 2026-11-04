# Wave 5.80 M1A：零件主档 + EBOM + 发布契约 规格说明

> 状态：**待评审**（Spec Mode）
> 日期：2026-11-04
> 上游计划：[Wave 5.80 PDM 全景实施计划](../../plans/2026-11-04-wave5.80-pdm-panorama.md) §6 M1A
> 上游设计：[PDM 功能模块全景：领域模型与架构设计](../../design/pdm-module-panorama.md)
> 既有实现：M0-A/M0-B 图文档中心（提交 `baf3d617`、`43db665f`）
> 边界：审批归 OA/Workflow；文件字节归 `whale-file`；采购/生产 BOM 归 ERP；车间执行归 MES；PDM 不拆微服务（9003 / `/pdm/**` / `bluewhale_pdm` / 前端 8106）。

---

## 一、目标与范围

### 1.1 目标

1. 把存量 `pdm_product` 扩展为「零件/产品主档」，支持成品、部件、零件、原材料与标准件/通用件标记。
2. 新增物料分类树，支持按类型/分类/材质/规格/来源筛选。
3. 新增单层 EBOM 结构：`pdm_bom` + `pdm_bom_line`，支持 4 层树增删改、环检测、用量与行序号校验。
4. 新增 BOM 发布与不可变快照：`pdm_bom_release` 保存稳定 JSON + SHA-256 校验和。
5. 新增 ERP 内网契约：`GET /pdm/internal/bom/{bomNo}/versions/{versionNo}`，按 `bomNo + versionNo` 幂等。
6. 新增权限 930–939、菜单 185–186、角色 `pdm_engineer`(21104)。
7. 前端新增零件库页与 BOM 树编辑器/发布快照查看。
8. `/pdm/product/**` 旧接口与响应保持兼容。

### 1.2 不做（本阶段）

- PBOM/MBOM 派生、BOM 对比、Excel 导出、零件去重、ERP 供应商/库存只读（归 M1B）。
- ECR/ECO/ECN、审批流（归 M2 / OA）。
- 工艺路线与 MES 下发（归 M3）。
- 文件字节、CAD 预览（归 `whale-file` / M4）。
- 新增微服务、独立数据库、前端子应用。

---

## 二、关键决策（Decision-Complete）

| 编号 | 决策 | 结论 |
|---|---|---|
| D1 | 迁移版本号 | 新增 `V4__part_bom.sql`（V3 已被 ACL 主体迁移占用），保持严格递增。 |
| D2 | 零件主档归属 | 复用 `pdm_product`，`code` 即 `partNo`，唯一键 `uk_tenant_code` 不变；新增列全部可空或有默认值。 |
| D3 | 主档默认值 | `part_type` 默认 `PART`，`uom` 默认 `PCS`，`source_type` 默认 `UNIQUE`；请求缺省时由服务填充，避免显式插入 NULL。 |
| D4 | 分类编码 | `pdm_part_category.code` 租户内唯一；`category_code` 为逻辑引用，不建物理外键；引用不存在时返回 400。 |
| D5 | BOM 版本模型 | 一个 `bom_no` 下多版本，每版本一行 `pdm_bom`；唯一键 `(tenant_id, bom_no, version_no)`；版本号 `V1.0/V2.0/...`，每次新建递增主版本。 |
| D6 | BOM 视图 | M1A 仅允许 `EBOM`；`view` 列预留 `PBOM/MBOM`，非 `EBOM` 直接 400。 |
| D7 | BOM 行模型 | 行是「父件→子件」边：`bom_id + parent_part_id + child_part_id + line_no + qty + uom + loss_rate`；同一 BOM 同一父件下 `line_no` 与 `child_part_id` 均唯一。 |
| D8 | 环检测 | 加入行前从父件沿 `parent_part_id` 向上回溯；若子件等于父件或任一祖先 → `BOM_CYCLE_DETECTED`（400）。 |
| D9 | 装配约束 | 父件必须为 `PRODUCT/ASSEMBLY`；子件必须存在且 `ENABLED`；`PART/RAW_MATERIAL` 不得作为父件。 |
| D10 | 删除行 | 若该行的子件在本 BOM 中仍有下级行，拒绝删除（避免悬挂子树）；否则逻辑删除该行。 |
| D11 | 发布 | 仅 `DRAFT` 可发布；至少 1 行；发布后该版本 `RELEASED`，同 `bom_no` 其他 `RELEASED` 版本置 `OBSOLETE`；发布行与快照不可改。 |
| D12 | 发布幂等 | 已存在同 `(bom_no, version_no)` 快照时直接返回既有记录，不重复生成。 |
| D13 | 快照稳定性 | 行按父件路径深度优先排序；JSON 用固定字段顺序；`checksum = SHA-256(稳定 JSON)`；同一版本重复拉取结果完全一致。 |
| D14 | 内网契约 | `/pdm/internal/bom/**` 不加 `@RequirePermission`，依赖 `InternalTokenFilter` + 网关 `internal-deny`；不存在/跨租户一律 404。 |
| D15 | 零件删除保护 | 零件被任何 BOM 作为产品根或子件引用时拒绝删除（400），避免历史 BOM 悬挂。 |
| D16 | 权限号段 | PDM 900–1099；本阶段使用 930–939，菜单 185–186，角色 21104；940–949 / 187–189 继续预留。 |

---

## 三、数据库变更

### 3.1 `V4__part_bom.sql`（bluewhale_pdm）

**扩展 `pdm_product`**（幂等：逐列查 `information_schema.COLUMNS`，存在则 `SELECT 1`，不存在则 `ALTER TABLE ... ADD COLUMN`）：

| 列 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `part_type` | VARCHAR(16) NOT NULL | `PART` | PRODUCT/ASSEMBLY/PART/RAW_MATERIAL |
| `material` | VARCHAR(64) NULL | — | 材质 |
| `spec` | VARCHAR(128) NULL | — | 规格 |
| `uom` | VARCHAR(16) NOT NULL | `PCS` | 计量单位 |
| `category_code` | VARCHAR(64) NULL | — | 物料分类编码 |
| `erp_item_code` | VARCHAR(64) NULL | — | ERP 物料编码映射 |
| `source_type` | VARCHAR(16) NOT NULL | `UNIQUE` | STANDARD/COMMON/UNIQUE |

**新增表**：

1. `pdm_part_category`
   - `id, tenant_id, parent_id (0 根), code, name, path, sort_no, remark, create_time, update_time, deleted`
   - `UNIQUE uk_part_category_code (tenant_id, code)`
   - `UNIQUE uk_part_category_name (tenant_id, parent_id, name)`
   - `KEY idx_part_category_path (tenant_id, path)`
2. `pdm_bom`
   - `id, tenant_id, bom_no, product_id, product_code, view, version_no, version_seq, status, remark, create_time, update_time, deleted`
   - `UNIQUE uk_bom_no_version (tenant_id, bom_no, version_no)`
   - `KEY idx_bom_product (tenant_id, product_id, view, status)`
3. `pdm_bom_line`
   - `id, tenant_id, bom_id, parent_part_id, child_part_id, line_no, qty DECIMAL(18,6), uom, loss_rate DECIMAL(9,4) DEFAULT 0, remark, create_time, update_time, deleted`
   - `UNIQUE uk_bom_line_no (tenant_id, bom_id, parent_part_id, line_no)`
   - `UNIQUE uk_bom_line_child (tenant_id, bom_id, parent_part_id, child_part_id)`
   - `KEY idx_bom_line_parent (tenant_id, bom_id, parent_part_id)`
   - `KEY idx_bom_line_child (tenant_id, child_part_id)`
4. `pdm_bom_release`
   - `id, tenant_id, bom_id, bom_no, version_no, product_code, view, checksum CHAR(64), line_count, payload LONGTEXT, released_by, released_at, create_time, update_time, deleted`
   - `UNIQUE uk_bom_release (tenant_id, bom_no, version_no, checksum)`
   - `KEY idx_bom_release_no (tenant_id, bom_no, version_no)`

**种子**：租户 1 三个根分类（标准件 `STD`、通用件 `COMMON`、原材料 `RAW`），`INSERT IGNORE`。既有 `pdm_product` 两行由列默认值补足。

### 3.2 `migration-5.80-pdm-part.sql`（bluewhale_auth，幂等）

- 权限 930–939：

| ID | 权限码 | 路径/方法 |
|---|---|---|
| 930 | `sys:pdm:part:list` | GET `/pdm/part/page,/pdm/part/{id},/pdm/part/{id}/where-used,/pdm/part/category/tree,/pdm/part/category/{id}` |
| 931 | `sys:pdm:part:add` | POST `/pdm/part` |
| 932 | `sys:pdm:part:edit` | PUT `/pdm/part/{id}` |
| 933 | `sys:pdm:part:delete` | DELETE `/pdm/part/{id}` |
| 934 | `sys:pdm:part:category:manage` | POST/PUT/DELETE `/pdm/part/category,/pdm/part/category/{id}` |
| 935 | `sys:pdm:bom:list` | GET `/pdm/bom/page,/pdm/bom/{id},/pdm/bom/{id}/releases` |
| 936 | `sys:pdm:bom:add` | POST `/pdm/bom` |
| 937 | `sys:pdm:bom:edit` | POST/PUT/DELETE `/pdm/bom/{id}/lines,/pdm/bom/{id}/lines/{lineId}` |
| 938 | `sys:pdm:bom:delete` | DELETE `/pdm/bom/{id}` |
| 939 | `sys:pdm:bom:release` | POST `/pdm/bom/{id}/release` |

- 菜单：185 `零件库` `/pdm/part`（`sys:pdm:part:list`），186 `BOM 结构` `/pdm/bom`（`sys:pdm:bom:list`）。
- 角色：21104 `pdm_engineer`（PDM 工程师），并复制到既有业务租户。
- 授权：`tenant_admin/pdm_manager/pdm_engineer` → 930–939；`pdm_member` → 930 + 935。
- 全部 `NOT EXISTS` / `MD5` 稳定 ID，无删除语句。

---

## 四、后端设计

### 4.1 存量扩展

- [PdmProduct.java](file:///f:/blue-whale/backend/whale-apps/whale-pdm/src/main/java/com/bluewhale/pdm/entity/PdmProduct.java)：新增 `partType/material/spec/uom/categoryCode/erpItemCode/sourceType`。
- `PdmProductSaveRequest`：新增同名字段；`partType`、`sourceType` 使用 `@Pattern`；长度限制对齐列宽。
- `PdmProductQueryRequest`：新增 `partType/categoryCode/sourceType` 过滤。
- `PdmProductVO`：新增同名字段。
- `PdmProductServiceImpl`：
  - `create/update` 前归一化：空 `partType→PART`、空 `uom→PCS`、空 `sourceType→UNIQUE`；
  - 校验 `categoryCode` 存在（非空时）；
  - `delete` 前校验未被 BOM 引用（`pdm_bom.product_id` 或 `pdm_bom_line.child_part_id`）；
  - 旧测试断言字段保持不变。

### 4.2 新包 `com.bluewhale.pdm.part`

**实体/Mapper**：`PdmPartCategory`、`PdmBom`、`PdmBomLine`、`PdmBomRelease` + 4 个 `BaseMapper`。

**支撑类**：
- `PdmPartTypes`：`PRODUCT/ASSEMBLY/PART/RAW_MATERIAL`、`STANDARD/COMMON/UNIQUE`、合法性判断。
- `PdmBomStatus`：`DRAFT/RELEASED/OBSOLETE`。
- `PdmBomVersionNo`：`nextMajor("V1.0")→"V2.0"`、`seqOf`（major*10000+minor）、校验格式 `V\d+\.\d+`。
- `PdmBomSnapshotBuilder`：由 BOM 头 + 行构建树、深度优先展开、稳定 JSON、SHA-256。

**服务**：
- `PdmPartCategoryService`：`tree/create/update/delete`；建/改校验 code 唯一、同级名称唯一、移动防环、路径重写；有子分类或零件引用时拒绝删除。
- `PdmBomService`：
  - `page(PdmBomQueryRequest)`；
  - `detail(id)` 返回头 + 行 + 树；
  - `create(PdmBomSaveRequest)`：产品存在且为 PRODUCT/ASSEMBLY，EBOM，生成 `bomNo`（缺省 `BOM-{productCode}`）与版本（同 `bomNo` 已有则下一主版本）；
  - `addLine/updateLine/deleteLine`：DRAFT 限定、父/子校验、环检测、用量 > 0、行号/子件唯一、删除保护；
  - `release(id, userId)`：DRAFT → RELEASED + 快照 + 同家族旧版本 OBSOLETE + 幂等；
  - `releases(bomNo)` / `snapshot(bomNo, versionNo)`；
  - `delete(id)`：仅 DRAFT 且未发布可删；
  - `whereUsed(partId)`：返回引用该零件的 BOM（`bomNo/versionNo/view/productCode/status`）。
- `PdmBomService` 复用 `PdmProductMapper` 与 4 个 Mapper，不跨包访问 `document`。

**控制器**：
- `PdmPartController` `/pdm/part`：`page/{id}/where-used` + `POST/PUT/DELETE`；权限 930–933；写操作 `@AuditLog(module="PDM 零件库")`。
- `PdmPartCategoryController` `/pdm/part/category`：`tree/{id}` + `POST/PUT/DELETE`；权限 930/934。
- `PdmBomController` `/pdm/bom`：`page/{id}/releases`、`POST`、`/lines` 增改删、`/release`、`DELETE`；权限 935–939；写操作 `@AuditLog(module="PDM BOM")`。
- `PdmInternalBomController` `/pdm/internal/bom`：`GET /{bomNo}/versions/{versionNo}`；无 `@RequirePermission`，依赖内网 token 过滤器；404 语义。

**转换器**：`PdmConverter` 增加分类、BOM 头、BOM 行、发布快照的 MapStruct 映射。

### 4.3 错误语义

| 场景 | 返回 |
|---|---|
| 零件/分类/BOM 不存在或跨租户 | 404 `NOT_FOUND` |
| 编码/分类重复、非法类型、停用零件入 BOM、空 BOM 发布 | 400 `PARAM_ERROR` |
| 加入自身/祖先作为子件 | 400 `PARAM_ERROR`，消息固定前缀 `BOM_CYCLE_DETECTED:` |
| 非 DRAFT 修改/发布 | 400 `PARAM_ERROR`，消息说明当前状态 |
| 内网快照不存在 | 404 `NOT_FOUND` |

---

## 五、前端设计

- `src/api/part.ts`：零件分页/详情/保存/删除/where-used、分类树/维护、BOM 分页/详情/建/行增改删/发布/快照列表。
- `src/pages/part/index.tsx`：筛选（关键字/类型/来源/分类/状态）+ 表格 + 新增/编辑弹窗。
- `src/pages/part/PartForm.tsx`：编码、名称、类型、来源、材质、规格、单位、分类、ERP 编码、状态、备注。
- `src/pages/bom/index.tsx`：BOM 列表（`bomNo/version/view/product/status`）+ 新建弹窗（选产品）+ 进入编辑器。
- `src/pages/bom/BomEditor.tsx`：树表（`antd Table` 展开层级）、新增行（父件/子件/用量/单位/损耗/行号）、编辑、删除、发布确认、快照抽屉；`sys:pdm:bom:release` 控制发布按钮。
- `App.tsx`：新增 `/part`「零件库」、`/bom`「BOM 结构」菜单与路由；默认仍 `/document`。
- 所有按钮受 `hasPermission` 控制，接口仍由后端兜底。

---

## 六、验证与验收

### 6.1 自动化验证

| 项 | 命令/断言 |
|---|---|
| 后端单测 | `mvn -pl backend/whale-apps/whale-pdm test`，0 failures |
| 新增测试 | `PdmBomVersionNoTest`、`PdmBomSnapshotBuilderTest`、`PdmPartCategoryServiceImplTest`、`PdmBomServiceImplTest`、`PdmPartReferenceGuardTest` |
| Flyway 静态校验 | 现有 `PdmFlywayMigrationScriptTest` 自动覆盖 V4 命名/递增/无破坏语句 |
| 合同测试 | 同一 `bomNo+versionNo` 连续两次快照 `checksum` 与 `payload` 完全相等；未知版本 404 |
| 架构守护 | `PdmArchitectureTest` 5 条继续通过 |
| 前端 | `pnpm --filter whale-pdm lint`、`pnpm --filter whale-pdm build` |

### 6.2 DoD 对照

- 成品 → 部件 → 零件 → 原材料 4 层树可建、可改、可删；自引用/后代引用返回 `BOM_CYCLE_DETECTED`。
- 发布后 BOM 与快照不可改；再次发布生成新版本；重复发布同版本返回既有快照。
- ERP 契约两次拉取一致、行顺序稳定、跨租户/不存在 404。
- `partNo`（`code`）租户内唯一；标准件可多 BOM 复用；停用零件不能入新 BOM。
- `/pdm/product/**` 旧接口与既有测试不变。
- 前端 BOM 树按需展开，操作后局部刷新。

---

## 七、交付与提交

- 新增文件：`V4__part_bom.sql`、`migration-5.80-pdm-part.sql`、`com.bluewhale.pdm.part` 包、前端 `api/part.ts` 与 `pages/part`、`pages/bom`、测试 5 个、评审文档 1 个。
- 修改文件：`PdmProduct` 四件套、`PdmProductServiceImpl`、`PdmConverter`、`App.tsx`、计划文档 §6 状态。
- 提交：仅暂存上述 PDM/auth 专属文件与本次 docs；**不提交** `docs/devlog/2026-11-04.md`、`docs/requirements/register.md`、`docs/service-catalog.md`、`frontend/pnpm-lock.yaml`。
- 提交信息：`feat(pdm): 零件主档与 EBOM 发布快照契约`。

---

## 八、待确认

无阻塞项。若需调整，仅影响：BOM 版本递增策略（D5）、空 BOM 是否允许发布（D11）、零件删除保护强度（D15）。
