package com.bluewhale.mes.equipment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备停机记录实体，对应表 {@code mes_eq_downtime}。
 *
 * <p>故障状态开始即开单，恢复（非 FAULT）时关闭并回填时长；计划停机以 {@code plannedFlag} 区分。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_eq_downtime")
public class MesEquipmentDowntime extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 停机单号（租户内唯一） */
    private String downtimeNo;

    /** 设备 ID */
    private Long equipmentId;

    /** 设备编码快照 */
    private String equipmentCode;

    /** 触发停机的状态事件 ID */
    private Long statusEventId;

    /** 停机原因代码 */
    private String reasonCode;

    /** 停机原因名称 */
    private String reasonName;

    /** 是否计划停机：0 否 / 1 是 */
    private Integer plannedFlag;

    /** 停机开始时间 */
    private LocalDateTime startAt;

    /** 停机结束时间 */
    private LocalDateTime endAt;

    /** 停机时长（分钟） */
    private BigDecimal durationMinutes;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
