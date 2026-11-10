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
    @Size(max = 64, message = "设备编码长度不能超过 64")
    private String equipmentCode;

    /** 设备名称 */
    @NotBlank(message = "设备名称不能为空")
    @Size(max = 128, message = "设备名称长度不能超过 128")
    private String equipmentName;

    /** 设备类型 */
    @Pattern(regexp = "MACHINE|ROBOT|AGV|INSPECTION|OTHER", message = "设备类型不合法")
    private String equipmentType;

    /** 所属车间 ID */
    private Long workshopId;

    /** 所属车间编码 */
    @Size(max = 64, message = "车间编码长度不能超过 64")
    private String workshopCode;

    /** 所属产线 ID */
    private Long lineId;

    /** 所属产线编码 */
    @Size(max = 64, message = "产线编码长度不能超过 64")
    private String lineCode;

    /** 所属工作中心 ID */
    private Long workCenterId;

    /** 所属工作中心编码 */
    @Size(max = 64, message = "工作中心编码长度不能超过 64")
    private String workCenterCode;

    /** 工位编码 */
    @Size(max = 64, message = "工位编码长度不能超过 64")
    private String stationCode;

    /** 规格型号 */
    @Size(max = 64, message = "规格型号长度不能超过 64")
    private String modelNo;

    /** 制造厂商 */
    @Size(max = 128, message = "制造厂商长度不能超过 128")
    private String manufacturer;

    /** 购入日期 */
    private LocalDate purchaseDate;

    /** 保养周期（天） */
    @Min(value = 0, message = "保养周期不能为负")
    private Integer maintenanceCycleDays;

    /** 状态基线 */
    @Pattern(regexp = "RUNNING|IDLE|FAULT|MAINTENANCE|OFFLINE", message = "状态基线不合法")
    private String statusBaseline;

    /** 初始状态（仅新增时生效） */
    @Pattern(regexp = "RUNNING|IDLE|FAULT|MAINTENANCE|OFFLINE", message = "初始状态不合法")
    private String status;

    /** 是否启用：0 否 / 1 是 */
    private Integer enabled;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
