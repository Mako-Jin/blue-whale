package com.bluewhale.mes.equipment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备状态事件实体，对应表 {@code mes_eq_status_event}。
 *
 * <p>只追加不覆盖：每次状态变更插入一条事件，历史通过发生时间还原。
 * {@code sourceEventId} 作为采集/网关事件的幂等键。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_eq_status_event")
public class MesEquipmentStatusEvent extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 设备 ID */
    private Long equipmentId;

    /** 设备编码快照 */
    private String equipmentCode;

    /** 变更前状态 */
    private String fromStatus;

    /** 变更后状态 */
    private String toStatus;

    /** 来源：MANUAL / COLLECT / WORK_ORDER */
    private String eventType;

    /** 发生时间 */
    private LocalDateTime occurredAt;

    /** 上一状态持续秒数 */
    private Long durationSeconds;

    /** 操作人 */
    private String operator;

    /** 来源事件幂等键 */
    private String sourceEventId;

    /** 备注 */
    private String remark;
}
