package com.bluewhale.crm.sales.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预测调整记录实体，对应表 {@code crm_forecast_adjustment}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_forecast_adjustment")
public class ForecastAdjustmentEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 预测快照 ID */
    private Long snapshotId;

    /** 调整字段 */
    private String fieldCode;

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
