package com.bluewhale.mes.trace.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 线边叫料/补货请求
 *
 * @author BlueWhale
 */
@Data
public class MesReplenishmentRequest {

    /** 请求号（幂等键；空则自动生成） */
    @Size(max = 64, message = "请求号长度不能超过 64")
    private String requestNo;

    /** 产线 ID */
    @NotNull(message = "产线不能为空")
    private Long lineId;

    /** 产线编码快照 */
    @NotBlank(message = "产线编码不能为空")
    @Size(max = 64, message = "产线编码长度不能超过 64")
    private String lineCode;

    /** 线边仓编码 */
    @NotBlank(message = "线边仓编码不能为空")
    @Size(max = 64, message = "线边仓编码长度不能超过 64")
    private String warehouseCode;

    /** 物料编码 */
    @NotBlank(message = "物料编码不能为空")
    @Size(max = 64, message = "物料编码长度不能超过 64")
    private String itemCode;

    /** 物料名称 */
    @Size(max = 128, message = "物料名称长度不能超过 128")
    private String itemName;

    /** 叫料数量 */
    @NotNull(message = "叫料数量不能为空")
    @DecimalMin(value = "0.0001", message = "叫料数量必须大于 0")
    private BigDecimal quantity;

    /** 计量单位 */
    @Size(max = 16, message = "计量单位长度不能超过 16")
    private String uom;

    /** 叫料原因 */
    @Size(max = 255, message = "叫料原因长度不能超过 255")
    private String reason;

    /** 叫料人 */
    @Size(max = 64, message = "叫料人长度不能超过 64")
    private String requestedBy;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
