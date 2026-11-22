package com.bluewhale.mes.trace.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 批次建档请求
 *
 * @author BlueWhale
 */
@Data
public class MesBatchSaveRequest {

    /** 批次号（空则自动生成） */
    @Size(max = 64, message = "{validation.mes.mes-batch.batch-no-size}")
    private String batchNo;

    /** 物料编码 */
    @NotBlank(message = "{validation.mes.mes-batch.item-code-required}")
    @Size(max = 64, message = "{validation.mes.mes-batch.item-code-size}")
    private String itemCode;

    /** 物料名称 */
    @Size(max = 128, message = "{validation.mes.mes-batch.item-name-size}")
    private String itemName;

    /** 批次类型：RAW / SEMI / FINISHED */
    @Pattern(regexp = "RAW|SEMI|FINISHED", message = "{validation.mes.mes-batch.batch-type-pattern}")
    private String batchType;

    /** 来源：PURCHASE / PRODUCTION / RETURN */
    @Pattern(regexp = "PURCHASE|PRODUCTION|RETURN", message = "{validation.mes.mes-batch.source-type-pattern}")
    private String sourceType;

    /** 来源工单 ID */
    private Long workOrderId;

    /** 来源工单编码 */
    @Size(max = 64, message = "{validation.mes.mes-batch.work-order-code-size}")
    private String workOrderCode;

    /** 供应商编码 */
    @Size(max = 64, message = "{validation.mes.mes-batch.supplier-code-size}")
    private String supplierCode;

    /** 批次初始数量 */
    @DecimalMin(value = "0.0001", message = "{validation.mes.mes-batch.quantity-min}")
    private BigDecimal quantity;

    /** 计量单位 */
    @Size(max = 16, message = "{validation.mes.mes-batch.uom-size}")
    private String uom;

    /** 生产/到货日期 */
    private LocalDate productionDate;

    /** 有效期至 */
    private LocalDate expiryDate;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-batch.remark-size}")
    private String remark;
}
