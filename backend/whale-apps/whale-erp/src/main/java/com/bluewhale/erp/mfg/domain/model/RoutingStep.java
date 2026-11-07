package com.bluewhale.erp.mfg.domain.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 工艺路线工序快照。
 *
 * @author BlueWhale
 */
@Data
public class RoutingStep {

    /** 工序序号 */
    private Integer stepNo;

    /** 工序名称 */
    private String name;

    /** 工作中心编码 */
    private String workCenterCode;

    /** 单件标准工时（小时） */
    private BigDecimal standardHours;

    /** 准备工时（小时） */
    private BigDecimal setupHours;
}
