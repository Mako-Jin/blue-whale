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
    @NotNull(message = "检验项 ID 不能为空")
    private Long planItemId;

    /** 检验项序号 */
    private Integer itemNo;

    /** 检验项名称 */
    @Size(max = 128, message = "检验项名称长度不能超过 128")
    private String itemName;

    /** 样本序号 */
    @Min(value = 1, message = "样本序号必须大于 0")
    private Integer sampleNo;

    /** 实测值文本 */
    @Size(max = 128, message = "实测值长度不能超过 128")
    private String measuredValue;

    /** 实测数值 */
    private BigDecimal numericValue;

    /** 判定：OK / NG */
    @NotNull(message = "判定结果不能为空")
    @Pattern(regexp = "OK|NG", message = "判定结果仅支持 OK / NG")
    private String result;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
