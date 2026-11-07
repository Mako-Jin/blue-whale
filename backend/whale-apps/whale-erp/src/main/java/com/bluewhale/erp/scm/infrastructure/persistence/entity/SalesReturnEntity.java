package com.bluewhale.erp.scm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销售退货单头实体，对应表 {@code erp_scm_sales_return}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_scm_sales_return")
public class SalesReturnEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 退货单号 */
    private String docNo;

    /** 源销售订单主键 */
    private Long salesOrderId;

    /** 客户主键 */
    private Long customerId;

    /** 客户名称快照 */
    private String customerName;

    /** 退货日期 */
    private LocalDate docDate;

    /** 退货入库仓库 */
    private Long warehouseId;

    /** 状态 */
    private String status;

    /** 币种 */
    private String currency;

    /** 退货总额 */
    private BigDecimal totalAmount;

    /** 备注 */
    private String remark;

    /** CRM 退换货申请单号（幂等键） */
    private String crmReturnNo;

    /** 来源系统类型 */
    private String sourceType;

    /** 来源单号 */
    private String sourceId;
}
