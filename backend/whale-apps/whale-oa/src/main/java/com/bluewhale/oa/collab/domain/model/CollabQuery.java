package com.bluewhale.oa.collab.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 协同业务分页查询。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CollabQuery extends PageQuery {

    /** 关键字（名称 / 单号 / 标题 / 主题） */
    private String keyword;

    /** 状态 */
    private String status;

    /** 任务优先级 */
    private String priority;

    /** 任务负责人用户ID */
    private Long ownerId;

    /** 会议室ID */
    private Long roomId;

    /** 公告范围 */
    private String scopeType;

    /** 任务来源 */
    private String source;

    /** 参会人用户ID（用于“我参与的会议”过滤） */
    private Long participantId;

    /** 日期起（含） */
    private LocalDate dateFrom;

    /** 日期止（含） */
    private LocalDate dateTo;

    /** 当前用户ID（我的公告/我的任务） */
    private Long userId;
}
