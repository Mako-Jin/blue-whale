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
    @Size(max = 64, message = "{validation.mes.mes-spare-part.part-code-size}")
    private String partCode;

    /** 备件名称 */
    @NotBlank(message = "{validation.mes.mes-spare-part.part-name-required}")
    @Size(max = 128, message = "{validation.mes.mes-spare-part.part-name-size}")
    private String partName;

    /** 规格型号 */
    @Size(max = 128, message = "{validation.mes.mes-spare-part.spec-size}")
    private String spec;

    /** 计量单位 */
    @Size(max = 16, message = "{validation.mes.mes-spare-part.uom-size}")
    private String uom;

    /** 安全库存 */
    @Min(value = 0, message = "{validation.mes.mes-spare-part.safety-stock-min}")
    private BigDecimal safetyStock;

    /** 状态：ENABLED / DISABLED */
    @Pattern(regexp = "ENABLED|DISABLED", message = "{validation.mes.mes-spare-part.status-pattern}")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-spare-part.remark-size}")
    private String remark;
}
