package com.bluewhale.oa.analytics.domain.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报表快照（某一统计期间的聚合结果）。
 *
 * @author BlueWhale
 */
@Data
public class ReportSnapshot {

    /** 主键ID */
    private Long id;

    /** 报表编码 */
    private String reportCode;

    /** 报表名称快照 */
    private String reportName;

    /** 统计期间（YYYY-MM） */
    private String period;

    /** 起始日期 */
    private LocalDate fromDate;

    /** 截止日期 */
    private LocalDate toDate;

    /** 数据行数 */
    private Integer rowCount;

    /** 快照内容（列定义与数据行的 JSON） */
    private String payload;

    /** 生成人用户ID */
    private Long generatedBy;

    /** 生成人姓名快照 */
    private String generatedByName;

    /** 生成时间 */
    private LocalDateTime generatedAt;
}
