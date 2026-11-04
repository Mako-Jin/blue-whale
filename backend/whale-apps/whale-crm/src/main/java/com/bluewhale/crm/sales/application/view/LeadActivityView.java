package com.bluewhale.crm.sales.application.view;

import com.bluewhale.crm.sales.domain.model.LeadActivity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 线索活动视图。
 *
 * @author BlueWhale
 */
@Data
public class LeadActivityView {

    /** 主键 */
    private Long id;

    /** 线索 ID */
    private Long leadId;

    /** 渠道 */
    private String channel;

    /** 内容 */
    private String content;

    /** 结果 */
    private String result;

    /** 下次行动时间 */
    private LocalDateTime nextActionAt;

    /** 记录人 */
    private Long createdBy;

    /** 记录时间 */
    private LocalDateTime createTime;

    /**
     * 领域模型转视图。
     *
     * @param activity 活动
     * @return 视图
     */
    public static LeadActivityView from(LeadActivity activity) {
        LeadActivityView view = new LeadActivityView();
        view.setId(activity.getId());
        view.setLeadId(activity.getLeadId());
        view.setChannel(activity.getChannel());
        view.setContent(activity.getContent());
        view.setResult(activity.getResult());
        view.setNextActionAt(activity.getNextActionAt());
        view.setCreatedBy(activity.getCreatedBy());
        view.setCreateTime(activity.getCreateTime());
        return view;
    }
}
