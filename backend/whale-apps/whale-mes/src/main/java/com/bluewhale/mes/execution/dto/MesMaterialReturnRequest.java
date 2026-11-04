package com.bluewhale.mes.execution.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 退料请求
 *
 * @author BlueWhale
 */
@Data
public class MesMaterialReturnRequest {

    /** 工单 ID */
    @NotNull(message = "工单不能为空")
    private Long workOrderId;

    /** 任务 ID */
    private Long taskId;

    /** 原领料单 ID */
    private Long issueId;

    /** 退料仓库编码 */
    @NotBlank(message = "退料仓库不能为空")
    @Size(max = 64, message = "仓库编码长度不能超过 64")
    private String warehouseCode;

    /** 物料编码 */
    @NotBlank(message = "物料编码不能为空")
    @Size(max = 64, message = "物料编码长度不能超过 64")
    private String itemCode;

    /** 物料名称 */
    @Size(max = 128, message = "物料名称长度不能超过 128")
    private String itemName;

    /** 退料数量 */
    @NotNull(message = "退料数量不能为空")
    @DecimalMin(value = "0.0001", message = "退料数量必须大于 0")
    private BigDecimal quantity;

    /** 计量单位 */
    @Size(max = 16, message = "计量单位长度不能超过 16")
    private String uom;

    /** 批次号 */
    @Size(max = 64, message = "批次号长度不能超过 64")
    private String batchNo;

    /** 退料原因 */
    @Size(max = 255, message = "退料原因长度不能超过 255")
    private String reason;
}
