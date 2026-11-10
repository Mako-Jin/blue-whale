package com.bluewhale.party.union.application.view;

import com.bluewhale.party.union.domain.model.SignupStatus;
import com.bluewhale.party.union.domain.model.UnionSignup;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工会活动报名视图。
 *
 * @author BlueWhale
 */
@Data
public class UnionSignupView {

    /** 报名记录 ID */
    private Long id;

    /** 活动 ID */
    private Long activityId;

    /** 活动名称（列表回填） */
    private String activityTitle;

    /** 工会会员 ID */
    private Long unionMemberId;

    /** 会员姓名（回填） */
    private String memberName;

    /** 报名用户 ID */
    private Long userId;

    /** 报名状态码 */
    private String status;

    /** 报名时间 */
    private LocalDateTime signupAt;

    /** 取消时间 */
    private LocalDateTime cancelledAt;

    /**
     * 由领域模型构建视图。
     *
     * @param signup 报名记录
     * @return 视图
     */
    public static UnionSignupView from(UnionSignup signup) {
        return from(signup, null, null);
    }

    /**
     * 由领域模型构建视图并回填活动名称与会员姓名。
     *
     * @param signup 报名记录
     * @param activityTitle 活动名称
     * @param memberName 会员姓名
     * @return 视图
     */
    public static UnionSignupView from(UnionSignup signup, String activityTitle, String memberName) {
        UnionSignupView view = new UnionSignupView();
        view.setId(signup.getId());
        view.setActivityId(signup.getActivityId());
        view.setActivityTitle(activityTitle);
        view.setUnionMemberId(signup.getUnionMemberId());
        view.setMemberName(memberName);
        view.setUserId(signup.getUserId());
        SignupStatus status = signup.getStatus() == null ? SignupStatus.SIGNED : signup.getStatus();
        view.setStatus(status.name());
        view.setSignupAt(signup.getSignupAt());
        view.setCancelledAt(signup.getCancelledAt());
        return view;
    }
}
