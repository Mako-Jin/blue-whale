package com.bluewhale.party.union.application.view;

import com.bluewhale.party.union.domain.model.ProposalStatus;
import com.bluewhale.party.union.domain.model.UnionProposal;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 职代会提案视图。
 *
 * @author BlueWhale
 */
@Data
public class UnionProposalView {

    /** 提案 ID */
    private Long id;

    /** 提案编号 */
    private String proposalNo;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 提交人用户 ID */
    private Long submitterUserId;

    /** 提交人姓名 */
    private String submitterName;

    /** 状态码 */
    private String status;

    /** 办理人 */
    private String handler;

    /** 立案时间 */
    private LocalDateTime filedAt;

    /** 办理时间 */
    private LocalDateTime handledAt;

    /** 反馈内容 */
    private String feedback;

    /** 反馈时间 */
    private LocalDateTime feedbackAt;

    /** 满意度评分 */
    private Integer rating;

    /** 评价时间 */
    private LocalDateTime ratedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 由领域模型构建视图。
     *
     * @param proposal 提案
     * @return 视图
     */
    public static UnionProposalView from(UnionProposal proposal) {
        UnionProposalView view = new UnionProposalView();
        view.setId(proposal.getId());
        view.setProposalNo(proposal.getProposalNo());
        view.setTitle(proposal.getTitle());
        view.setContent(proposal.getContent());
        view.setSubmitterUserId(proposal.getSubmitterUserId());
        view.setSubmitterName(proposal.getSubmitterName());
        ProposalStatus status = proposal.getStatus() == null
                ? ProposalStatus.SUBMITTED : proposal.getStatus();
        view.setStatus(status.name());
        view.setHandler(proposal.getHandler());
        view.setFiledAt(proposal.getFiledAt());
        view.setHandledAt(proposal.getHandledAt());
        view.setFeedback(proposal.getFeedback());
        view.setFeedbackAt(proposal.getFeedbackAt());
        view.setRating(proposal.getRating());
        view.setRatedAt(proposal.getRatedAt());
        view.setCreateTime(proposal.getCreateTime());
        return view;
    }
}
