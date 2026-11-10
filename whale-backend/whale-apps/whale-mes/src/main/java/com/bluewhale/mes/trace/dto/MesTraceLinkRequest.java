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
    @NotBlank(message = "上级批次号不能为空")
    @Size(max = 64, message = "上级批次号长度不能超过 64")
    private String parentBatchNo;

    /** 下级批次号（产出） */
    @NotBlank(message = "下级批次号不能为空")
    @Size(max = 64, message = "下级批次号长度不能超过 64")
    private String childBatchNo;

    /** 消耗数量 */
    @NotNull(message = "消耗数量不能为空")
    @DecimalMin(value = "0.0001", message = "消耗数量必须大于 0")
    private BigDecimal quantity;

    /** 计量单位 */
    @Size(max = 16, message = "计量单位长度不能超过 16")
    private String uom;

    /** 关联工单 ID */
    private Long workOrderId;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
