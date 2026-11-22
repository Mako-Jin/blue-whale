package com.bluewhale.mes.quality.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 手工创建 NCR 请求（检验 FAILED 由服务层自动生成，无需调用方传）
 *
 * @author BlueWhale
 */
@Data
public class MesNcrCreateRequest {

    /** 来源：INSPECTION / REPORT / MANUAL */
    @Pattern(regexp = "INSPECTION|REPORT|MANUAL", message = "{validation.mes.mes-ncr.source-type-pattern}")
    private String sourceType;

    /** 工单 ID */
    private Long workOrderId;

    /** 工单编码 */
    @Size(max = 64, message = "{validation.mes.mes-ncr.work-order-code-size}")
    private String workOrderCode;

    /** 工序任务 ID */
    private Long execTaskId;

    /** 工序任务号 */
    @Size(max = 64, message = "{validation.mes.mes-ncr.exec-task-no-size}")
    private String execTaskNo;

    /** 物料编码 */
    @Size(max = 64, message = "{validation.mes.mes-ncr.item-code-size}")
    private String itemCode;

    /** 物料名称 */
    @Size(max = 128, message = "{validation.mes.mes-ncr.item-name-size}")
    private String itemName;

    /** 批次号 */
    @Size(max = 64, message = "{validation.mes.mes-ncr.batch-no-size}")
    private String batchNo;

    /** 工序编码 */
    @Size(max = 64, message = "{validation.mes.mes-ncr.operation-code-size}")
    private String operationCode;

    /** 缺陷代码 */
    @Size(max = 64, message = "{validation.mes.mes-ncr.defect-code-size}")
    private String defectCode;

    /** 缺陷名称 */
    @Size(max = 128, message = "{validation.mes.mes-ncr.defect-name-size}")
    private String defectName;

    /** 不良数量 */
    @DecimalMin(value = "0.0001", message = "{validation.mes.mes-ncr.defect-quantity-min}")
    private BigDecimal defectQuantity;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-ncr.remark-size}")
    private String remark;
}
