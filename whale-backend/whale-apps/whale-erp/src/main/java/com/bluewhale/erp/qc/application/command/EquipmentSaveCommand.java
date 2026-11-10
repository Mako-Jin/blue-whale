package com.bluewhale.erp.qc.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * 设备新增/编辑命令。
 *
 * @author BlueWhale
 */
@Data
public class EquipmentSaveCommand {

    /** 设备编码 */
    @NotBlank
    private String equipmentNo;

    /** 设备名称 */
    @NotBlank
    private String name;

    /** 设备类型 */
    private String equipmentType;

    /** 安装位置 */
    private String location;

    /** 状态：RUNNING / IDLE / DOWN / RETIRED */
    private String status;

    /** 购置日期 */
    private LocalDate purchaseDate;

    /** 备注 */
    private String remark;
}
