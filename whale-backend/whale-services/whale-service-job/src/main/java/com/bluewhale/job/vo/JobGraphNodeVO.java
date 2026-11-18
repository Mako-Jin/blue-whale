package com.bluewhale.job.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * DAG 拓扑图节点视图对象（精简字段，避免图接口携带执行参数等大字段）。
 *
 * @author BlueWhale
 */
@Data
public class JobGraphNodeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private Long id;

    /** 任务名称 */
    private String jobName;

    /** 任务分组 */
    private String jobGroup;

    /** 任务状态：1 运行中 / 0 已停止 */
    private Integer status;

    /** 执行器应用名 */
    private String executorApp;

    /** 任务编码 */
    private String jobCode;

    /** Cron 表达式 */
    private String cronExpression;

    /** 分片总数 */
    private Integer shardTotal;

    /** 单次执行超时秒数 */
    private Integer timeoutSeconds;

    /** 连续失败自动暂停阈值 */
    private Integer failThreshold;

    /** 当前连续失败次数 */
    private Integer failCount;
}
