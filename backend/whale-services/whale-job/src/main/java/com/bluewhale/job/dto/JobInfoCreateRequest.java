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

    /** 分片总数，默认 1（不分片） */
    @Min(value = 1, message = "分片总数不能小于 1")
    @Max(value = 16, message = "分片总数不能超过 16")
    private Integer shardTotal;

    /** 任务状态：1 运行中 / 0 已停止，默认 0 */
    private Integer status;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
