package com.bluewhale.alert.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 告警规则分页查询条件
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AlertRuleQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 关键字：规则名称 / 规则编码模糊匹配 */
    private String keyword;

    /** 监控指标（精确匹配） */
    private String metric;

    /** 告警级别（精确匹配） */
    private String level;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;
}
