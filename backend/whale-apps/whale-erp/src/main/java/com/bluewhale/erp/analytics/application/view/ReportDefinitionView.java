package com.bluewhale.erp.analytics.application.view;

import lombok.Data;

/**
 * 报表定义视图。
 *
 * @author BlueWhale
 */
@Data
public class ReportDefinitionView {

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
