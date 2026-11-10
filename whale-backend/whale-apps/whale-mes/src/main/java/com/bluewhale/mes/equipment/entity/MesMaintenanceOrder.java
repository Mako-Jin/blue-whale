package com.bluewhale.mes.equipment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 保养工单实体，对应表 {@code mes_eq_maintenance_order}。
 *
 * <p>状态机 {@code PLANNED → DUE → IN_PROGRESS → COMPLETED → ACCEPTED}，旁支 {@code OVERDUE}。
 * 完工必须覆盖计划全部保养项；验收后才能回写计划的 {@code lastDoneAt/nextDueAt}。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_eq_maintenance_order")
public class MesMaintenanceOrder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 保养工单号（租户内唯一） */
    private String orderNo;

    /** 保养计划 ID */
    private Long planId;

    /** 保养计划编号快照 */
    private String planNo;

    /** 设备 ID */
    private Long equipmentId;

    /** 设备编码快照 */
    private String equipmentCode;

    /** 保养类型快照 */
    private String maintenanceType;

    /** 保养项清单快照（分号分隔） */
    private String checklist;

    /** 应完成时间 */
    private LocalDateTime dueAt;

    /** 状态：PLANNED / DUE / IN_PROGRESS / COMPLETED / ACCEPTED / OVERDUE */
    private String status;

    /** 执行人 */
    private String assignee;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 完工时间 */
    private LocalDateTime completedAt;

    /** 完工登记人 */
    private String completedBy;

    /** 验收人 */
    private String acceptedBy;

    /** 验收时间 */
    private LocalDateTime acceptedAt;

    /** 验收结论 */
    private String conclusion;

    /** 保养项结论（项名=结论，分号分隔） */
    private String checklistResult;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
