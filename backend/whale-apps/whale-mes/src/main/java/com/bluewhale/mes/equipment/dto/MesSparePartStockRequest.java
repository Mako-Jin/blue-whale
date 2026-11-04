package com.bluewhale.mes.equipment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 备件出入库请求
 *
 * @author BlueWhale
 */
@Data
public class MesSparePartStockRequest {

    /** 备件编码 */
    @NotBlank(message = "备件编码不能为空")
    @Size(max = 64, message = "备件编码长度不能超过 64")
    private String partCode;

    /** 仓库编码（空则 DEFAULT） */
    @Size(max = 64, message = "仓库编码长度不能超过 64")
    private String warehouseCode;

    /** 数量（正数） */
    @NotNull(message = "数量不能为空")
    @DecimalMin(value = "0.0001", message = "数量必须大于 0")
    private BigDecimal quantity;

    /** 关联单号（维修工单/采购单） */
    @Size(max = 64, message = "关联单号长度不能超过 64")
    private String refNo;

    /** 操作人 */
    @Size(max = 64, message = "操作人长度不能超过 64")
    private String operator;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
