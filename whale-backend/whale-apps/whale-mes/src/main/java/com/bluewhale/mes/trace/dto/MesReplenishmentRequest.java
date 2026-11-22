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
    @Size(max = 64, message = "{validation.mes.mes-replenishment.request-no-size}")
    private String requestNo;

    /** 产线 ID */
    @NotNull(message = "{validation.mes.mes-replenishment.line-id-required}")
    private Long lineId;

    /** 产线编码快照 */
    @NotBlank(message = "{validation.mes.mes-replenishment.line-code-required}")
    @Size(max = 64, message = "{validation.mes.mes-replenishment.line-code-size}")
    private String lineCode;

    /** 线边仓编码 */
    @NotBlank(message = "{validation.mes.mes-replenishment.warehouse-code-required}")
    @Size(max = 64, message = "{validation.mes.mes-replenishment.warehouse-code-size}")
    private String warehouseCode;

    /** 物料编码 */
    @NotBlank(message = "{validation.mes.mes-replenishment.item-code-required}")
    @Size(max = 64, message = "{validation.mes.mes-replenishment.item-code-size}")
    private String itemCode;

    /** 物料名称 */
    @Size(max = 128, message = "{validation.mes.mes-replenishment.item-name-size}")
    private String itemName;

    /** 叫料数量 */
    @NotNull(message = "{validation.mes.mes-replenishment.quantity-required}")
    @DecimalMin(value = "0.0001", message = "{validation.mes.mes-replenishment.quantity-min}")
    private BigDecimal quantity;

    /** 计量单位 */
    @Size(max = 16, message = "{validation.mes.mes-replenishment.uom-size}")
    private String uom;

    /** 叫料原因 */
    @Size(max = 255, message = "{validation.mes.mes-replenishment.reason-size}")
    private String reason;

    /** 叫料人 */
    @Size(max = 64, message = "{validation.mes.mes-replenishment.requested-by-size}")
    private String requestedBy;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-replenishment.remark-size}")
    private String remark;
}
