package com.bluewhale.mes.collect.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 归档策略视图
 *
 * @author BlueWhale
 */
@Data
public class MesArchivePolicyVO {

    /** 策略 ID */
    private Long id;

    /** 策略编码 */
    private String policyCode;

    /** 归档目标表 */
    private String tableName;

    /** 保留天数 */
    private Integer retentionDays;

    /** 单批归档条数 */
    private Integer archiveBatchSize;

    /** 调度表达式 */
    private String cronExpr;

    /** 状态 */
    private String status;

    /** 最近执行时间 */
    private LocalDateTime lastRunAt;

    /** 最近候选条数 */
    private Long lastCandidateCount;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
