package com.bluewhale.mes.equipment.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 保养工单视图
 *
 * @author BlueWhale
 */
@Data
public class MesMaintenanceOrderVO {

    /** 工单 ID */
    private Long id;

    /** 工单号 */
    private String orderNo;

    /** 计划 ID */
    private Long planId;

    /** 计划编号 */
    private String planNo;

    /** 设备 ID */
    private Long equipmentId;

    /** 设备编码 */
    private String equipmentCode;

    /** 保养类型 */
    private String maintenanceType;

    /** 保养项清单 */
    private String checklist;

    /** 应完成时间 */
    private LocalDateTime dueAt;

    /** 状态 */
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

    /** 保养项结论 */
    private String checklistResult;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
