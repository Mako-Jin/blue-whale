package com.bluewhale.alert.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

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

    /** 记录状态（精确匹配）：FIRING 触发中 / RECOVERED 已恢复 / SILENCED 已静默 */
    private String status;

    /** 是否已认领：true 仅已认领 / false 仅未认领 / null 不限 */
    private Boolean acknowledged;

    /** 触发时间起始（含） */
    private LocalDateTime triggerTimeStart;

    /** 触发时间截止（含） */
    private LocalDateTime triggerTimeEnd;

    /** 内容关键字（模糊匹配） */
    private String keyword;

    /** 排序字段：triggerTime / createTime / recoverTime，默认 triggerTime */
    private String sortField;

    /** 排序方向：asc / desc，默认 desc */
    private String sortOrder;
}
