package com.bluewhale.job.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 失败次数 TopN 条目。
 *
 * @author BlueWhale
 */
@Data
public class JobFailureTopVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private Long jobId;

    /** 任务名称 */
    private String jobName;

    /** 窗口内失败次数 */
    private long failureCount;
}
