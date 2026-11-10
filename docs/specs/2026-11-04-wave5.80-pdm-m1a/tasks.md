# Wave 5.80 M1A：零件主档 + EBOM + 发布契约 任务清单

> 对应规格：[spec.md](./spec.md)
> 状态：**待评审 → 待执行**
> 约定：每个任务独立可验证；后端先行，前端随后；完成后统一回归并提交。

---

## 阶段一：数据库与权限

- [x] **T1.1 `V4__part_bom.sql`**
  - 逐列幂等扩展 `pdm_product`：`part_type/material/spec/uom/category_code/erp_item_code/source_type`。
  - 新建 `pdm_part_category`、`pdm_bom`、`pdm_bom_line`、`pdm_bom_release` 及唯一键/索引。
  - 租户 1 种子分类（`STD/COMMON/RAW`）。
  - 全脚本无 `DROP/TRUNCATE/DELETE`，`CREATE TABLE IF NOT EXISTS`。
- [x] **T1.2 `migration-5.80-pdm-part.sql`（auth）**
  - 权限 930–939；菜单 185–186；角色 21104 `pdm_engineer` + 业务租户复制。
  - 授权：`tenant_admin/pdm_manager/pdm_engineer` 930–939；`pdm_member` 930+935。
  - 全部 `NOT EXISTS` / MD5 幂等。

## 阶段二：零件主档扩展

- [x] **T2.1 实体/DTO/VO 扩展**
  - `PdmProduct`、`PdmProductSaveRequest`、`PdmProductQueryRequest`、`PdmProductVO` 增加 7 个字段。
- [x] **T2.2 服务默认值与校验**
  - `PdmProductServiceImpl#create/update` 归一化默认值、校验 `categoryCode` 存在。
  - `#delete` 增加 BOM 引用保护。
- [x] **T2.3 零件接口**
  - `PdmPartController`：`page/detail/where-used/create/update/delete`；权限 930–933；写操作审计。
  - `PdmPartCategoryController`：`tree/detail/create/update/delete`；权限 930/934。
  - `PdmPartCategoryService`：树、编码/同级名唯一、移动防环、路径重写、删除保护。

## 阶段三：BOM 结构

- [x] **T3.1 实体/Mapper/转换器**
  - `PdmBom`、`PdmBomLine`、`PdmBomRelease` 实体 + Mapper；`PdmConverter` 增加映射。
- [x] **T3.2 版本与校验工具**
  - `PdmBomVersionNo`（`V1.0` 递增/序号/格式）。
  - `PdmPartTypes`、`PdmBomStatus` 常量与判定。
- [x] **T3.3 BOM 服务**
  - `page/detail/create/delete`：产品校验、EBOM 限定、`bomNo` 自动生成、版本递增。
  - `addLine/updateLine/deleteLine`：DRAFT 限定、父装配约束、停用子件拒绝、`qty>0`、行号/子件唯一、环检测、删除保护。
  - `whereUsed(partId)`。
- [x] **T3.4 BOM 控制器**
  - `PdmBomController`：`/pdm/bom` 分页/详情/新建/删除/行增改删；权限 935–938；审计。
  - 错误消息固定 `BOM_CYCLE_DETECTED:` 前缀。

## 阶段四：发布与内网契约

- [x] **T4.1 快照构建器**
  - `PdmBomSnapshotBuilder`：树展开、路径、深度优先排序、固定字段 JSON、SHA-256。
- [x] **T4.2 发布服务**
  - `release(id, userId)`：至少 1 行、DRAFT→RELEASED、同家族旧版本 OBSOLETE、写 `pdm_bom_release`、重复发布返回既有记录。
  - `releases(bomNo)`。
- [x] **T4.3 内网端点**
  - `PdmInternalBomController`：`GET /pdm/internal/bom/{bomNo}/versions/{versionNo}`。
  - 无 `@RequirePermission`，依赖 `InternalTokenFilter`；404 语义。
- [x] **T4.4 契约测试**
  - 连续两次快照 `checksum/payload` 相等；行顺序稳定；未知版本 404。

## 阶段五：前端

- [x] **T5.1 API 层**
  - `src/api/part.ts`：零件、分类、BOM、发布、快照类型与函数。
- [x] **T5.2 零件库**
  - `pages/part/index.tsx` + `PartForm.tsx`：筛选、表格、新增/编辑、停用提示。
- [x] **T5.3 BOM 编辑器**
  - `pages/bom/index.tsx`：列表 + 新建。
  - `pages/bom/BomEditor.tsx`：树表展开、行增改删、发布确认、快照抽屉。
- [x] **T5.4 路由与菜单**
  - `App.tsx` 增加 `/part`、`/bom` 与权限码；默认 `/document` 不变。

## 阶段六：验证、文档与提交

- [x] **T6.1 后端回归**
  - `mvn -pl whale-backend/whale-apps/whale-pdm test` → 0 failures。
- [x] **T6.2 前端回归**
  - `pnpm --filter whale-pdm lint`、`pnpm --filter whale-pdm build`。
- [x] **T6.3 文档**
  - 更新计划 §6 实施状态；新增 `docs/reviews/2026-11-04-wave5.80-pdm-m1a.md`。
- [x] **T6.4 提交**
  - 仅暂存 PDM/auth/docs 本次文件；排除并行改动与锁文件。
  - 提交信息：`feat(pdm): 零件主档与 EBOM 发布快照契约`。

---

## 验收勾选

- [x] 4 层树可建改删；环检测返回 `BOM_CYCLE_DETECTED`。
- [x] 发布后不可改；重复发布幂等；新版本递增。
- [x] ERP 契约两次拉取一致；跨租户/不存在 404。
- [x] `partNo` 唯一；标准件跨 BOM 复用；停用零件不可入 BOM。
- [x] `/pdm/product/**` 旧接口与测试不变。
