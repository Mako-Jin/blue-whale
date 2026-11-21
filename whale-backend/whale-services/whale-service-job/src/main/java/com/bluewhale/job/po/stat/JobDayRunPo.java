package com.bluewhale.job.po.stat;

import lombok.Data;

import java.time.LocalDate;

/**
 * 按天聚合的执行次数（时辰殿概览 T5.5）。
 *
 * @author BlueWhale
 */
@Data
public class JobDayRunPo {

    /** 日期（{@code DATE(start_time)} 的输出，仅用于分组标签） */
    private LocalDate day;

    /** 当日已结束的执行次数 */
    private Long runs;
}
