package com.bluewhale.job.po.stat;

import lombok.Data;

/**
 * 时间窗内的执行统计（时辰殿概览 T5.5）。
 *
 * @author BlueWhale
 */
@Data
public class JobRunStatPo {

    /** 已结束的执行次数（成功 + 失败） */
    private Long totalRuns;

    /** 其中成功次数 */
    private Long successRuns;

    /** 其中失败次数 */
    private Long failureRuns;
}
