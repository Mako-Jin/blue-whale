package com.bluewhale.mes.execution.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 领料 / 补料请求
 *
 * @author BlueWhale
 */
@Data
public class MesMaterialIssueRequest {

    /** 工单 ID */
    @NotNull(message = "{validation.mes.mes-material-issue.work-order-id-required}")
    private Long workOrderId;

    /** 任务 ID */
    private Long taskId;

    /** 领料仓库编码 */
    @NotBlank(message = "{validation.mes.mes-material-issue.warehouse-code-required}")
    @Size(max = 64, message = "{validation.mes.mes-material-issue.warehouse-code-size}")
    private String warehouseCode;

    /** 产线 ID（填写时按线边仓扣减库存） */
    private Long lineId;

    /** 产线编码（与产线 ID 同时填写） */
    @Size(max = 64, message = "{validation.mes.mes-material-issue.line-code-size}")
    private String lineCode;

    /** 类型：ISSUE 领料 / SUPPLEMENT 补料 */
    @Pattern(regexp = "ISSUE|SUPPLEMENT", message = "{validation.mes.mes-material-issue.issue-type-pattern}")
    private String issueType;

    /** 补料原因 */
    @Size(max = 255, message = "{validation.mes.mes-material-issue.reason-size}")
    private String reason;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-material-issue.remark-size}")
    private String remark;

    /** 领料明细 */
    @NotEmpty(message = "{validation.mes.mes-material-issue.lines-required}")
    @Valid
    private List<Line> lines;

    /**
     * 领料明细项
     */
    @Data
    public static class Line {

        /** 物料编码 */
        @NotBlank(message = "{validation.mes.line.item-code-required}")
        @Size(max = 64, message = "{validation.mes.line.item-code-size}")
        private String itemCode;

        /** 物料名称 */
        @Size(max = 128, message = "{validation.mes.line.item-name-size}")
        private String itemName;

        /** 领用数量 */
        @NotNull(message = "{validation.mes.line.quantity-required}")
        @DecimalMin(value = "0.0001", message = "{validation.mes.line.quantity-min}")
        private BigDecimal quantity;

        /** 计量单位 */
        @Size(max = 16, message = "{validation.mes.line.uom-size}")
        private String uom;

        /** 批次号 */
        @Size(max = 64, message = "{validation.mes.line.batch-no-size}")
        private String batchNo;
    }
}
