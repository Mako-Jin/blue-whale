package com.bluewhale.mes.trace.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 条码生成请求
 *
 * @author BlueWhale
 */
@Data
public class MesBarcodeGenerateRequest {

    /** 条码规则 ID */
    @NotNull(message = "{validation.mes.mes-barcode-generate.rule-id-required}")
    private Long ruleId;

    /** 绑定批次 ID */
    @NotNull(message = "{validation.mes.mes-barcode-generate.batch-id-required}")
    private Long batchId;

    /** 生成数量 */
    @NotNull(message = "{validation.mes.mes-barcode-generate.quantity-required}")
    @Min(value = 1, message = "{validation.mes.mes-barcode-generate.quantity-min}")
    @Max(value = 1000, message = "{validation.mes.mes-barcode-generate.quantity-max}")
    private Integer quantity;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-barcode-generate.remark-size}")
    private String remark;
}
