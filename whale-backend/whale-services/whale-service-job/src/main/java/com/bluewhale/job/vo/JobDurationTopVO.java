package com.bluewhale.job.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 平均耗时 TopN 条目。
 *
 * @author BlueWhale
 */
@Data
public class JobDurationTopVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private Long jobId;

    /** 任务名称 */
    private String jobName;

    /** 窗口内平均耗时毫秒数 */
    private long avgDurationMillis;

    /** 窗口内执行次数（分母，便于判断样本量） */
    private long executionCount;
}
