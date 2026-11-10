package com.bluewhale.party.activity.application.view;

import lombok.Data;

import java.time.LocalDate;

/**
 * 即将开始的党务活动（活动通知扫描用）。
 *
 * @author BlueWhale
 */
@Data
public class ActivityNoticeView {

    /** 组织生活 ID */
    private Long activityId;

    /** 活动标题 */
    private String title;

    /** 所属支部 ID */
    private Long branchId;

    /** 所属支部名称 */
    private String branchName;

    /** 计划日期 */
    private LocalDate plannedDate;
}
