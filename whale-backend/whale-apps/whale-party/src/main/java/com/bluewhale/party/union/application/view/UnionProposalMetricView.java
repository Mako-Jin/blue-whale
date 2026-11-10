package com.bluewhale.party.union.application.view;

import lombok.Data;

/**
 * 提案办理统计视图（M-P4）。
 *
 * @author BlueWhale
 */
@Data
public class UnionProposalMetricView {

    /** 年度内提交的提案总数 */
    private long totalCount;

    /** 年度内提交且已反馈的提案数（办结分子） */
    private long closedCount;
}
