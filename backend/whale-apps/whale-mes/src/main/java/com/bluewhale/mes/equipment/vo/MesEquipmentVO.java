package com.bluewhale.mes.equipment.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备台账视图
 *
 * @author BlueWhale
 */
@Data
public class MesEquipmentVO {

    /** 设备 ID */
    private Long id;

    /** 设备编码 */
    private String equipmentCode;

    /** 设备名称 */
    private String equipmentName;

    /** 设备类型 */
    private String equipmentType;

    /** 所属车间 ID */
    private Long workshopId;

    /** 所属车间编码 */
    private String workshopCode;

    /** 所属产线 ID */
    private Long lineId;

    /** 所属产线编码 */
    private String lineCode;

    /** 所属工作中心 ID */
    private Long workCenterId;

    /** 所属工作中心编码 */
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

    /** 是否启用 */
    private Integer enabled;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
