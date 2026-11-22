package com.bluewhale.mes.equipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备状态变更请求
 *
 * @author BlueWhale
 */
@Data
public class MesEquipmentStatusChangeRequest {

    /** 目标状态：RUNNING / IDLE / FAULT / MAINTENANCE / OFFLINE */
    @NotBlank(message = "{validation.mes.mes-equipment-status-change.to-status-required}")
    @Pattern(regexp = "RUNNING|IDLE|FAULT|MAINTENANCE|OFFLINE", message = "{validation.mes.mes-equipment-status-change.to-status-pattern}")
    private String toStatus;

    /** 来源：MANUAL / COLLECT / WORK_ORDER */
    @Pattern(regexp = "MANUAL|COLLECT|WORK_ORDER", message = "{validation.mes.mes-equipment-status-change.event-type-pattern}")
    private String eventType;

    /** 发生时间（空则当前时间） */
    private LocalDateTime occurredAt;

    /** 操作人 */
    @Size(max = 64, message = "{validation.mes.mes-equipment-status-change.operator-size}")
    private String operator;

    /** 停机原因代码（转为 FAULT 时可选） */
    @Size(max = 64, message = "{validation.mes.mes-equipment-status-change.reason-code-size}")
    private String reasonCode;

    /** 停机原因名称 */
    @Size(max = 128, message = "{validation.mes.mes-equipment-status-change.reason-name-size}")
    private String reasonName;

    /** 来源事件幂等键 */
    @Size(max = 64, message = "{validation.mes.mes-equipment-status-change.source-event-id-size}")
    private String sourceEventId;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-equipment-status-change.remark-size}")
    private String remark;
}
