package com.bluewhale.job.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 定时任务分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JobInfoQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 关键字：任务名称模糊匹配 */
    private String keyword;

    /** 任务分组（精确匹配） */
    private String jobGroup;

    /** 任务状态：1 运行中 / 0 已停止 */
    private Integer status;
}
