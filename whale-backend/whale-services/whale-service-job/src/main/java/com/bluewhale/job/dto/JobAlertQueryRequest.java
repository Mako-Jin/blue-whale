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

    /** 告警级别（精确匹配），如 ERROR / WARN / INFO */
    private String level;

    /**
     * 告警通道（模糊匹配）。
     *
     * <p>多通道场景下 {@code job_alert.channel} 为逗号拼接值（如 {@code DINGTALK,FEISHU}），
     * 因此按单个通道名模糊筛选，而不是精确相等。</p>
     */
    private String channel;
}
