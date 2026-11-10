package com.bluewhale.mes.equipment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 设备台账实体，对应表 {@code mes_eq_equipment}。
 *
 * <p>编码租户内唯一；状态由 {@link MesEquipmentStatusEvent} 驱动，台账只保留当前状态快照。
 * 故障（{@code FAULT}）设备在门禁中阻断开工。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_eq_equipment")
public class MesEquipment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 设备编码（租户内唯一） */
    private String equipmentCode;

    /** 设备名称 */
    private String equipmentName;

    /** 设备类型：MACHINE / ROBOT / AGV / INSPECTION / OTHER */
    private String equipmentType;

    /** 所属车间 ID */
    private Long workshopId;

    /** 所属车间编码快照 */
    private String workshopCode;

    /** 所属产线 ID */
    private Long lineId;

    /** 所属产线编码快照 */
    private String lineCode;

    /** 所属工作中心 ID */
    private Long workCenterId;

    /** 所属工作中心编码快照 */
    private String workCenterCode;

    /** 工位编码 */
    private String stationCode;

    /** 规格型号 */
    private String modelNo;

    /** 制造厂商 */
    private String manufacturer;

    /** 购入日期 */
    private LocalDate purchaseDate;

    /** 保养周期（天） */
    private Integer maintenanceCycleDays;

    /** 状态基线 */
    private String statusBaseline;

    /** 当前状态 */
    private String status;

    /** 是否启用：0 否 / 1 是 */
    private Integer enabled;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
