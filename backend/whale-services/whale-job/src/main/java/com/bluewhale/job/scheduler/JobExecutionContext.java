package com.bluewhale.job.scheduler;

import lombok.Getter;

/**
 * 任务执行上下文：向 {@link JobHandler} 透传任务标识、参数、分片、重试与执行器定位信息。
 *
 * <p>由调度引擎在每次尝试时构造；{@link JobHandler#executeWithContext(JobExecutionContext)}
 * 的默认实现会退化为仅使用 {@link #getParam()} 调用旧方法，因此既有执行器实现
 * 无需改动即可继续工作。</p>
 *
 * <p>{@code tenantId / executorApp / jobCode} 供 HTTP 型执行器寻址使用：
 * 前两者用于定位「哪个租户的哪个应用」，{@code jobCode} 用于拼出对端的触发路径。
 * 旧构造签名保留，这三个字段为 {@code null} 时执行器应回退到「按参数直连」。</p>
 *
 * @author BlueWhale
 */
@Getter
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

    /** 租户ID：执行器跨服务调用时放到 {@code X-Tenant-Id} 头 */
    private final Long tenantId;

    /** 执行器应用名（Nacos 服务名），用于从注册表解析实例地址 */
    private final String executorApp;

    /** 任务编码，用于拼出对端 {@code /internal/job/{code}/run} 路径 */
    private final String jobCode;

    /**
     * 兼容旧签名的构造：执行器定位信息为空，HTTP 型执行器将回退到按参数直连。
     *
     * @param jobId      任务ID
     * @param jobName    任务名称
     * @param param      执行器参数
     * @param shardIndex 当前分片序号
     * @param shardTotal 分片总数
     * @param retryCount 已重试次数
     */
    public JobExecutionContext(Long jobId, String jobName, String param,
                               int shardIndex, int shardTotal, int retryCount) {
        this(jobId, jobName, param, shardIndex, shardTotal, retryCount, null, null, null);
    }

    /**
     * 完整构造。
     *
     * @param jobId       任务ID
     * @param jobName     任务名称
     * @param param       执行器参数
     * @param shardIndex  当前分片序号
     * @param shardTotal  分片总数
     * @param retryCount  已重试次数
     * @param tenantId    租户ID
     * @param executorApp 执行器应用名
     * @param jobCode     任务编码
     */
    public JobExecutionContext(Long jobId, String jobName, String param,
                               int shardIndex, int shardTotal, int retryCount,
                               Long tenantId, String executorApp, String jobCode) {
        this.jobId = jobId;
        this.jobName = jobName;
        this.param = param;
        this.shardIndex = shardIndex;
        this.shardTotal = shardTotal;
        this.retryCount = retryCount;
        this.tenantId = tenantId;
        this.executorApp = executorApp;
        this.jobCode = jobCode;
    }
}
