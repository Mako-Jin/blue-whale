package com.bluewhale.job.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 定时任务执行日志分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JobLogQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 任务ID（精确匹配） */
    private Long jobId;

    /** 执行状态：1 成功 / 0 执行中 / -1 失败 */
    private Integer status;
}
