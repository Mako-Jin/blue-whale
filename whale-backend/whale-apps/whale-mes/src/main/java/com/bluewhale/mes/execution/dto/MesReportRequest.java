package com.bluewhale.mes.execution.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 扫码报工请求。
 *
 * <p>约束：合格数量 + 不良数量 = 报工数量；不良明细数量之和等于不良数量；
 * 累计报工不超过任务已派工数量。报工为追加式，提交后不可修改。</p>
 *
 * @author BlueWhale
 */
@Data
public class MesReportRequest {

    /** 任务 ID */
    @NotNull(message = "任务不能为空")
    private Long taskId;

    /** 报工数量 */
    @NotNull(message = "报工数量不能为空")
    @DecimalMin(value = "0.0001", message = "报工数量必须大于 0")
    private BigDecimal reportQuantity;

    /** 合格数量 */
    @NotNull(message = "合格数量不能为空")
    @DecimalMin(value = "0.0000", message = "合格数量不能为负数")
    private BigDecimal qualifiedQuantity;

    /** 不良数量 */
    @NotNull(message = "不良数量不能为空")
    @DecimalMin(value = "0.0000", message = "不良数量不能为负数")
    private BigDecimal defectQuantity;

    /** 工位 ID */
    private Long stationId;

    /** 报工人 */
    @Size(max = 64, message = "报工人长度不能超过 64")
    private String reporter;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;

    /** 不良明细 */
    @Valid
    private List<DefectItem> defects;

    /**
     * 不良明细项
     */
    @Data
    public static class DefectItem {

        /** 缺陷编码 */
        @NotBlank(message = "缺陷编码不能为空")
        @Size(max = 64, message = "缺陷编码长度不能超过 64")
        private String defectCode;

        /** 缺陷名称 */
        @Size(max = 128, message = "缺陷名称长度不能超过 128")
        private String defectName;

        /** 不良数量 */
        @NotNull(message = "不良数量不能为空")
        @DecimalMin(value = "0.0001", message = "不良明细数量必须大于 0")
        private BigDecimal quantity;
    }
}
