package com.bluewhale.erp.analytics.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销售事实只读实体，对应只读视图 {@code v_erp_bi_sales_fact}。
 *
 * @author BlueWhale
 */
@Data
@TableName("v_erp_bi_sales_fact")
public class SalesFactEntity {

    /** 订单号 */
    private String docNo;

    /** 单据日期 */
    private LocalDate docDate;

    /** 客户ID */
    private Long customerId;

    /** 客户名称 */
    private String customerName;

    /** 物料ID */
    private Long itemId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 仓库ID */
    private Long warehouseId;

    /** 订单数量 */
    private BigDecimal quantity;

    /** 已发数量 */
    private BigDecimal shippedQuantity;

    /** 行金额 */
    private BigDecimal amount;
}
