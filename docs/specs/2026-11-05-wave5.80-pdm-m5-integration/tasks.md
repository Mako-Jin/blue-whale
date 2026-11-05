# Wave 5.80 PDM M5 任务清单

> 状态：全部完成（2026-11-05）
> 规格：[spec.md](spec.md)

| 编号 | 任务 | 交付物 | 状态 |
|---|---|---|---|
| M5-1 | CAD 集成契约 | `/pdm/integration/cad/checkin`、`/ebom-import`；`PdmIntCadService` 幂等检入与 EBOM 草稿导入 | 完成 |
| M5-2 | ERP 物料同步 | `PdmIntErpClient` + `/erp/internal/item/upsert`；按 `pdmPartNo` 幂等 upsert；`pdm_int_sync_log` 记录 | 完成 |
| M5-3 | BOM 同步对账 | ERP 拉取 BOM 记录 checksum；`reconcileBom` 三态结论；重新通知端点 | 完成 |
| M5-4 | MES 下发对账 | `reconcileRoute` 读取 `pdm_prc_publish`；失败重推端点 | 完成 |
| M5-5 | OA 双向状态 | `reconcileOa` 一致性检查；`compensateOa` 人工补偿与审计 | 完成 |
| M5-6 | 同步运维页 | `/pdm/integration/sync` 页、`/pdm/integration/reconcile` 页；重试/重推/补偿 | 完成 |
| M5-7 | 权限/菜单 | `migration-5.80-pdm-integration.sql`：权限 990–997、菜单 202–203 | 完成 |
| M5-8 | 迁移 | `V12__integration_sync.sql`：`pdm_int_sync_log`、`pdm_int_reconcile` | 完成 |
| M5-9 | 网关 | `/erp/internal/**` 加入 internal-deny | 完成 |
| M5-10 | 测试与文档 | 同步 5 项、对账 6 项、CAD 4 项、迁移 1 项；PDM 197 tests；ERP 466 tests | 完成 |

## 未完成（明确留待）

- 外部 CAD 插件本体与安装包：只提供契约与 REST 接口，桌面端另行交付。
- `whale-message` 告警推送：当前以同步日志与对账结果替代，消息通道后续接入。
- ERP 只读回传价格/周期/库存展示：契约保留，前端随 M1B ERP 只读推进。
- MES 撤回的双向对账页面：M3 已提供撤回接口，运维页暂不展示撤回。
