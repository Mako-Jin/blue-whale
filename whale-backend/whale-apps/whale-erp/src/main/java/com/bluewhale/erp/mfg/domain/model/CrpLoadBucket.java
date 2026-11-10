package com.bluewhale.erp.mfg.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * CRP 工作中心日负荷。
 *
 * @author BlueWhale
 */
@Data
public class CrpLoadBucket {

    /** 工作中心编码 */
    private String workCenterCode;

    /** 日期 */
    private LocalDate date;

    /** 可用产能工时 */
    private BigDecimal capacityHours;

    /** 负荷工时（无限负荷口径） */
    private BigDecimal loadHours;

    /** 利用率百分比 */
    private BigDecimal utilizationPercent;

    /** 是否超载 */
    private boolean overload;
}
