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
    @NotNull(message = "工单不能为空")
    private Long workOrderId;

    /** 任务 ID */
    private Long taskId;

    /** 领料仓库编码 */
    @NotBlank(message = "领料仓库不能为空")
    @Size(max = 64, message = "仓库编码长度不能超过 64")
    private String warehouseCode;

    /** 产线 ID（填写时按线边仓扣减库存） */
    private Long lineId;

    /** 产线编码（与产线 ID 同时填写） */
    @Size(max = 64, message = "产线编码长度不能超过 64")
    private String lineCode;

    /** 类型：ISSUE 领料 / SUPPLEMENT 补料 */
    @Pattern(regexp = "ISSUE|SUPPLEMENT", message = "领料类型不合法")
    private String issueType;

    /** 补料原因 */
    @Size(max = 255, message = "补料原因长度不能超过 255")
    private String reason;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;

    /** 领料明细 */
    @NotEmpty(message = "领料明细不能为空")
    @Valid
    private List<Line> lines;

    /**
     * 领料明细项
     */
    @Data
    public static class Line {

        /** 物料编码 */
        @NotBlank(message = "物料编码不能为空")
        @Size(max = 64, message = "物料编码长度不能超过 64")
        private String itemCode;

        /** 物料名称 */
        @Size(max = 128, message = "物料名称长度不能超过 128")
        private String itemName;

        /** 领用数量 */
        @NotNull(message = "领用数量不能为空")
        @DecimalMin(value = "0.0001", message = "领用数量必须大于 0")
        private BigDecimal quantity;

        /** 计量单位 */
        @Size(max = 16, message = "计量单位长度不能超过 16")
        private String uom;

        /** 批次号 */
        @Size(max = 64, message = "批次号长度不能超过 64")
        private String batchNo;
    }
}
