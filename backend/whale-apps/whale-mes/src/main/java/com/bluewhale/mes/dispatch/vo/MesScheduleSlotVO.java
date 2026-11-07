package com.bluewhale.mes.dispatch.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 排程时段视图对象。
 *
 * @author BlueWhale
 */
@Data
public class MesScheduleSlotVO {

    /** 主键 ID */
    private Long id;

    /** 排程计划 ID */
    private Long planId;

    /** 任务 ID */
    private Long taskId;

    /** 任务编号 */
    private String taskNo;

    /** 工序编码 */
    private String operationCode;

    /** 工序名称 */
    private String operationName;

    /** 工作中心 ID */
    private Long workCenterId;

    /** 工作中心编码 */
    private String workCenterCode;

    /** 班次 ID */
    private Long shiftId;

    /** 排程日期 */
    private LocalDate scheduleDate;

    /** 班次产能 */
    private BigDecimal capacityQuantity;

    /** 已分配数量 */
    private BigDecimal allocatedQuantity;

    /** 状态：RESERVED/RELEASED */
    private String status;
}
