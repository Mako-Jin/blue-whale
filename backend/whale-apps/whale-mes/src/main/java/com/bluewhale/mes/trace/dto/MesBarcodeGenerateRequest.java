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
    @NotNull(message = "条码规则不能为空")
    private Long ruleId;

    /** 绑定批次 ID */
    @NotNull(message = "绑定批次不能为空")
    private Long batchId;

    /** 生成数量 */
    @NotNull(message = "生成数量不能为空")
    @Min(value = 1, message = "生成数量至少为 1")
    @Max(value = 1000, message = "单次最多生成 1000 个条码")
    private Integer quantity;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
