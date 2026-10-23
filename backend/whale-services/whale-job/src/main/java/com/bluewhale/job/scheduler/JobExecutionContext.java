package com.bluewhale.job.scheduler;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 任务执行上下文：向 {@link JobHandler} 透传任务标识、参数、分片与重试信息。
 *
 * <p>由调度引擎在每次尝试时构造；{@link JobHandler#executeWithContext(JobExecutionContext)}
 * 的默认实现会退化为仅使用 {@link #getParam()} 调用旧方法，因此既有执行器实现
 * 无需改动即可继续工作，需要感知分片或重试次数的实现可覆盖带上下文的方法。</p>
 *
 * @author BlueWhale
 */
@Getter
@RequiredArgsConstructor
public class JobExecutionContext {

    /** 任务ID */
    private final Long jobId;

    /** 任务名称 */
    private final String jobName;

    /** 执行器参数（来自 {@code job_info.handler_param}） */
    private final String param;

    /** 当前分片序号（从 0 开始） */
    private final int shardIndex;

    /** 分片总数（1 表示不分片） */
    private final int shardTotal;

    /** 本次执行已重试次数（首次执行为 0） */
    private final int retryCount;
}
