# Wave 5.80 M1A 验收清单

> 对应规格：[spec.md](./spec.md) · 任务：[tasks.md](./tasks.md)
> 复核时间：2026-11-04

## 数据库与权限

- [x] `V4__part_bom.sql` 幂等扩展 `pdm_product` 7 列，且不含 `DROP/TRUNCATE/DELETE`
  - 证据：`information_schema.COLUMNS` + `PREPARE/EXECUTE/DEALLOCATE`；脚本头注释声明无破坏性语句。
- [x] 新建 `pdm_part_category`、`pdm_bom`、`pdm_bom_line`、`pdm_bom_release`，唯一键与索引齐备
  - 证据：`uk_part_category_code`、`uk_bom_no_version`、`uk_bom_line_no`、`uk_bom_line_child`、`uk_bom_release`。
- [x] `migration-5.80-pdm-part.sql` 幂等登记权限 930–939、菜单 185–186、角色 21104 与授权
  - 证据：全部 `NOT EXISTS`；`pdm_manager/pdm_engineer` 10 项、`pdm_member` 930+935。

## 零件主档

- [x] 零件新增/编辑缺省填充 `PART/PCS/UNIQUE`，并校验 `categoryCode` 存在
  - 证据：`PdmProductServiceImpl#normalize/assertCategoryExists`；`PdmProductServiceImplTest` 断言默认值。
- [x] 零件删除在 BOM 根/子件引用时返回 400
  - 证据：`PdmProductServiceImplTest#deleteShouldRejectWhenReferencedByBom`。
- [x] `/pdm/product/**` 旧接口与既有测试不变
  - 证据：后端 83 tests / 0 failures；旧控制器未改动。

## 物料分类

- [x] 分类树支持新增/编辑/删除，编码与同级名称唯一
  - 证据：`uk_part_category_code/name` + `PdmPartCategoryServiceImplTest`。
- [x] 移动分类禁止挂到自身后代，路径随之重写
  - 证据：`PdmPartCategoryServiceImpl#update` 祖先链校验 + 子树路径重写。
- [x] 存在子分类或被零件引用时拒绝删除
  - 证据：`PdmPartCategoryServiceImplTest#deleteShouldRejectWhenHasChildren/Referenced`。

## EBOM

- [x] 同一 `bomNo` 多版本，`V1.0 → V2.0` 递增
  - 证据：`PdmBomVersionNoTest`（4 例）。
- [x] 仅 `DRAFT` 可增改删行；父件必须为 `PRODUCT/ASSEMBLY`，停用子件拒绝
  - 证据：`PdmBomServiceImplTest` 草稿/装配/停用断言。
- [x] 同父同级 `lineNo` 与同父子件唯一，`qty > 0`
  - 证据：唯一键 + `PdmBomLineSaveRequest @DecimalMin` + 服务校验。
- [x] 自身或后代作为子件返回 `BOM_CYCLE_DETECTED:` 前缀
  - 证据：`PdmBomServiceImpl#assertNoCycle` + `PdmBomServiceImplTest`。

## 发布与快照

- [x] 发布至少 1 行，`DRAFT → RELEASED`，同家族旧版本置 `OBSOLETE`
  - 证据：`PdmBomServiceImplTest#releaseShouldObsoletePrevious`。
- [x] 重复发布同一版本返回既有快照（幂等）
  - 证据：`PdmBomServiceImplTest#releaseShouldBeIdempotent`。
- [x] 两次快照 `checksum/payload` 相等，行顺序稳定
  - 证据：`PdmBomSnapshotBuilderTest`；固定字段 JSON + SHA-256。
- [x] 内网端点无功能权限注解，未知版本 404
  - 证据：`PdmInternalBomController` + `PdmBomServiceImplTest#snapshotShouldReturn404`。

## 前端

- [x] `/part` 零件库：分类树筛选、分页表格、新增/编辑、使用处、分类维护
  - 证据：`src/pages/part/index.tsx`、`PartForm.tsx`。
- [x] `/bom` BOM 列表与结构编辑器：树表、行增改删、发布确认、发布记录
  - 证据：`src/pages/bom/index.tsx`、`BomEditor.tsx`。
- [x] `App.tsx` 增加 `/part`、`/bom` 菜单与路由，默认仍 `/document`
  - 证据：菜单权限码 `sys:pdm:part:list`、`sys:pdm:bom:list`。

## 回归与交付

- [x] 后端 `mvn -pl backend/whale-apps/whale-pdm test` 通过
  - 证据：83 tests / 0 failures / 0 errors。
- [x] 前端 `pnpm --filter whale-pdm lint` 与 `pnpm --filter whale-pdm build` 通过
  - 证据：ESLint 0 warnings；`tsc -b` + `vite build` 成功。
- [x] 计划 §6 状态与检查报告已更新
  - 证据：[计划 §6.4](../plans/2026-11-04-wave5.80-pdm-panorama.md#64-实施状态m1a2026-11-04)、[M1A 报告](../reviews/2026-11-04-wave5.80-pdm-m1a.md)。
