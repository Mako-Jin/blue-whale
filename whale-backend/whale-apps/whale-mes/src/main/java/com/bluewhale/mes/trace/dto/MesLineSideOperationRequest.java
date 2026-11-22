package com.bluewhale.mes.trace.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 线边仓出入库请求
 *
 * @author BlueWhale
 */
@Data
public class MesLineSideOperationRequest {

    /** 产线 ID */
    @NotNull(message = "{validation.mes.mes-line-side-operation.line-id-required}")
    private Long lineId;

    /** 产线编码快照 */
    @NotBlank(message = "{validation.mes.mes-line-side-operation.line-code-required}")
    @Size(max = 64, message = "{validation.mes.mes-line-side-operation.line-code-size}")
    private String lineCode;

    /** 线边仓编码 */
    @NotBlank(message = "{validation.mes.mes-line-side-operation.warehouse-code-required}")
    @Size(max = 64, message = "{validation.mes.mes-line-side-operation.warehouse-code-size}")
    private String warehouseCode;

    /** 物料编码 */
    @NotBlank(message = "{validation.mes.mes-line-side-operation.item-code-required}")
    @Size(max = 64, message = "{validation.mes.mes-line-side-operation.item-code-size}")
    private String itemCode;

    /** 物料名称 */
    @Size(max = 128, message = "{validation.mes.mes-line-side-operation.item-name-size}")
    private String itemName;

    /** 批次号 */
    @Size(max = 64, message = "{validation.mes.mes-line-side-operation.batch-no-size}")
    private String batchNo;

    /** 数量 */
    @NotNull(message = "{validation.mes.mes-line-side-operation.quantity-required}")
    @DecimalMin(value = "0.0001", message = "{validation.mes.mes-line-side-operation.quantity-min}")
    private BigDecimal quantity;

    /** 计量单位 */
    @Size(max = 16, message = "{validation.mes.mes-line-side-operation.uom-size}")
    private String uom;

    /** 入库类型：INBOUND 入库 / RETURN 退料退回 */
    @Size(max = 16, message = "{validation.mes.mes-line-side-operation.movement-type-size}")
    private String movementType;

    /** 关联工单 ID */
    private Long workOrderId;

    /** 关联任务 ID */
    private Long taskId;

    /** 操作人 */
    @Size(max = 64, message = "{validation.mes.mes-line-side-operation.operator-size}")
    private String operator;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-line-side-operation.remark-size}")
    private String remark;
}
