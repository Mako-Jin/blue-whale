package com.bluewhale.crm.sales.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预测手工调整记录。
 *
 * @author BlueWhale
 */
@Data
public class ForecastAdjustment {

    /** 主键 */
    private Long id;

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

    /** 创建时间 */
    private LocalDateTime createTime;
}
