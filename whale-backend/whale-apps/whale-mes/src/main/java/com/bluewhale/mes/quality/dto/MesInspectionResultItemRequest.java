package com.bluewhale.mes.quality.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 检验结果明细请求
 *
 * @author BlueWhale
 */
@Data
public class MesInspectionResultItemRequest {

    /** 计划检验项 ID */
    @NotNull(message = "{validation.mes.mes-inspection-result-item.plan-item-id-required}")
    private Long planItemId;

    /** 检验项序号 */
    private Integer itemNo;

    /** 检验项名称 */
    @Size(max = 128, message = "{validation.mes.mes-inspection-result-item.item-name-size}")
    private String itemName;

    /** 样本序号 */
    @Min(value = 1, message = "{validation.mes.mes-inspection-result-item.sample-no-min}")
    private Integer sampleNo;

    /** 实测值文本 */
    @Size(max = 128, message = "{validation.mes.mes-inspection-result-item.measured-value-size}")
    private String measuredValue;

    /** 实测数值 */
    private BigDecimal numericValue;

    /** 判定：OK / NG */
    @NotNull(message = "{validation.mes.mes-inspection-result-item.result-required}")
    @Pattern(regexp = "OK|NG", message = "{validation.mes.mes-inspection-result-item.result-pattern}")
    private String result;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-inspection-result-item.remark-size}")
    private String remark;
}
