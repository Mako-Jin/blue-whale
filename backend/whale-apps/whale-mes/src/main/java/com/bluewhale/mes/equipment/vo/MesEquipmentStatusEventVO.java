package com.bluewhale.mes.equipment.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备状态事件视图
 *
 * @author BlueWhale
 */
@Data
public class MesEquipmentStatusEventVO {

    /** 事件 ID */
    private Long id;

    /** 设备 ID */
    private Long equipmentId;

    /** 设备编码 */
    private String equipmentCode;

    /** 变更前状态 */
    private String fromStatus;

    /** 变更后状态 */
    private String toStatus;

    /** 来源类型 */
    private String eventType;

    /** 发生时间 */
    private LocalDateTime occurredAt;

    /** 上一状态持续秒数 */
    private Long durationSeconds;

    /** 操作人 */
    private String operator;

    /** 备注 */
    private String remark;
}
