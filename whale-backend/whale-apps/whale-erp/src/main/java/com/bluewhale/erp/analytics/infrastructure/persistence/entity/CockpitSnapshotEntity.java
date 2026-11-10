package com.bluewhale.erp.analytics.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 驾驶舱快照实体，对应表 {@code erp_bi_cockpit_snapshot}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_bi_cockpit_snapshot")
public class CockpitSnapshotEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 快照编号 */
    private String snapshotNo;

    /** 统计期间 */
    private String periodCode;

    /** 统计截止日期 */
    private LocalDate statDate;

    /** 收入金额 */
    private BigDecimal revenueAmount;

    /** 成本金额 */
    private BigDecimal costAmount;

    /** 利润金额 */
    private BigDecimal profitAmount;

    /** 订单数 */
    private Integer orderCount;

    /** 未关闭预警数 */
    private Integer alertCount;

    /** 状态 */
    private String status;

    /** 刷新时间 */
    private LocalDateTime refreshedAt;

    /** 备注 */
    private String remark;
}
