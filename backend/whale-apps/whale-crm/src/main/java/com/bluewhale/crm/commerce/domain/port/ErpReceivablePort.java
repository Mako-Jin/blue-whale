package com.bluewhale.crm.commerce.domain.port;

/**
 * ERP 应收只读端口。
 *
 * @author BlueWhale
 */
public interface ErpReceivablePort {

    /**
     * 按客户编码查询 ERP 未结应收与账龄。
     *
     * @param customerCode 客户编码
     * @return 应收汇总；客户不存在时返回空摘要
     */
    ErpReceivableSummary fetchByCustomerCode(String customerCode);
}
