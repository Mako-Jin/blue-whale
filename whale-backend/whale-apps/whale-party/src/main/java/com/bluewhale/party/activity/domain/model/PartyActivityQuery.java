package com.bluewhale.party.activity.domain.model;

import lombok.Data;

import java.time.LocalDate;

/**
 * 组织生活查询条件。
 *
 * @author BlueWhale
 */
@Data
public class PartyActivityQuery {

    /** 关键字（主题模糊） */
    private String keyword;

    /** 所属支部 ID */
    private Long branchId;

    /** 类型 */
    private String activityType;

    /** 状态 */
    private String status;

    /** 计划日期起（含） */
    private LocalDate from;

    /** 计划日期止（含） */
    private LocalDate to;

    /** 页码（从 1 起） */
    private Long current = 1L;

    /** 每页条数 */
    private Long size = 20L;
}
