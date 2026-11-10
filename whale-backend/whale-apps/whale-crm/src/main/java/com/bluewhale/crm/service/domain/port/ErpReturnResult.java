package com.bluewhale.crm.service.domain.port;

import java.math.BigDecimal;

/**
 * ERP 销售退货结果摘要。
 *
 * @param erpReturnId ERP 销售退货 ID
 * @param erpReturnNo ERP 销售退货单号
 * @param status ERP 退货单状态
 * @param totalAmount 退货总额
 * @author BlueWhale
 */
public record ErpReturnResult(Long erpReturnId, String erpReturnNo, String status, BigDecimal totalAmount) {
}
