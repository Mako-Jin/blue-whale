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
    @Pattern(regexp = "INSPECTION|REPORT|MANUAL", message = "来源仅支持 INSPECTION / REPORT / MANUAL")
    private String sourceType;

    /** 工单 ID */
    private Long workOrderId;

    /** 工单编码 */
    @Size(max = 64, message = "工单编码长度不能超过 64")
    private String workOrderCode;

    /** 工序任务 ID */
    private Long execTaskId;

    /** 工序任务号 */
    @Size(max = 64, message = "工序任务号长度不能超过 64")
    private String execTaskNo;

    /** 物料编码 */
    @Size(max = 64, message = "物料编码长度不能超过 64")
    private String itemCode;

    /** 物料名称 */
    @Size(max = 128, message = "物料名称长度不能超过 128")
    private String itemName;

    /** 批次号 */
    @Size(max = 64, message = "批次号长度不能超过 64")
    private String batchNo;

    /** 工序编码 */
    @Size(max = 64, message = "工序编码长度不能超过 64")
    private String operationCode;

    /** 缺陷代码 */
    @Size(max = 64, message = "缺陷代码长度不能超过 64")
    private String defectCode;

    /** 缺陷名称 */
    @Size(max = 128, message = "缺陷名称长度不能超过 128")
    private String defectName;

    /** 不良数量 */
    @DecimalMin(value = "0.0001", message = "不良数量必须大于 0")
    private BigDecimal defectQuantity;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
