package com.bluewhale.oa.administrative.domain.gateway;

import com.bluewhale.oa.administrative.domain.model.ErpPurchaseOrderRequest;

/**
 * ERP 采购订单网关。
 *
 * @author BlueWhale
 */
public interface ErpPurchaseGateway {

    /**
     * 创建 ERP 采购订单草稿（sourceType + sourceId 幂等）。
     *
     * @param request 采购订单请求
     * @return ERP 采购订单号
     */
    String createPurchaseOrderDraft(ErpPurchaseOrderRequest request);
}
