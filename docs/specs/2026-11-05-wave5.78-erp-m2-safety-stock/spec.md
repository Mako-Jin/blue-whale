# Wave 5.78 M2 剩余 B 第四期：安全库存、缺料预警与呆滞分析

> 状态：**已实施（2026-11-05，验证与文档回填完成）**
> 关联计划：[ERP 全景计划](../../plans/2026-11-03-wave5.78-erp-panorama.md) §12.8
> 关联设计：[ERP 模块全景设计](../../design/erp-module-panorama.md) §3.2
> 关联需求：[REQ-FUNC-038](../../requirements/register.md)
> 上游切片：价格表（V11、权限 527–532、菜单 102）

---

## 1. 范围

### 1.1 本期交付

| 能力 | 说明 |
|---|---|
| 安全库存维护 | 按「仓库 + 物料」维护安全库存量与最高库存量，支持分页、详情、新增、修改、删除 |
| 缺料预警 | 按安全库存配置与实际库存余额（默认 0）比较，输出缺口数量 |
| 呆滞分析 | 库存余额 > 0 且最近出库流水早于 N 天（默认 90）的物料，输出积压数量、金额与库龄 |

### 1.2 明确不做（并记录原因）

- **批次追溯**：需在既有 `erp_scm_stock_movement` / `erp_scm_stock_balance` 上 `ALTER TABLE` 增加批次列，与「迁移仅允许 `CREATE TABLE IF NOT EXISTS`」的质量门禁冲突，推迟至单独迁移窗口。
- **安全库存自动补货建议**：MRP 剩余阶段统一处理，本期只做预警查询，不生成计划订单。
- **呆滞计提跌价准备**：涉及成本与财务扩展规则（D5/D6 未定稿），不写死默认。

---

## 2. 数据模型

新增迁移 `V12__scm_safety_stock.sql`（仅 `CREATE TABLE IF NOT EXISTS`，无物理外键）：

```sql
CREATE TABLE IF NOT EXISTS erp_scm_safety_stock (
    id          BIGINT        NOT NULL,
    tenant_id   BIGINT        NOT NULL DEFAULT 1,
    warehouse_id BIGINT       NOT NULL,
    item_id     BIGINT        NOT NULL,
    item_code   VARCHAR(64)   NOT NULL,
    item_name   VARCHAR(128)  NOT NULL,
    safety_qty  DECIMAL(18,4) NOT NULL DEFAULT 0,
    max_qty     DECIMAL(18,4) DEFAULT NULL,
    remark      VARCHAR(255)  DEFAULT NULL,
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_safety_stock (tenant_id, warehouse_id, item_id),
    KEY idx_safety_stock_item (tenant_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='安全库存配置';
```

不变量：

1. `safety_qty >= 0`；`max_qty` 为空表示不设上限，非空必须 `>= safety_qty`。
2. 「租户 + 仓库 + 物料」唯一，重复新增返回 `40941`。
3. 删除为逻辑删除；预警与呆滞不依赖该表删除状态以外的快照。

---

## 3. 后端设计（`com.bluewhale.erp.scm`）

### 3.1 新增文件

| 分层 | 文件 |
|---|---|
| domain/model | `SafetyStock.java`、`SafetyStockQuery.java` |
| domain/repository | `SafetyStockRepository.java` |
| application/command | `SafetyStockCreateCommand.java`、`SafetyStockUpdateCommand.java` |
| application/view | `SafetyStockView.java`、`ShortageView.java`、`IdleStockView.java` |
| application/service | `SafetyStockAppService.java`、`impl/SafetyStockAppServiceImpl.java` |
| infrastructure | `entity/SafetyStockEntity.java`、`mapper/SafetyStockMapper.java`、`repository/SafetyStockRepositoryImpl.java` |
| interfaces/controller | `SafetyStockController.java` |

### 3.2 仓储扩展

- `StockRepository` 增加 `List<StockBalance> listBalances(ScmQuery query)`（不分页，供预警/呆滞计算）。
- `StockRepository` 增加 `Optional<LocalDateTime> lastOutboundAt(Long itemId, Long warehouseId)`：
  `direction = 'OUT'` 且 `deleted = 0` 的最大 `occurred_at`。

### 3.3 分析口径

**缺料预警**：`shortageQty = safetyQty - onHandQty`，仅返回 `shortageQty > 0`；`onHandQty` 缺失按 0。
支持 `warehouseId / itemId / keyword` 过滤，内存计算后按 `current/size` 切片，`total` 为命中去重条数。

**呆滞分析**：余额 `quantity > 0`；`lastOutboundAt` 为空视为从未出库（计数为 `null`，前端显示「从未出库」，且无论阈值均计入）；
非空时 `idleDays = between(lastOutboundAt.date, today)`，保留 `idleDays >= idleDays 参数`（默认 90）。
参数 `idleDays < 1` 抛 `40024`。排序：库龄降序（空置顶），金额降序。

### 3.4 错误码（`ErpErrorCode` 追加）

| 码 | 常量 | 语义 |
|---|---|---|
| 40024 | `SAFETY_STOCK_INVALID` | 安全库存参数非法（数量为负或上限小于安全量） |
| 40439 | `SAFETY_STOCK_NOT_FOUND` | 安全库存记录不存在 |
| 40941 | `SAFETY_STOCK_CONFLICT` | 同「仓库 + 物料」配置已存在 |

### 3.5 端点与权限（权限 533–538、菜单 103）

| 方法 | 路径 | 权限码 |
|---|---|---|
| GET | `/erp/scm/safety-stock/page` | `sys:erp:safety-stock:list` (533) |
| GET | `/erp/scm/safety-stock/{id}` | `sys:erp:safety-stock:detail` (534) |
| POST | `/erp/scm/safety-stock` | `sys:erp:safety-stock:create` (535) |
| PUT | `/erp/scm/safety-stock/{id}` | `sys:erp:safety-stock:update` (536) |
| DELETE | `/erp/scm/safety-stock/{id}` | `sys:erp:safety-stock:delete` (537) |
| GET | `/erp/scm/safety-stock/shortage`、`/idle` | `sys:erp:safety-stock:analysis` (538) |

写操作加 `@AuditLog`。权限脚本 `migration-5.78-erp-safety-stock.sql`：`tenant_admin/erp_manager/erp_scm` 全量，
`erp_member` 仅 533/534/538；菜单 103 按租户 MD5 幂等复制。

### 3.6 测试

- `SafetyStockTest`：数量校验、上限校验、边界值。
- `SafetyStockAppServiceImplTest`：创建快照物料与仓库、重复冲突、更新、删除、缺料计算、呆滞阈值与从未出库。
- `ErpFlywayMigrationScriptTest`：新增 V12 断言（1 表、`uk_safety_stock`、无 `FOREIGN KEY`）。

---

## 4. 前端设计（`whale-frontend/platform-apps/whale-erp`）

| 文件 | 变更 |
|---|---|
| `src/api/scm.ts` | 新增 `SafetyStockView/ShortageView/IdleStockView` 与 6 个 api 函数 |
| `src/pages/SafetyStockPage.tsx` | 新建：三页签（安全库存 / 缺料预警 / 呆滞分析），仓库与物料下拉复用 `pageWarehouses`、`listErpItems` |
| `src/App.tsx` | 菜单项与页面路由 `/erp/scm/safety-stock` |
| `../whale-platform/src/router/menu.ts` | 注入平台菜单项，权限 `sys:erp:safety-stock:list` |

按钮按权限码显隐；删除二次确认。

---

## 5. 验收标准

1. 空库启动自动建 `erp_scm_safety_stock`；重复执行 V12 无副作用。
2. 同仓库同物料重复新增返回 40941；`max_qty < safety_qty` 返回 40024。
3. 缺料预警只返回缺口 > 0 的项，库存缺失按 0；呆滞只返回余额 > 0 且库龄达阈值项。
4. 跨租户访问返回 40439。
5. 后端 `.\mvnw.cmd -pl :whale-erp "-Pcoverage-gate" verify` 全绿（含 Checkstyle / ArchUnit）。
6. 前端 `pnpm --filter whale-erp lint/test/build`、`pnpm --filter whale-platform lint/build` 全绿。
7. 文档一致性脚本与部署契约脚本 PASSED。
