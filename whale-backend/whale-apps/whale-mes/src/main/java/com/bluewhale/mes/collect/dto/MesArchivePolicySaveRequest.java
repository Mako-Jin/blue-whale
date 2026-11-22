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
    @Size(max = 64, message = "{validation.mes.mes-archive-policy.policy-code-size}")
    private String policyCode;

    /** 归档目标表 */
    @NotBlank(message = "{validation.mes.mes-archive-policy.table-name-required}")
    @Size(max = 64, message = "{validation.mes.mes-archive-policy.table-name-size}")
    private String tableName;

    /** 保留天数 */
    @Min(value = 1, message = "{validation.mes.mes-archive-policy.retention-days-min}")
    private Integer retentionDays;

    /** 单批归档条数 */
    @Min(value = 1, message = "{validation.mes.mes-archive-policy.archive-batch-size-min}")
    private Integer archiveBatchSize;

    /** 调度表达式 */
    @Size(max = 64, message = "{validation.mes.mes-archive-policy.cron-expr-size}")
    private String cronExpr;

    /** 状态：ENABLED / DISABLED */
    @Pattern(regexp = "ENABLED|DISABLED", message = "{validation.mes.mes-archive-policy.status-pattern}")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-archive-policy.remark-size}")
    private String remark;
}
