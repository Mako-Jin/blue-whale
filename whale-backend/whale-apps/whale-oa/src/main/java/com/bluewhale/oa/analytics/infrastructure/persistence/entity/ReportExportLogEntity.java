package com.bluewhale.oa.analytics.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报表导出记录实体，对应表 {@code oa_report_export_log}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_report_export_log")
public class ReportExportLogEntity extends BaseEntity {

    /** 报表编码 */
    private String reportCode;

    /** 报表名称快照 */
    private String reportName;

    /** 统计期间 */
    private String period;

    /** 起始日期 */
    private LocalDate fromDate;

    /** 截止日期 */
    private LocalDate toDate;

    /** 导出格式 */
    private String exportFormat;

    /** 导出行数 */
    private Integer rowCount;

    /** 导出人用户ID */
    private Long exportedBy;

    /** 导出人姓名快照 */
    private String exportedByName;

    /** 导出时间 */
    private LocalDateTime exportedAt;
}
