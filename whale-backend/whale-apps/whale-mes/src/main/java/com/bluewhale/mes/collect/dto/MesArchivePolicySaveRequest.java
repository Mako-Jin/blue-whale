package com.bluewhale.mes.collect.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 归档策略保存请求
 *
 * @author BlueWhale
 */
@Data
public class MesArchivePolicySaveRequest {

    /** 策略 ID（编辑时必填） */
    private Long id;

    /** 策略编码（空则编辑时保持不变） */
    @Size(max = 64, message = "策略编码长度不能超过 64")
    private String policyCode;

    /** 归档目标表 */
    @NotBlank(message = "归档目标表不能为空")
    @Size(max = 64, message = "归档目标表长度不能超过 64")
    private String tableName;

    /** 保留天数 */
    @Min(value = 1, message = "保留天数必须大于 0")
    private Integer retentionDays;

    /** 单批归档条数 */
    @Min(value = 1, message = "单批归档条数必须大于 0")
    private Integer archiveBatchSize;

    /** 调度表达式 */
    @Size(max = 64, message = "调度表达式长度不能超过 64")
    private String cronExpr;

    /** 状态：ENABLED / DISABLED */
    @Pattern(regexp = "ENABLED|DISABLED", message = "状态仅支持 ENABLED / DISABLED")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
