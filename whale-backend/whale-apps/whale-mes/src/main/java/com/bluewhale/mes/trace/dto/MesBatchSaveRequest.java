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
    @Size(max = 64, message = "批次号长度不能超过 64")
    private String batchNo;

    /** 物料编码 */
    @NotBlank(message = "物料编码不能为空")
    @Size(max = 64, message = "物料编码长度不能超过 64")
    private String itemCode;

    /** 物料名称 */
    @Size(max = 128, message = "物料名称长度不能超过 128")
    private String itemName;

    /** 批次类型：RAW / SEMI / FINISHED */
    @Pattern(regexp = "RAW|SEMI|FINISHED", message = "批次类型仅支持 RAW / SEMI / FINISHED")
    private String batchType;

    /** 来源：PURCHASE / PRODUCTION / RETURN */
    @Pattern(regexp = "PURCHASE|PRODUCTION|RETURN", message = "来源仅支持 PURCHASE / PRODUCTION / RETURN")
    private String sourceType;

    /** 来源工单 ID */
    private Long workOrderId;

    /** 来源工单编码 */
    @Size(max = 64, message = "工单编码长度不能超过 64")
    private String workOrderCode;

    /** 供应商编码 */
    @Size(max = 64, message = "供应商编码长度不能超过 64")
    private String supplierCode;

    /** 批次初始数量 */
    @DecimalMin(value = "0.0001", message = "批次数量必须大于 0")
    private BigDecimal quantity;

    /** 计量单位 */
    @Size(max = 16, message = "计量单位长度不能超过 16")
    private String uom;

    /** 生产/到货日期 */
    private LocalDate productionDate;

    /** 有效期至 */
    private LocalDate expiryDate;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
