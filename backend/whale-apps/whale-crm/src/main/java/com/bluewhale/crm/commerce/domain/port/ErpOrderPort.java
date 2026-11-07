package com.bluewhale.crm.commerce.domain.port;

/**
 * ERP 销售订单内网端口。
 *
 * @author BlueWhale
 */
public interface ErpOrderPort {

    /**
     * 按 {@code crmOrderNo} 幂等创建 ERP 销售订单。
     *
     * @param command 下发命令
     * @return ERP 履约摘要
     */
    ErpOrderResult create(ErpOrderCommand command);

    /**
     * 按 {@code crmOrderNo} 查询 ERP 销售订单。
     *
     * @param crmOrderNo CRM 商务订单号
     * @return ERP 履约摘要；不存在返回 {@code null}
     */
    ErpOrderResult findByCrmOrderNo(String crmOrderNo);

    /**
     * 按 ERP 订单号取消销售订单（重复取消幂等，已发货拒绝）。
     *
     * @param erpOrderNo ERP 销售订单号
     */
    void cancel(String erpOrderNo);
}
