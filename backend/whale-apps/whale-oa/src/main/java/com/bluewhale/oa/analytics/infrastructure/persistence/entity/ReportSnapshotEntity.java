package com.bluewhale.oa.analytics.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报表快照实体，对应表 {@code oa_report_snapshot}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_report_snapshot")
public class ReportSnapshotEntity extends BaseEntity {

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

    /** 快照内容（JSON） */
    private String payload;

    /** 生成人用户ID */
    private Long generatedBy;

    /** 生成人姓名快照 */
    private String generatedByName;

    /** 生成时间 */
    private LocalDateTime generatedAt;
}
