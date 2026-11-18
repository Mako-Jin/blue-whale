package com.bluewhale.job.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务依赖关系实体，对应表 {@code job_dependency}。
 *
 * <p>每行表示一条「下游任务依赖上游任务」的有向边；同一 {@code runId} 下，
 * 仅当上游任务全部分片成功执行后才会链式触发下游。公共字段（id / create_time /
 * update_time / deleted）继承自 {@link BaseEntity}。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_dependency")
public class JobDependencyPo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 下游任务ID（逻辑关联 job_info.id） */
    private Long jobId;

    /** 上游任务ID（逻辑关联 job_info.id） */
    private Long upstreamJobId;
}
