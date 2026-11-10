package com.bluewhale.mes.dispatch.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 排程时段实体，对应表 {@code mes_dp_schedule_slot}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_dp_schedule_slot")
public class MesScheduleSlot extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 排程计划 ID */
    private Long planId;

    /** 任务 ID */
    private Long taskId;

    /** 工作中心 ID */
    private Long workCenterId;

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
