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
    @NotNull(message = "{validation.mes.mes-material-return.work-order-id-required}")
    private Long workOrderId;

    /** 任务 ID */
    private Long taskId;

    /** 原领料单 ID */
    private Long issueId;

    /** 退料仓库编码 */
    @NotBlank(message = "{validation.mes.mes-material-return.warehouse-code-required}")
    @Size(max = 64, message = "{validation.mes.mes-material-return.warehouse-code-size}")
    private String warehouseCode;

    /** 物料编码 */
    @NotBlank(message = "{validation.mes.mes-material-return.item-code-required}")
    @Size(max = 64, message = "{validation.mes.mes-material-return.item-code-size}")
    private String itemCode;

    /** 物料名称 */
    @Size(max = 128, message = "{validation.mes.mes-material-return.item-name-size}")
    private String itemName;

    /** 退料数量 */
    @NotNull(message = "{validation.mes.mes-material-return.quantity-required}")
    @DecimalMin(value = "0.0001", message = "{validation.mes.mes-material-return.quantity-min}")
    private BigDecimal quantity;

    /** 计量单位 */
    @Size(max = 16, message = "{validation.mes.mes-material-return.uom-size}")
    private String uom;

    /** 批次号 */
    @Size(max = 64, message = "{validation.mes.mes-material-return.batch-no-size}")
    private String batchNo;

    /** 退料原因 */
    @Size(max = 255, message = "{validation.mes.mes-material-return.reason-size}")
    private String reason;
}
