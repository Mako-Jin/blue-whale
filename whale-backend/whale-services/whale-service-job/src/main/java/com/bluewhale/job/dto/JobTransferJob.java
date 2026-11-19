package com.bluewhale.job.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 任务导出/导入单项：只包含与环境无关、可搬运的任务定义字段。
 *
 * <p>不含 {@code id} / {@code tenantId}（环境相关）、{@code failCount}（运行态计数），
 * 避免把源环境的标识与失败历史带到目标环境。</p>
 *
 * @author BlueWhale
 */
@Data
public class JobTransferJob {

    /** 任务名称 */
    @NotBlank(message = "{validation.job.transfer.name-required}")
    @Size(max = 64, message = "{validation.job.transfer.name-size}")
    private String jobName;

    /** 任务分组 */
    @Size(max = 64, message = "{validation.job.transfer.group-size}")
    private String jobGroup;

    /** 执行器应用名（Nacos 服务名） */
    @Size(max = 64, message = "{validation.job.transfer.executor-app-size}")
    private String executorApp;

    /** 任务编码（对应业务侧 @ScheduledJob.code） */
    @Size(max = 128, message = "{validation.job.transfer.job-code-size}")
    private String jobCode;

    /** Cron 执行表达式 */
    @NotBlank(message = "{validation.job.transfer.cron-required}")
    @Size(max = 64, message = "{validation.job.transfer.cron-size}")
    private String cronExpression;

    /** 执行器 Bean 名称 */
    @NotBlank(message = "{validation.job.transfer.handler-bean-required}")
    @Size(max = 128, message = "{validation.job.transfer.handler-bean-size}")
    private String handlerBean;

    /** 执行器参数 */
    @Size(max = 512, message = "{validation.job.transfer.handler-param-size}")
    private String handlerParam;

    /** 错过执行策略：SMART / FIRE_ONCE / DO_NOTHING */
    @Size(max = 16, message = "{validation.job.transfer.misfire-policy-size}")
    private String misfirePolicy;

    /** 失败重试次数上限 */
    @Min(value = 0, message = "{validation.job.transfer.max-retry-min}")
    @Max(value = 5, message = "{validation.job.transfer.max-retry-max}")
    private Integer maxRetry;

    /** 重试间隔秒数 */
    @Min(value = 0, message = "{validation.job.transfer.retry-interval-min}")
    private Integer retryIntervalSeconds;

    /** 分片总数：1~16，-1 表示按在线实例动态分片 */
    @Min(value = -1, message = "{validation.job.transfer.shard-total-min}")
    @Max(value = 16, message = "{validation.job.transfer.shard-total-max}")
    private Integer shardTotal;

    /** 单次执行超时秒数：0 表示沿用全局默认 */
    @Min(value = 0, message = "{validation.job.transfer.timeout-min}")
    @Max(value = 86400, message = "{validation.job.transfer.timeout-max}")
    private Integer timeoutSeconds;

    /** 连续失败自动暂停阈值：0 表示关闭熔断 */
    @Min(value = 0, message = "{validation.job.transfer.fail-threshold-min}")
    @Max(value = 100, message = "{validation.job.transfer.fail-threshold-max}")
    private Integer failThreshold;

    /** 任务状态：1 运行中 / 0 已停止 */
    @Min(value = 0, message = "{validation.job.transfer.status-invalid}")
    @Max(value = 1, message = "{validation.job.transfer.status-invalid}")
    private Integer status;

    /** 备注 */
    @Size(max = 255, message = "{validation.job.transfer.remark-size}")
    private String remark;
}
