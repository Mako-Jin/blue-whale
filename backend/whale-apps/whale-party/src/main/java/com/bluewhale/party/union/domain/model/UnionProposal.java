package com.bluewhale.party.union.domain.model;

import com.bluewhale.party.common.PartyErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 职代会提案（领域模型）。
 *
 * <p>每次流转只写当前节点的字段与时间，不覆盖历史节点：办理人、反馈内容、
 * 评价时间都是不可变事实，重写会让审计链断掉。</p>
 *
 * @author BlueWhale
 */
@Data
public class UnionProposal {

    /** 标题最大长度 */
    public static final int TITLE_MAX_LENGTH = 128;

    /** 内容最大长度 */
    public static final int CONTENT_MAX_LENGTH = 5000;

    /** 办理人最大长度 */
    public static final int HANDLER_MAX_LENGTH = 64;

    /** 反馈内容最大长度 */
    public static final int FEEDBACK_MAX_LENGTH = 500;

    /** 评分下限 */
    public static final int RATING_MIN = 1;

    /** 评分上限 */
    public static final int RATING_MAX = 5;

    /** 主键 */
    private Long id;

    /** 提案编号（租户内唯一，取号生成） */
    private String proposalNo;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 提交人用户 ID */
    private Long submitterUserId;

    /** 提交人姓名（提交时快照） */
    private String submitterName;

    /** 状态 */
    private ProposalStatus status;

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

    /** 满意度评分（1–5） */
    private Integer rating;

    /** 评价时间 */
    private LocalDateTime ratedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 校验并归一字段。
     */
    public void validate() {
        title = requireText(title, "提案标题", TITLE_MAX_LENGTH);
        content = requireText(content, "提案内容", CONTENT_MAX_LENGTH);
        if (status == null) {
            status = ProposalStatus.SUBMITTED;
        }
    }

    /**
     * 立案（仅已提交 → 已立案）。
     */
    public void file() {
        file(null);
    }

    /**
     * 立案并可同时指定办理人（仅已提交 → 已立案）。
     *
     * @param handler 办理人（可空）
     */
    public void file(String handler) {
        if (status == null || status != ProposalStatus.SUBMITTED) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_PROPOSAL_STATE_CONFLICT,
                    "只有已提交的提案可以立案");
        }
        this.handler = limitOptional(handler, "办理人", HANDLER_MAX_LENGTH);
        status = ProposalStatus.FILED;
        filedAt = LocalDateTime.now();
    }

    /**
     * 办理（仅已立案 → 办理中）。
     *
     * @param handler 办理人
     */
    public void handle(String handler) {
        if (status == null || status != ProposalStatus.FILED) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_PROPOSAL_STATE_CONFLICT,
                    "只有已立案的提案可以办理");
        }
        this.handler = requireText(handler, "办理人", HANDLER_MAX_LENGTH);
        status = ProposalStatus.HANDLING;
        handledAt = LocalDateTime.now();
    }

    /**
     * 反馈（仅办理中 → 已反馈）。
     *
     * @param text 反馈内容
     */
    public void feedback(String text) {
        if (status == null || status != ProposalStatus.HANDLING) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_PROPOSAL_STATE_CONFLICT,
                    "只有办理中的提案可以反馈");
        }
        this.feedback = requireText(text, "反馈内容", FEEDBACK_MAX_LENGTH);
        status = ProposalStatus.FEEDBACK;
        feedbackAt = LocalDateTime.now();
    }

    /**
     * 评价（仅已反馈 → 已评价，终态）。
     *
     * @param score 满意度评分（1–5）
     */
    public void rate(Integer score) {
        if (status == null || status != ProposalStatus.FEEDBACK) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_PROPOSAL_STATE_CONFLICT,
                    "只有已反馈的提案可以评价");
        }
        if (score == null || score < RATING_MIN || score > RATING_MAX) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_PROPOSAL_INVALID,
                    "评分应在 " + RATING_MIN + "–" + RATING_MAX + " 之间");
        }
        rating = score;
        status = ProposalStatus.RATED;
        ratedAt = LocalDateTime.now();
    }

    /**
     * 是否终态。
     *
     * @return 已评价返回 true
     */
    public boolean isTerminal() {
        return status == ProposalStatus.RATED;
    }

    /**
     * 必填文本校验。
     *
     * @param value 原值
     * @param label 字段名
     * @param maxLength 最大长度
     * @return 去空白后的文本
     */
    private String requireText(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_PROPOSAL_INVALID, label + "不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_PROPOSAL_INVALID,
                    label + "不得超过 " + maxLength + " 字符");
        }
        return trimmed;
    }

    /**
     * 选填文本校验。
     *
     * @param value 原值
     * @param label 字段名
     * @param maxLength 最大长度
     * @return 去空白后的文本（空值为 null）
     */
    private String limitOptional(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_PROPOSAL_INVALID,
                    label + "不得超过 " + maxLength + " 字符");
        }
        return trimmed;
    }
}
