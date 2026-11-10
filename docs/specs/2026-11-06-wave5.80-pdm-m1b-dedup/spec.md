# Wave 5.80 PDM M1B-4：零件去重候选与人工合并 规格

> 状态：已实现（2026-11-06）
> 关联计划：[Wave 5.80 PDM 全景计划 §M1B-4](../../plans/2026-11-04-wave5.80-pdm-panorama.md)
> 关联需求：REQ-FUNC-051、REQ-ARCH-020、REQ-SEC-030

## 一、目标

1. 系统按“归一化名称 + 材质 + 规格”自动给出重复零件候选组，供工程师审阅。
2. 工程师选定源件与保留件后一次合并：改写可编辑 BOM 中的子件引用，停用源件并记录合并目标。
3. 已发布（RELEASED/OBSOLETE）BOM 与其发布快照保持不可变，历史追溯不断链。
4. 合并动作可审计，重复提交幂等拒绝。

## 二、约束

| 编号 | 约束 |
|---|---|
| C-1 | 不新增服务；去重与合并留在 `whale-pdm` 的 `part` 上下文 |
| C-2 | 归一化仅做去首尾空白、大小写不敏感、连续空白折叠；不做同义词/拼音近似 |
| C-3 | 仅 `DRAFT` BOM 的 `child_part_id` 可被改写；`RELEASED`/`OBSOLETE` 只统计跳过数 |
| C-4 | 源件不物理删除：`status=DISABLED`，`merged_to` 指向保留件 |
| C-5 | 源件若为 BOM 主体或作为父件出现在任何 BOM 行中，拒绝合并（避免装配树断链） |
| C-6 | 同一 `(bom_id, parent_part_id, child_part_id)` 已存在保留件行时，用量累加到保留件行并删除源行 |
| C-7 | 迁移仅允许幂等 `ALTER/CREATE INDEX`，禁止 `DROP/TRUNCATE/DELETE` |
| C-8 | 合并接口必须权限保护并写审计，权限号沿用 PDM 段 941–942（940 已被 BOM 导出占用） |

## 三、数据模型

### 3.1 `pdm_product.merged_to`

| 字段 | 类型 | 说明 |
|---|---|---|
| `merged_to` | BIGINT NULL | 合并目标零件 ID；非空表示该零件已被合并停用 |

迁移 `V13__part_merge.sql` 逐列检查 `information_schema` 后幂等追加，并补 `idx_product_merged_to`。

## 四、契约

### 4.1 候选查询

`GET /pdm/part/dedup/candidates`（权限 `sys:pdm:part:dedup:list`）

返回按候选数倒序、最多 100 组的数组：

```json
[
  {
    "dedupKey": "name|material|spec",
    "name": "六角螺栓",
    "material": "304",
    "spec": "M8*30",
    "count": 3,
    "parts": [
      {"id": "1", "code": "P-001", "name": "六角螺栓", "partType": "PART",
       "material": "304", "spec": "M8*30", "uom": "PCS", "sourceType": "STANDARD",
       "status": "ENABLED", "erpItemCode": "ERP-001", "createTime": "2026-11-01T10:00:00"}
    ]
  }
]
```

仅统计 `status=ENABLED` 且未删除零件；单次扫描上限 5000 条，避免全表拖库。

### 4.2 合并

`POST /pdm/part/merge`（权限 `sys:pdm:part:merge`，审计类型 UPDATE）

```json
{"sourceId": "1", "targetId": "2", "remark": "同图号重复建码"}
```

响应：

```json
{"sourceId": "1", "targetId": "2", "rewrittenLines": 4, "mergedLines": 2, "skippedReleasedLines": 3}
```

### 4.3 校验与幂等

| 场景 | 结果 |
|---|---|
| 源/目标不存在 | `NOT_FOUND` |
| 源 == 目标 | `PARAM_ERROR`「源零件与保留零件不能相同」 |
| 源件已停用或 `merged_to` 非空 | `PARAM_ERROR`「源零件已停用或已合并」 |
| 目标件非启用或 `merged_to` 非空 | `PARAM_ERROR`「保留零件不可用」 |
| 源件是 BOM 主体（`pdm_bom.product_id`） | `PARAM_ERROR`「源零件为 BOM 主体，不能合并」 |
| 源件作为父件出现在 BOM 行 | `PARAM_ERROR`「源零件在 BOM 结构中作为父件使用，不能合并」 |

## 五、权限与菜单

| 编号 | 权限码 | 说明 | 授权 |
|---|---|---|---|
| 941 | `sys:pdm:part:dedup:list` | 查看去重候选 | tenant_admin / pdm_manager / pdm_engineer / pdm_member |
| 942 | `sys:pdm:part:merge` | 执行合并 | tenant_admin / pdm_manager / pdm_engineer |

菜单 187：`零件去重`，路径 `/pdm/part/dedup`，组件 `pdm/part/dedup`，权限 `sys:pdm:part:dedup:list`。

## 六、验证

- 后端：`mvn -o -pl whale-backend/whale-apps/whale-pdm test`（新增去重分组、改写、跳过、冲突累加、拒绝分支、迁移用例）。
- 前端：`pnpm --filter whale-pdm lint && pnpm --filter whale-pdm build`。
- 迁移：`V13__part_merge.sql` 可重复执行；`migration-5.80-pdm-part-merge.sql` 幂等。

## 七、明确不做

- 不做自动合并、不做相似度/拼音/图号近似匹配。
- 不改写已发布 BOM 及其发布快照。
- 不迁移 ERP 侧物料主数据（由 M1B-5 的只读契约承接）。
