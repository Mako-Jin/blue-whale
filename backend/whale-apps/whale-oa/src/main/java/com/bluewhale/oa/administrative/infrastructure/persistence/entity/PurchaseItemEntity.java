package com.bluewhale.oa.administrative.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 采购明细实体，对应表 {@code oa_purchase_item}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_purchase_item")
public class PurchaseItemEntity extends BaseEntity {

    private Long purchaseId;

    private Integer lineNo;

    private String itemCode;

    private String itemName;

    private BigDecimal quantity;

    private BigDecimal unitPrice;

    private BigDecimal amount;

    private String supplierAdvice;
}
