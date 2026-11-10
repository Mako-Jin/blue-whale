package com.bluewhale.party.activity.application.view;

import com.bluewhale.party.activity.domain.model.ActivitySignin;
import com.bluewhale.party.activity.domain.model.ActivityStatus;
import com.bluewhale.party.activity.domain.model.ActivityType;
import com.bluewhale.party.activity.domain.model.PartyActivity;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 组织生活视图。
 *
 * @author BlueWhale
 */
@Data
public class ActivityView {

    /** 组织生活 ID */
    private Long id;

    /** 所属支部 ID */
    private Long branchId;

    /** 所属支部名称 */
    private String branchName;

    /** 类型码 */
    private String activityType;

    /** 类型标签 */
    private String typeLabel;

    /** 活动主题 */
    private String title;

    /** 计划日期 */
    private LocalDate plannedDate;

    /** 实际召开日期 */
    private LocalDate heldDate;

    /** 活动地点 */
    private String location;

    /** 主持人 */
    private String hostName;

    /** 应到人数 */
    private Integer expectedCount;

    /** 到会人数 */
    private Long presentCount;

    /** 状态码 */
    private String status;

    /** 状态标签 */
    private String statusLabel;

    /** 会议纪要 */
    private String summary;

    /** 备注 */
    private String remark;

    /** 签到明细（仅详情接口返回） */
    private List<SigninView> signins;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 由领域模型构建视图（不含签到明细）。
     *
     * @param activity 组织生活
     * @return 视图
     */
    public static ActivityView from(PartyActivity activity) {
        ActivityView view = new ActivityView();
        view.setId(activity.getId());
        view.setBranchId(activity.getBranchId());
        view.setTitle(activity.getTitle());
        view.setPlannedDate(activity.getPlannedDate());
        view.setHeldDate(activity.getHeldDate());
        view.setLocation(activity.getLocation());
        view.setHostName(activity.getHostName());
        view.setExpectedCount(activity.getExpectedCount());
        view.setSummary(activity.getSummary());
        view.setRemark(activity.getRemark());
        view.setCreateTime(activity.getCreateTime());
        ActivityType type = activity.getActivityType() == null
                ? ActivityType.MEMBER_ASSEMBLY : activity.getActivityType();
        view.setActivityType(type.name());
        view.setTypeLabel(type.getLabel());
        ActivityStatus status = activity.getStatus() == null ? ActivityStatus.PLANNED : activity.getStatus();
        view.setStatus(status.name());
        view.setStatusLabel(status.getLabel());
        return view;
    }

    /**
     * 签到明细视图。
     *
     * @author BlueWhale
     */
    @Data
    public static class SigninView {

        /** 党员档案 ID */
        private Long memberId;

        /** 党员姓名 */
        private String memberName;

        /** 是否到会 */
        private boolean present;

        /** 缺席原因 */
        private String absentReason;

        /** 签到时间 */
        private LocalDateTime signedAt;

        /**
         * 由领域模型构建视图。
         *
         * @param signin 签到记录
         * @return 视图
         */
        public static SigninView from(ActivitySignin signin) {
            SigninView view = new SigninView();
            view.setMemberId(signin.getMemberId());
            view.setMemberName(signin.getMemberName());
            view.setPresent(signin.isPresent());
            view.setAbsentReason(signin.getAbsentReason());
            view.setSignedAt(signin.getSignedAt());
            return view;
        }
    }
}
