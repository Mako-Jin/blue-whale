package com.bluewhale.mes.equipment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 设备台账保存请求（新增/编辑）
 *
 * @author BlueWhale
 */
@Data
public class MesEquipmentSaveRequest {

    /** 设备 ID（编辑时必填） */
    private Long id;

    /** 设备编码（空则编辑时保持不变） */
    @Size(max = 64, message = "{validation.mes.mes-equipment.equipment-code-size}")
    private String equipmentCode;

    /** 设备名称 */
    @NotBlank(message = "{validation.mes.mes-equipment.equipment-name-required}")
    @Size(max = 128, message = "{validation.mes.mes-equipment.equipment-name-size}")
    private String equipmentName;

    /** 设备类型 */
    @Pattern(regexp = "MACHINE|ROBOT|AGV|INSPECTION|OTHER", message = "{validation.mes.mes-equipment.equipment-type-pattern}")
    private String equipmentType;

    /** 所属车间 ID */
    private Long workshopId;

    /** 所属车间编码 */
    @Size(max = 64, message = "{validation.mes.mes-equipment.workshop-code-size}")
    private String workshopCode;

    /** 所属产线 ID */
    private Long lineId;

    /** 所属产线编码 */
    @Size(max = 64, message = "{validation.mes.mes-equipment.line-code-size}")
    private String lineCode;

    /** 所属工作中心 ID */
    private Long workCenterId;

    /** 所属工作中心编码 */
    @Size(max = 64, message = "{validation.mes.mes-equipment.work-center-code-size}")
    private String workCenterCode;

    /** 工位编码 */
    @Size(max = 64, message = "{validation.mes.mes-equipment.station-code-size}")
    private String stationCode;

    /** 规格型号 */
    @Size(max = 64, message = "{validation.mes.mes-equipment.model-no-size}")
    private String modelNo;

    /** 制造厂商 */
    @Size(max = 128, message = "{validation.mes.mes-equipment.manufacturer-size}")
    private String manufacturer;

    /** 购入日期 */
    private LocalDate purchaseDate;

    /** 保养周期（天） */
    @Min(value = 0, message = "{validation.mes.mes-equipment.maintenance-cycle-days-min}")
    private Integer maintenanceCycleDays;

    /** 状态基线 */
    @Pattern(regexp = "RUNNING|IDLE|FAULT|MAINTENANCE|OFFLINE", message = "{validation.mes.mes-equipment.status-baseline-pattern}")
    private String statusBaseline;

    /** 初始状态（仅新增时生效） */
    @Pattern(regexp = "RUNNING|IDLE|FAULT|MAINTENANCE|OFFLINE", message = "{validation.mes.mes-equipment.status-pattern}")
    private String status;

    /** 是否启用：0 否 / 1 是 */
    private Integer enabled;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-equipment.remark-size}")
    private String remark;
}
