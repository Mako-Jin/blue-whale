package com.bluewhale.workflow.po.stat;

import lombok.Data;

import java.time.LocalDate;

/**
 * 按天聚合的实例数（政事殿概览 T5.6）。
 *
 * <p>发起与办结共用这一种形状：两条 SQL 分别按 {@code create_time} 与
 * {@code finished_at} 分组，结果都落到"日期 + 次数"。</p>
 *
 * @author BlueWhale
 */
@Data
public class ProcessDayCountPo {

    /** 日期（{@code DATE(create_time)} / {@code DATE(finished_at)} 的输出，仅用于分组标签） */
    private LocalDate day;

    /** 当日的实例数 */
    private Long total;
}
