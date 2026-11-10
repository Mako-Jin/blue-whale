package com.bluewhale.crm.commerce.domain.port;

import java.math.BigDecimal;

/**
 * ERP 销售订单履约摘要。
 *
 * @param erpOrderId ERP 销售订单 ID
 * @param erpOrderNo ERP 销售订单号
 * @param status ERP 履约状态
 * @param totalAmount 订单总额
 * @param shippedAmount 已发货金额
 * @author BlueWhale
 */
public record ErpOrderResult(Long erpOrderId, String erpOrderNo, String status,
                             BigDecimal totalAmount, BigDecimal shippedAmount) {
}
