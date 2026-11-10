package com.bluewhale.party.union.application.view;

import lombok.Data;

/**
 * 工会活动统计视图（M-P4）。
 *
 * @author BlueWhale
 */
@Data
public class UnionActivityMetricView {

    /** 年度内已结束活动数 */
    private long closedCount;

    /** 年度内已结束活动的有效报名数（取消报名不计） */
    private long signupCount;
}
