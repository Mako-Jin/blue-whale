package com.bluewhale.mes.trace.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 批次谱系建链请求（父批次被消耗 → 产出子批次）
 *
 * @author BlueWhale
 */
@Data
public class MesTraceLinkRequest {

    /** 上级批次号（被消耗） */
    @NotBlank(message = "{validation.mes.mes-trace-link.parent-batch-no-required}")
    @Size(max = 64, message = "{validation.mes.mes-trace-link.parent-batch-no-size}")
    private String parentBatchNo;

    /** 下级批次号（产出） */
    @NotBlank(message = "{validation.mes.mes-trace-link.child-batch-no-required}")
    @Size(max = 64, message = "{validation.mes.mes-trace-link.child-batch-no-size}")
    private String childBatchNo;

    /** 消耗数量 */
    @NotNull(message = "{validation.mes.mes-trace-link.quantity-required}")
    @DecimalMin(value = "0.0001", message = "{validation.mes.mes-trace-link.quantity-min}")
    private BigDecimal quantity;

    /** 计量单位 */
    @Size(max = 16, message = "{validation.mes.mes-trace-link.uom-size}")
    private String uom;

    /** 关联工单 ID */
    private Long workOrderId;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-trace-link.remark-size}")
    private String remark;
}
