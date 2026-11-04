package com.bluewhale.mes.equipment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 备件主档保存请求
 *
 * @author BlueWhale
 */
@Data
public class MesSparePartSaveRequest {

    /** 备件 ID（编辑时必填） */
    private Long id;

    /** 备件编码（空则编辑时保持不变） */
    @Size(max = 64, message = "备件编码长度不能超过 64")
    private String partCode;

    /** 备件名称 */
    @NotBlank(message = "备件名称不能为空")
    @Size(max = 128, message = "备件名称长度不能超过 128")
    private String partName;

    /** 规格型号 */
    @Size(max = 128, message = "规格型号长度不能超过 128")
    private String spec;

    /** 计量单位 */
    @Size(max = 16, message = "计量单位长度不能超过 16")
    private String uom;

    /** 安全库存 */
    @Min(value = 0, message = "安全库存不能为负")
    private BigDecimal safetyStock;

    /** 状态：ENABLED / DISABLED */
    @Pattern(regexp = "ENABLED|DISABLED", message = "状态仅支持 ENABLED / DISABLED")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
