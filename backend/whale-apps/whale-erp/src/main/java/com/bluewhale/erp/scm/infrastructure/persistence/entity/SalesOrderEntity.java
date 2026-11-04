package com.bluewhale.erp.scm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销售订单头实体，对应表 {@code erp_scm_sales_order}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_scm_sales_order")
public class SalesOrderEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 订单号 */
    private String docNo;

    /** 客户 ID */
    private Long customerId;

    /** 客户名称快照 */
    private String customerName;

    /** 单据日期 */
    private LocalDate docDate;

    /** 计划发货日期 */
    private LocalDate deliveryDate;

    /** 发货仓库 ID */
    private Long warehouseId;

    /** 状态 */
    private String status;

    /** 币种 */
    private String currency;

    /** 订单总额 */
    private BigDecimal totalAmount;

    /** 已发货金额 */
    private BigDecimal shippedAmount;

    /** 备注 */
    private String remark;
}
