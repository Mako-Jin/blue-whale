package com.bluewhale.job.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 执行日志统计查询条件。
 *
 * @author BlueWhale
 */
@Data
public class JobLogStatsQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务ID（精确匹配；指定后不返回 TopN） */
    private Long jobId;

    /** 任务分组（按 job_info 解析任务集合后过滤） */
    private String jobGroup;

    /** 统计开始时间（含），为空时取结束时间前 7 天 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime from;

    /** 统计结束时间（含），为空时取当前时间 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime to;
}
