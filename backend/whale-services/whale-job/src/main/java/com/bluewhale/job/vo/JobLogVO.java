package com.bluewhale.job.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定时任务执行日志视图对象（字段与 {@code job_log} 表一致）
 *
 * @author BlueWhale
 */
@Data
public class JobLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 任务ID */
    private Long jobId;

    /** 任务名称（冗余） */
    private String jobName;

    /** 执行器 Bean 名称（冗余） */
    private String handlerBean;

    /** 开始执行时间 */
    private LocalDateTime startTime;

    /** 执行结束时间 */
    private LocalDateTime endTime;

    /** 执行状态：1 成功 / 0 执行中 / -1 失败 */
    private Integer status;

    /** 本次执行已重试次数 */
    private Integer retryCount;

    /** 当前分片序号（从 0 开始） */
    private Integer shardIndex;

    /** 失败原因 */
    private String errorMsg;

    /** 执行结果 */
    private String result;

    /** 创建时间 */
    private LocalDateTime createTime;
}
