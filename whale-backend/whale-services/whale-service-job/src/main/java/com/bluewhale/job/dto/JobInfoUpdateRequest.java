package com.bluewhale.job.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新定时任务请求（字段与新增一致，更新后由调度引擎按最新定义重新注册）。
 *
 * @author BlueWhale
 */
@Data
public class JobInfoUpdateRequest {

    /** 任务名称 */
    @NotBlank(message = "{validation.job.info.name-required}")
    @Size(max = 64, message = "{validation.job.info.name-size}")
    private String jobName;

    /** 任务分组 */
    @Size(max = 64, message = "{validation.job.info.group-size}")
    private String jobGroup;

    /** 执行器应用名（Nacos 服务名）：配置后按注册表寻址 */
    @Size(max = 64, message = "{validation.job.info.executor-app-size}")
    private String executorApp;

    /** 任务编码（对应业务侧 @ScheduledJob.code） */
    @Size(max = 128, message = "{validation.job.info.job-code-size}")
    private String jobCode;

    /** Cron 执行表达式 */
    @NotBlank(message = "{validation.job.info.cron-required}")
    @Size(max = 64, message = "{validation.job.info.cron-size}")
    private String cronExpression;

    /** 执行器 Bean 名称 */
    @NotBlank(message = "{validation.job.info.handler-bean-required}")
    @Size(max = 128, message = "{validation.job.info.handler-bean-size}")
    private String handlerBean;

    /** 执行器参数 */
    @Size(max = 512, message = "{validation.job.info.handler-param-size}")
    private String handlerParam;

    /** 错过执行策略：SMART / FIRE_ONCE / DO_NOTHING */
    @Size(max = 16, message = "{validation.job.info.misfire-policy-size}")
    private String misfirePolicy;

    /** 失败重试次数上限（为空时保留原值） */
    @Min(value = 0, message = "{validation.job.info.max-retry-min}")
    @Max(value = 5, message = "{validation.job.info.max-retry-max}")
    private Integer maxRetry;

    /** 重试间隔秒数（为空时保留原值） */
    @Min(value = 0, message = "{validation.job.info.retry-interval-min}")
    private Integer retryIntervalSeconds;

    /**
     * 分片总数（为空时保留原值）。
     *
     * <p>取 {@code -1} 表示动态分片：每次执行按该任务当前可派发的执行器实例数决定分片数。</p>
     */
    @Min(value = -1, message = "{validation.job.info.shard-total-min}")
    @Max(value = 16, message = "{validation.job.info.shard-total-max}")
    private Integer shardTotal;

    /** 单次执行超时秒数（为空时保留原值）：0 表示沿用全局默认 */
    @Min(value = 0, message = "{validation.job.info.timeout-min}")
    @Max(value = 86400, message = "{validation.job.info.timeout-max}")
    private Integer timeoutSeconds;

    /** 连续失败自动暂停阈值（为空时保留原值）：0 表示关闭熔断 */
    @Min(value = 0, message = "{validation.job.info.fail-threshold-min}")
    @Max(value = 100, message = "{validation.job.info.fail-threshold-max}")
    private Integer failThreshold;

    /** 任务状态：1 运行中 / 0 已停止（为空时保留原值） */
    @Min(value = 0, message = "{validation.job.info.status-invalid}")
    @Max(value = 1, message = "{validation.job.info.status-invalid}")
    private Integer status;

    /** 备注 */
    @Size(max = 255, message = "{validation.job.info.remark-size}")
    private String remark;
}
