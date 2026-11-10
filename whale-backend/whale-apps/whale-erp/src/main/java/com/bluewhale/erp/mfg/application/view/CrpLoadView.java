package com.bluewhale.erp.mfg.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * CRP 工作中心日负荷视图。
 *
 * @author BlueWhale
 */
@Data
public class CrpLoadView {

    /** 工作中心编码 */
    private String workCenterCode;

    /** 日期 */
    private LocalDate date;

    /** 可用产能工时 */
    private BigDecimal capacityHours;

    /** 负荷工时 */
    private BigDecimal loadHours;

    /** 利用率百分比 */
    private BigDecimal utilizationPercent;

    /** 是否超载 */
    private boolean overload;
}
