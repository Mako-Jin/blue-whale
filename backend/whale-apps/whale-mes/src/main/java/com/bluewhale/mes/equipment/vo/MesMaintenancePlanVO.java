package com.bluewhale.mes.equipment.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 保养计划视图
 *
 * @author BlueWhale
 */
@Data
public class MesMaintenancePlanVO {

    /** 计划 ID */
    private Long id;

    /** 计划编号 */
    private String planNo;

    /** 设备 ID */
    private Long equipmentId;

    /** 设备编码 */
    private String equipmentCode;

    /** 保养类型 */
    private String maintenanceType;

    /** 保养周期（天） */
    private Integer cycleDays;

    /** 保养项清单 */
    private String checklist;

    /** 上次完成时间 */
    private LocalDateTime lastDoneAt;

    /** 下次到期时间 */
    private LocalDateTime nextDueAt;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
