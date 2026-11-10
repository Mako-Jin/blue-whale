package com.bluewhale.party.union.application.view;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 即将开始的工会活动（活动通知扫描用）。
 *
 * @author BlueWhale
 */
@Data
public class UnionActivityNoticeView {

    /** 活动 ID */
    private Long activityId;

    /** 活动名称 */
    private String title;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 活动地点 */
    private String location;
}
