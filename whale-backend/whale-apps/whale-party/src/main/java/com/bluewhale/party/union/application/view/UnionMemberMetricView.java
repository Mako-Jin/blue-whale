package com.bluewhale.party.union.application.view;

import lombok.Data;

/**
 * 工会会员统计视图（M-P4）。
 *
 * @author BlueWhale
 */
@Data
public class UnionMemberMetricView {

    /** 在册会员数（覆盖率分子） */
    private long activeMembers;
}
