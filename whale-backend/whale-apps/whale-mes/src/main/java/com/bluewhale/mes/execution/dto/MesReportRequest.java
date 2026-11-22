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
    @NotNull(message = "{validation.mes.mes-report.task-id-required}")
    private Long taskId;

    /** 报工数量 */
    @NotNull(message = "{validation.mes.mes-report.report-quantity-required}")
    @DecimalMin(value = "0.0001", message = "{validation.mes.mes-report.report-quantity-min}")
    private BigDecimal reportQuantity;

    /** 合格数量 */
    @NotNull(message = "{validation.mes.mes-report.qualified-quantity-required}")
    @DecimalMin(value = "0.0000", message = "{validation.mes.mes-report.qualified-quantity-min}")
    private BigDecimal qualifiedQuantity;

    /** 不良数量 */
    @NotNull(message = "{validation.mes.mes-report.defect-quantity-required}")
    @DecimalMin(value = "0.0000", message = "{validation.mes.mes-report.defect-quantity-min}")
    private BigDecimal defectQuantity;

    /** 工位 ID */
    private Long stationId;

    /** 报工人 */
    @Size(max = 64, message = "{validation.mes.mes-report.reporter-size}")
    private String reporter;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-report.remark-size}")
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
        @NotBlank(message = "{validation.mes.defect-item.defect-code-required}")
        @Size(max = 64, message = "{validation.mes.defect-item.defect-code-size}")
        private String defectCode;

        /** 缺陷名称 */
        @Size(max = 128, message = "{validation.mes.defect-item.defect-name-size}")
        private String defectName;

        /** 不良数量 */
        @NotNull(message = "{validation.mes.defect-item.quantity-required}")
        @DecimalMin(value = "0.0001", message = "{validation.mes.defect-item.quantity-min}")
        private BigDecimal quantity;
    }
}
