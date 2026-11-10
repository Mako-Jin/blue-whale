package com.bluewhale.crm.service.domain.port;

/**
 * ERP 销售退货内网端口。
 *
 * @author BlueWhale
 */
public interface ErpReturnPort {

    /**
     * 按 {@code crmReturnNo} 幂等创建并过账 ERP 销售退货。
     *
     * @param command 下发命令
     * @return ERP 退货摘要
     */
    ErpReturnResult create(ErpReturnCommand command);

    /**
     * 按 {@code crmReturnNo} 查询 ERP 销售退货。
     *
     * @param crmReturnNo CRM 退换货申请单号
     * @return ERP 退货摘要；不存在返回 {@code null}
     */
    ErpReturnResult findByCrmReturnNo(String crmReturnNo);
}
