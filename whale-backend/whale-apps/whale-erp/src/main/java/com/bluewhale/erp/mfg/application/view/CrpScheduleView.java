package com.bluewhale.erp.mfg.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * CRP 工序排程视图。
 *
 * @author BlueWhale
 */
@Data
public class CrpScheduleView {

    /** 生产订单号 */
    private String orderNo;

    /** 产出物料编码 */
    private String itemCode;

    /** 工序序号 */
    private Integer stepNo;

    /** 工序名称 */
    private String name;

    /** 工作中心编码 */
    private String workCenterCode;

    /** 待加工数量 */
    private BigDecimal quantity;

    /** 工序工时 */
    private BigDecimal hours;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 工序状态 */
    private String status;

    /** 是否晚于订单计划完工日 */
    private boolean delayed;
}
