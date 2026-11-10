package com.bluewhale.alert.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 告警记录分页查询条件
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AlertRecordQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 规则编码（精确匹配） */
    private String ruleCode;

    /** 告警级别（精确匹配）：INFO / WARN / ERROR / CRITICAL */
    private String alertLevel;

    /** 记录状态（精确匹配）：FIRING 触发中 / RECOVERED 已恢复 */
    private String status;
}
