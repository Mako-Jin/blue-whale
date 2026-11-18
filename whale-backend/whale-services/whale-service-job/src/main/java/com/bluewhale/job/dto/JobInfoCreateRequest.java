package com.bluewhale.job.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增定时任务请求。
 *
 * @author BlueWhale
 */
@Data
public class JobInfoCreateRequest {

    /** 任务名称 */
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 64, message = "任务名称长度不能超过 64")
    private String jobName;

    /** 任务分组，默认 DEFAULT */
    @Size(max = 64, message = "任务分组长度不能超过 64")
    private String jobGroup;

    /** 执行器应用名（Nacos 服务名）：配置后按注册表寻址，无需把地址写进任务参数 */
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

    /** 错过执行策略：SMART / FIRE_ONCE / DO_NOTHING，默认 SMART */
    @Size(max = 16, message = "错过执行策略长度不能超过 16")
    private String misfirePolicy;

    /** 失败重试次数上限，默认 0（不重试） */
    @Min(value = 0, message = "重试次数上限不能小于 0")
    @Max(value = 5, message = "重试次数上限不能超过 5")
    private Integer maxRetry;

    /** 重试间隔秒数，默认 60 */
    @Min(value = 0, message = "重试间隔不能小于 0")
    private Integer retryIntervalSeconds;

    /**
     * 分片总数，默认 1（不分片）。
     *
     * <p>取 {@code -1} 表示动态分片：每次执行按该任务当前可派发的执行器实例数决定分片数，
     * 扩容缩容后自动跟随，无需改配置。</p>
     */
    @Min(value = -1, message = "分片总数取 1~16，或 -1 表示按在线实例动态分片")
    @Max(value = 16, message = "分片总数不能超过 16")
    private Integer shardTotal;

    /** 单次执行超时秒数：0 表示沿用全局默认（默认 0，即不限制） */
    @Min(value = 0, message = "执行超时不能小于 0")
    @Max(value = 86400, message = "执行超时不能超过 24 小时")
    private Integer timeoutSeconds;

    /** 连续失败自动暂停阈值：0 表示关闭熔断（默认 0） */
    @Min(value = 0, message = "失败暂停阈值不能小于 0")
    @Max(value = 100, message = "失败暂停阈值不能超过 100")
    private Integer failThreshold;

    /** 任务状态：1 运行中 / 0 已停止，默认 0 */
    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
