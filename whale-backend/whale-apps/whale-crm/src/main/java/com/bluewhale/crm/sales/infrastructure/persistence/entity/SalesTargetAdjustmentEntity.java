package com.bluewhale.crm.sales.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售目标调整记录实体，对应表 {@code crm_sales_target_adjustment}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_sales_target_adjustment")
public class SalesTargetAdjustmentEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 销售目标 ID */
    private Long targetId;

    /** 调整前金额 */
    private BigDecimal beforeAmount;

    /** 调整后金额 */
    private BigDecimal afterAmount;

    /** 调整原因 */
    private String reason;

    /** 操作人 */
    private Long operatorId;

    /** 调整时间 */
    private LocalDateTime adjustedAt;
}
