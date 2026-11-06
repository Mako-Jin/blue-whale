package com.bluewhale.pdm.integration.client;

/**
 * ERP 内网客户端
 *
 * <p>只调用网关屏蔽的 {@code /erp/internal/**}；按物料编码幂等 upsert。</p>
 *
 * @author BlueWhale
 */
public interface PdmIntErpClient {

    /**
     * 按物料编码幂等同步物料
     *
     * @param tenantId 租户 ID
     * @param request  物料内容
     * @return 同步结果
     */
    PdmIntErpItemResult upsertItem(Long tenantId, PdmIntErpItemRequest request);

    /**
     * 按物料编码只读查询 ERP 物料
     *
     * @param tenantId 租户 ID
     * @param code     物料编码
     * @return 物料信息；ERP 不存在时 exists=false
     */
    PdmIntErpItemInfo getItem(Long tenantId, String code);

    /**
     * 按物料编码只读查询 ERP 库存
     *
     * @param tenantId 租户 ID
     * @param itemCode 物料编码
     * @return 库存信息
     */
    PdmIntErpStockInfo getStock(Long tenantId, String itemCode);
}
