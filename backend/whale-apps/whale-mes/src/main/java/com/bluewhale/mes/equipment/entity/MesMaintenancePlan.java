package com.bluewhale.mes.equipment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 保养计划实体，对应表 {@code mes_eq_maintenance_plan}。
 *
 * <p>{@code checklist} 以分号分隔保养项；生成保养工单时快照到工单，完工必须逐项给出结论。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_eq_maintenance_plan")
public class MesMaintenancePlan extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 保养计划编号（租户内唯一） */
    private String planNo;

    /** 设备 ID */
    private Long equipmentId;

    /** 设备编码快照 */
    private String equipmentCode;

    /** 保养类型：ROUTINE / INSPECTION / OVERHAUL */
    private String maintenanceType;

    /** 保养周期（天） */
    private Integer cycleDays;

    /** 保养项清单（分号分隔） */
    private String checklist;

    /** 上次完成时间 */
    private LocalDateTime lastDoneAt;

    /** 下次到期时间 */
    private LocalDateTime nextDueAt;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
