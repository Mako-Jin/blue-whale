package com.bluewhale.job.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定时任务视图对象（字段与 {@code job_info} 表一致）
 *
 * @author BlueWhale
 */
@Data
public class JobInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 任务名称 */
    private String jobName;

    /** 任务分组 */
    private String jobGroup;

    /** 执行器应用名（Nacos 服务名） */
    private String executorApp;

    /** 任务编码（对应业务侧 @ScheduledJob.code） */
    private String jobCode;

    /** Cron 执行表达式 */
    private String cronExpression;

    /** 执行器 Bean 名称 */
    private String handlerBean;

    /** 执行器参数 */
    private String handlerParam;

    /** 错过执行策略 */
    private String misfirePolicy;

    /** 失败重试次数上限 */
    private Integer maxRetry;

    /** 重试间隔秒数 */
    private Integer retryIntervalSeconds;

    /** 分片总数 */
    private Integer shardTotal;

    /** 单次执行超时秒数：0 表示沿用全局默认 */
    private Integer timeoutSeconds;

    /** 连续失败自动暂停阈值：0 表示关闭熔断 */
    private Integer failThreshold;

    /** 当前连续失败次数（成功后清零） */
    private Integer failCount;

    /** 任务状态：1 运行中 / 0 已停止 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 下次执行时间：cron 合法时按当前时间推算，否则为 null */
    private LocalDateTime nextFireTime;
}
