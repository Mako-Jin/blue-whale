package com.bluewhale.oa.expense.domain.gateway;

import com.bluewhale.oa.expense.domain.model.ErpPayableRequest;

/**
 * ERP 应付单内网网关。
 *
 * @author BlueWhale
 */
public interface ErpPayableGateway {

    /**
     * 创建应付单（按 sourceType + sourceId 幂等）。
     *
     * @param request 应付单请求
     * @return ERP 应付单号
     */
    String createPayable(ErpPayableRequest request);
}
