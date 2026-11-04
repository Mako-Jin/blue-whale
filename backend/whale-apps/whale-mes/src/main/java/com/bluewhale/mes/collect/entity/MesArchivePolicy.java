package com.bluewhale.mes.collect.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 归档策略实体，对应表 {@code mes_cl_archive_policy}。
 *
 * <p>归档执行采用“候选计数 + 幂等预览”，重复执行不产生额外副作用；物理清理由 DBA 作业承接。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_cl_archive_policy")
public class MesArchivePolicy extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 归档策略编码（租户内唯一） */
    private String policyCode;

    /** 归档目标表 */
    private String tableName;

    /** 保留天数 */
    private Integer retentionDays;

    /** 单批归档条数 */
    private Integer archiveBatchSize;

    /** 调度表达式 */
    private String cronExpr;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 最近执行时间 */
    private LocalDateTime lastRunAt;

    /** 最近一次归档候选条数 */
    private Long lastCandidateCount;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
