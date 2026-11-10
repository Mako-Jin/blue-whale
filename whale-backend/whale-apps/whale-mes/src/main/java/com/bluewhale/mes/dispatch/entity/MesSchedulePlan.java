package com.bluewhale.mes.dispatch.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 排程计划实体，对应表 {@code mes_dp_schedule_plan}。
 *
 * <p>MVP 仅记录排程结果与锁定状态，不做 APS 优化；同一工作中心同一时段超产能为软约束，允许人工覆盖并留痕。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_dp_schedule_plan")
public class MesSchedulePlan extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 排程计划号（租户内唯一） */
    private String planNo;

    /** 排程日期 */
    private LocalDate planDate;

    /** 工单 ID */
    private Long workOrderId;

    /** 工单编码快照 */
    private String workOrderCode;

    /** 状态：DRAFT/LOCKED/CANCELLED */
    private String status;

    /** 排程人 */
    private String plannedBy;

    /** 备注 */
    private String remark;
}
