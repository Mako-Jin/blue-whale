package com.bluewhale.job.po.stat;

import lombok.Data;

/**
 * 任务总数与运行中数量（时辰殿概览 T5.5）。
 *
 * @author BlueWhale
 */
@Data
public class JobCountPo {

    /** 在册任务数（未逻辑删除） */
    private Long jobs;

    /** 其中处于运行中的任务数 */
    private Long running;
}
