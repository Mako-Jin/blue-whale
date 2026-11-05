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
}
