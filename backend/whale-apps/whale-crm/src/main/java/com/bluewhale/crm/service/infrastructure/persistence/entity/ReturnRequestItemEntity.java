package com.bluewhale.crm.service.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 退换货申请行实体，对应表 {@code crm_return_item}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_return_item")
public class ReturnRequestItemEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 申请 ID */
    private Long requestId;

    /** 行号 */
    private Integer lineNo;

    /** 来源订单行号 */
    private Integer orderLineNo;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 退货数量 */
    private BigDecimal quantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 行金额 */
    private BigDecimal amount;
}
