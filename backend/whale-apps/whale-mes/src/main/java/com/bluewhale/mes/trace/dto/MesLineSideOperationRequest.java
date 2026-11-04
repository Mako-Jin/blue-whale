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
    @NotNull(message = "产线不能为空")
    private Long lineId;

    /** 产线编码快照 */
    @NotBlank(message = "产线编码不能为空")
    @Size(max = 64, message = "产线编码长度不能超过 64")
    private String lineCode;

    /** 线边仓编码 */
    @NotBlank(message = "线边仓编码不能为空")
    @Size(max = 64, message = "线边仓编码长度不能超过 64")
    private String warehouseCode;

    /** 物料编码 */
    @NotBlank(message = "物料编码不能为空")
    @Size(max = 64, message = "物料编码长度不能超过 64")
    private String itemCode;

    /** 物料名称 */
    @Size(max = 128, message = "物料名称长度不能超过 128")
    private String itemName;

    /** 批次号 */
    @Size(max = 64, message = "批次号长度不能超过 64")
    private String batchNo;

    /** 数量 */
    @NotNull(message = "数量不能为空")
    @DecimalMin(value = "0.0001", message = "数量必须大于 0")
    private BigDecimal quantity;

    /** 计量单位 */
    @Size(max = 16, message = "计量单位长度不能超过 16")
    private String uom;

    /** 入库类型：INBOUND 入库 / RETURN 退料退回 */
    @Size(max = 16, message = "入库类型长度不能超过 16")
    private String movementType;

    /** 关联工单 ID */
    private Long workOrderId;

    /** 关联任务 ID */
    private Long taskId;

    /** 操作人 */
    @Size(max = 64, message = "操作人长度不能超过 64")
    private String operator;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
