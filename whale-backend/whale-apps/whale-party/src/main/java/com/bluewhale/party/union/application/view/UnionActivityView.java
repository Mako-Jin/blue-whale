package com.bluewhale.party.union.application.view;

import com.bluewhale.party.union.domain.model.UnionActivity;
import com.bluewhale.party.union.domain.model.UnionActivityStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工会活动视图。
 *
 * @author BlueWhale
 */
@Data
public class UnionActivityView {

    /** 活动 ID */
    private Long id;

    /** 活动编号 */
    private String activityNo;

    /** 活动名称 */
    private String title;

    /** 活动类型 */
    private String activityType;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 活动地点 */
    private String location;

    /** 名额上限，0 表示不限 */
    private Integer quota;

    /** 状态码 */
    private String status;

    /** 备注 */
    private String remark;

    /** 已报名人数（详情与动作返回，列表可空） */
    private Long signupCount;

    /**
     * 由领域模型构建视图。
     *
     * @param activity 活动
     * @return 视图
     */
    public static UnionActivityView from(UnionActivity activity) {
        UnionActivityView view = new UnionActivityView();
        view.setId(activity.getId());
        view.setActivityNo(activity.getActivityNo());
        view.setTitle(activity.getTitle());
        view.setActivityType(activity.getActivityType());
        view.setStartTime(activity.getStartTime());
        view.setEndTime(activity.getEndTime());
        view.setLocation(activity.getLocation());
        view.setQuota(activity.getQuota());
        UnionActivityStatus status = activity.getStatus() == null
                ? UnionActivityStatus.DRAFT : activity.getStatus();
        view.setStatus(status.name());
        view.setRemark(activity.getRemark());
        return view;
    }
}
