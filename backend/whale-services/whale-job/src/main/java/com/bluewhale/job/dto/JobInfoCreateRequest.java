package com.bluewhale.job.dto;

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

    /** 任务状态：1 运行中 / 0 已停止，默认 0 */
    private Integer status;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
