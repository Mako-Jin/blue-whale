package com.bluewhale.erp.analytics.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报表定义实体，对应表 {@code erp_bi_report_def}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_bi_report_def")
public class ReportDefinitionEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 报表编号 */
    private String reportNo;

    /** 报表名称 */
    private String reportName;

    /** 报表类型 */
    private String reportType;

    /** 维度 */
    private String dimensionType;

    /** 指标 */
    private String metricType;

    /** 过滤条件 JSON */
    private String filterJson;

    /** 状态 */
    private String status;

    /** 创建人 */
    private String owner;

    /** 备注 */
    private String remark;
}
