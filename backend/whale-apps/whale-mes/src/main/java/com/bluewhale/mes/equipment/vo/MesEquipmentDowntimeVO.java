package com.bluewhale.mes.equipment.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备停机记录视图
 *
 * @author BlueWhale
 */
@Data
public class MesEquipmentDowntimeVO {

    /** 停机单号 */
    private String downtimeNo;

    /** 设备 ID */
    private Long equipmentId;

    /** 设备编码 */
    private String equipmentCode;

    /** 停机原因代码 */
    private String reasonCode;

    /** 停机原因名称 */
    private String reasonName;

    /** 是否计划停机 */
    private Integer plannedFlag;

    /** 停机开始时间 */
    private LocalDateTime startAt;

    /** 停机结束时间 */
    private LocalDateTime endAt;

    /** 停机时长（分钟） */
    private BigDecimal durationMinutes;

    /** 备注 */
    private String remark;
}
