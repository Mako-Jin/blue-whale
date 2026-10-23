package com.bluewhale.job.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务告警记录分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JobAlertQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 任务ID（精确匹配） */
    private Long jobId;

    /** 告警级别（精确匹配），如 ERROR */
    private String level;
}
