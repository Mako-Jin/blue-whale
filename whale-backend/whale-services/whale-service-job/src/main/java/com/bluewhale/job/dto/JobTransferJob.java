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
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 64, message = "任务名称长度不能超过 64")
    private String jobName;

    /** 任务分组 */
    @Size(max = 64, message = "任务分组长度不能超过 64")
    private String jobGroup;

    /** 执行器应用名（Nacos 服务名） */
    @Size(max = 64, message = "执行器应用名长度不能超过 64")
    private String executorApp;

    /** 任务编码（对应业务侧 @ScheduledJob.code） */
    @Size(max = 128, message = "任务编码长度不能超过 128")
    private String jobCode;

    /** Cron 执行表达式 */
    @NotBlank(message = "Cron 表达式不能为空")
    @Size(max = 64, message = "Cron 表达式长度不能超过 64")
    private String cronExpression;

    /** 执行器 Bean 名称 */
    @NotBlank(message = "执行器 Bean 名称不能为空")
    @Size(max = 128, message = "执行器 Bean 名称长度不能超过 128")
    private String handlerBean;

    /** 执行器参数 */
    @Size(max = 512, message = "执行器参数长度不能超过 512")
    private String handlerParam;

    /** 错过执行策略：SMART / FIRE_ONCE / DO_NOTHING */
    @Size(max = 16, message = "错过执行策略长度不能超过 16")
    private String misfirePolicy;

    /** 失败重试次数上限 */
    @Min(value = 0, message = "重试次数上限不能小于 0")
    @Max(value = 5, message = "重试次数上限不能超过 5")
    private Integer maxRetry;

    /** 重试间隔秒数 */
    @Min(value = 0, message = "重试间隔不能小于 0")
    private Integer retryIntervalSeconds;

    /** 分片总数：1~16，-1 表示按在线实例动态分片 */
    @Min(value = -1, message = "分片总数取 1~16，或 -1 表示按在线实例动态分片")
    @Max(value = 16, message = "分片总数不能超过 16")
    private Integer shardTotal;

    /** 单次执行超时秒数：0 表示沿用全局默认 */
    @Min(value = 0, message = "执行超时不能小于 0")
    @Max(value = 86400, message = "执行超时不能超过 24 小时")
    private Integer timeoutSeconds;

    /** 连续失败自动暂停阈值：0 表示关闭熔断 */
    @Min(value = 0, message = "失败暂停阈值不能小于 0")
    @Max(value = 100, message = "失败暂停阈值不能超过 100")
    private Integer failThreshold;

    /** 任务状态：1 运行中 / 0 已停止 */
    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
