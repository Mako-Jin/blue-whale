package com.bluewhale.crm.customer.application.view;

import com.bluewhale.crm.customer.domain.model.CustomerFollowUp;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 跟进记录视图。
 *
 * @author BlueWhale
 */
@Data
public class FollowUpView {

    /** 主键 */
    private Long id;

    /** 客户 ID */
    private Long customerId;

    /** 商机 ID */
    private Long opportunityId;

    /** 渠道 */
    private String channel;

    /** 内容 */
    private String content;

    /** 结果 */
    private String result;

    /** 下次动作 */
    private String nextAction;

    /** 下次动作时间 */
    private LocalDateTime nextActionAt;

    /** 记录人 */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 由领域模型构建视图。
     *
     * @param followUp 跟进记录
     * @return 跟进视图
     */
    public static FollowUpView from(CustomerFollowUp followUp) {
        FollowUpView view = new FollowUpView();
        view.setId(followUp.getId());
        view.setCustomerId(followUp.getCustomerId());
        view.setOpportunityId(followUp.getOpportunityId());
        view.setChannel(followUp.getChannel());
        view.setContent(followUp.getContent());
        view.setResult(followUp.getResult());
        view.setNextAction(followUp.getNextAction());
        view.setNextActionAt(followUp.getNextActionAt());
        view.setCreatedBy(followUp.getCreatedBy());
        view.setCreateTime(followUp.getCreateTime());
        return view;
    }
}
