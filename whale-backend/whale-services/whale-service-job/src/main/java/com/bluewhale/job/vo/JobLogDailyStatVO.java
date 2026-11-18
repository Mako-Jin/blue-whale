package com.bluewhale.job.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 按天聚合的执行统计。
 *
 * @author BlueWhale
 */
@Data
public class JobLogDailyStatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 统计日期（按 start_time 的服务端本地日期分桶） */
    private LocalDate date;

    /** 成功执行数 */
    private long successCount;

    /** 失败执行数 */
    private long failureCount;

    /** 平均耗时毫秒数（含成功与失败） */
    private long avgDurationMillis;

    /** 耗时 P95（毫秒，离散口径：升序第 ceil(n×0.95) 个） */
    private long p95DurationMillis;
}
