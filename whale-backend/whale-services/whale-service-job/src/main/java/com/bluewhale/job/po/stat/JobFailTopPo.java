package com.bluewhale.job.po.stat;

import lombok.Data;

/**
 * 按任务聚合的失败次数（时辰殿概览 T5.5）。
 *
 * @author BlueWhale
 */
@Data
public class JobFailTopPo {

    /** 任务名称（job_log 里的冗余列，历史数据可能为空） */
    private String jobName;

    /** 失败次数 */
    private Long failures;
}
