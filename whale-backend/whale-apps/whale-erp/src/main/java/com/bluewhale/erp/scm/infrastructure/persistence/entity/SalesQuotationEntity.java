package com.bluewhale.erp.scm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销售报价单头实体，对应表 {@code erp_scm_sales_quotation}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_scm_sales_quotation")
public class SalesQuotationEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 报价单号 */
    private String docNo;

    /** 客户主键 */
    private Long customerId;

    /** 客户名称快照 */
    private String customerName;

    /** 报价日期 */
    private LocalDate docDate;

    /** 报价有效期至 */
    private LocalDate validUntil;

    /** 发货仓库 */
    private Long warehouseId;

    /** 状态 */
    private String status;

    /** 币种 */
    private String currency;

    /** 报价总额 */
    private BigDecimal totalAmount;

    /** 转单生成的销售订单号 */
    private String convertedOrderNo;

    /** 备注 */
    private String remark;
}
