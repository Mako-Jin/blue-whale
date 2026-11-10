package com.bluewhale.party.union.domain.model;

import com.bluewhale.party.common.PartyErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工会活动报名（领域模型）。
 *
 * <p>取消与再报名都作用在同一行记录：唯一键是「活动 + 会员」，
 * 复用一行才能保证名单不会因反复操作出现重复。</p>
 *
 * @author BlueWhale
 */
@Data
public class UnionSignup {

    /** 主键 */
    private Long id;

    /** 活动 ID */
    private Long activityId;

    /** 工会会员 ID */
    private Long unionMemberId;

    /** 报名用户 ID */
    private Long userId;

    /** 报名状态 */
    private SignupStatus status;

    /** 报名时间 */
    private LocalDateTime signupAt;

    /** 取消时间 */
    private LocalDateTime cancelledAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 取消报名（仅已报名 → 已取消）。
     */
    public void cancel() {
        if (status == null || !status.isSigned()) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_ACTIVITY_STATE_CONFLICT,
                    "只有已报名的记录可以取消");
        }
        status = SignupStatus.CANCELLED;
        cancelledAt = LocalDateTime.now();
    }

    /**
     * 重新报名（仅已取消 → 已报名）。
     *
     * <p>刷新报名时间并清空取消时间：名单上的「报名时间」要反映当前这次有效报名。</p>
     */
    public void resign() {
        if (status == null || !status.isCancelled()) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_ACTIVITY_STATE_CONFLICT,
                    "只有已取消的报名可以重新报名");
        }
        status = SignupStatus.SIGNED;
        signupAt = LocalDateTime.now();
        cancelledAt = null;
    }

    /**
     * 是否已报名。
     *
     * @return 已报名返回 true
     */
    public boolean isSigned() {
        return status == SignupStatus.SIGNED;
    }
}
