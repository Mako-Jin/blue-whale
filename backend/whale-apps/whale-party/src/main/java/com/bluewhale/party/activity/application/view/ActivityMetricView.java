package com.bluewhale.party.activity.application.view;

import lombok.Data;

/**
 * 组织生活统计视图（M-P4）。
 *
 * @author BlueWhale
 */
@Data
public class ActivityMetricView {

    /** 当月计划场次（按计划日期口径） */
    private long plannedCount;

    /** 当月已召开场次（按实际召开日期口径） */
    private long heldCount;

    /** 已召开场次的签到记录总数（参会率分母） */
    private long signinCount;

    /** 已召开场次的实到人数（参会率分子） */
    private long presentCount;
}
