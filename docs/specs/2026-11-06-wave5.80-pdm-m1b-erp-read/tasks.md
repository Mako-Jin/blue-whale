# Wave 5.80 PDM M1B-5 任务清单

> 状态：全部完成（2026-11-06）
> 规格：[spec.md](spec.md)

| 编号 | 任务 | 交付物 | 状态 |
|---|---|---|---|
| M1B-5-1 | ERP 物料只读接口 | `ErpItemService.getByCode` + `GET /erp/internal/item/by-code` | 完成 |
| M1B-5-2 | ERP 库存聚合接口 | `StockRepository.listBalancesByItemCode` + `InventoryAppService.stockByItemCode` + `GET /erp/internal/stock/by-code` | 完成 |
| M1B-5-3 | PDM 客户端 | `PdmIntErpClient#getItem/getStock` 与解析/降级 | 完成 |
| M1B-5-4 | PDM 回显接口 | `PdmPartErpService` + `GET /pdm/part/{id}/erp-stock` | 完成 |
| M1B-5-5 | 前端回显 | 零件库「ERP 库存」按钮与弹窗 | 完成 |
| M1B-5-6 | 后端单测 | PDM 客户端映射/回退/降级/缺件 4 例；ERP 现有 466 例回归 | 完成 |
| M1B-5-7 | 文档同步 | 全景计划 M1B-5、评审、devlog | 完成 |

## 未完成（明确留待）

- 库存可用量/预占：依赖 ERP 库存预占模型，本轮不做。
- ERP 物料列表批量同步：本轮仅按编码只读回显。
