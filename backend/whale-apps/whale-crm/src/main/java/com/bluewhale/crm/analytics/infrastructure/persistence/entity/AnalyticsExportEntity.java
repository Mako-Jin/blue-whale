package com.bluewhale.crm.analytics.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分析导出任务实体，对应表 {@code crm_analytics_export}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_analytics_export")
public class AnalyticsExportEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 导出单号 */
    private String exportNo;

    /** 导出类型 */
    private String exportType;

    /** 筛选条件 JSON */
    private String filtersJson;

    /** 状态 */
    private String status;

    /** 数据行数 */
    private Integer rowCount;

    /** CSV 内容 */
    private String content;

    /** 失败原因 */
    private String error;

    /** 发起人 */
    private Long requestedBy;
}
