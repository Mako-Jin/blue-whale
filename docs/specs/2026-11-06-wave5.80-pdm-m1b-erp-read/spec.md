# Wave 5.80 PDM M1B-5：ERP 物料/库存只读契约 规格

> 状态：已实现（2026-11-06）
> 关联计划：[Wave 5.80 PDM 全景计划 §M1B-5](../../plans/2026-11-04-wave5.80-pdm-panorama.md)
> 关联需求：REQ-FUNC-051、REQ-ARCH-020

## 一、目标

1. ERP 暴露内网只读接口，按物料编码返回物料状态与各仓库库存；外部经网关访问继续 403。
2. PDM 零件库可按零件维护的 `erpItemCode`（缺省回退零件编码）回显 ERP 物料与库存。
3. ERP 不可用时 PDM 降级返回 `degraded=true`，不抛错、不阻断零件页。

## 二、约束

| 编号 | 约束 |
|---|---|
| C-1 | 只读；复用既有 `InternalTokenSupport` 内网令牌，不新增外部鉴权体系 |
| C-2 | 路径落在 `/erp/internal/**`，依赖既有网关 internal-deny 规则，不新增网关路由 |
| C-3 | 不新增数据库表与迁移；库存取 `stock_balance` 现有快照 |
| C-4 | 物料编码口径与 PDM→ERP 同步一致：优先 `pdm_product.erp_item_code`，未维护时用零件编码 |
| C-5 | 不新增权限码；PDM 回显接口复用 `sys:pdm:part:list` |
| C-6 | ERP 调用失败只降级，不吞掉零件主流程 |

## 三、契约

### 3.1 ERP 内网接口

`GET /erp/internal/item/by-code?code={code}`

```json
{"code": 0, "data": {"id": 9, "code": "ERP-001", "name": "六角螺栓", "status": "ENABLED"}}
```

- 物料不存在：`data` 为 `null`，HTTP 仍 200。

`GET /erp/internal/stock/by-code?itemCode={itemCode}`

```json
{"code": 0, "data": {
  "itemCode": "ERP-001", "itemName": "六角螺栓", "totalQuantity": 5,
  "warehouses": [{"warehouseId": 7, "warehouseCode": "WH-01", "warehouseName": "主仓", "quantity": 5}]
}}
```

- 无余额：`totalQuantity=0`、`warehouses=[]`。
- 仓库不存在时 `warehouseCode/warehouseName` 为空，数量保留。

### 3.2 PDM 回显接口

`GET /pdm/part/{id}/erp-stock`（权限 `sys:pdm:part:list`）

```json
{"partId": 1, "partCode": "P-001", "erpItemCode": "ERP-001", "itemExists": true,
 "itemName": "六角螺栓", "itemStatus": "ENABLED", "totalQuantity": 5,
 "warehouses": [{"warehouseId": 7, "warehouseCode": "WH-01", "warehouseName": "主仓", "quantity": 5}],
 "degraded": false, "message": null}
```

降级：`degraded=true`、`message="ERP 暂不可用，物料与库存信息未获取"`、`totalQuantity=0`、`warehouses=[]`。

## 四、验证

- ERP：`mvn -o -pl whale-backend/whale-apps/whale-erp test`（466 例全绿，含 ArchUnit）。
- PDM：`mvn -o -pl whale-backend/whale-apps/whale-pdm test`（210 例全绿，新增 ERP 回显 4 例）。
- 前端：`pnpm --filter whale-pdm lint`、`build`。
- 网关：确认 `/erp/internal/**` 仍在 internal-deny，不新增放行。

## 五、明确不做

- 不做库存预占/可用量计算（ERP 尚无预占模型）。
- 不做批量拉取与本地缓存同步（M1B-5 仅为只读回显）。
- 不在 ERP 侧扩展权限码。
