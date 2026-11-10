package com.bluewhale.party.union.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 职代会提案实体，对应表 {@code pty_union_proposal}。
 *
 * <p>各节点时间显式映射列名：它们是流转顺序的证据，
 * 列名错位会让「立案/办理/反馈/评价」时间互相错配。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pty_union_proposal")
public class UnionProposalEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 提案编号（租户内唯一） */
    @TableField("proposal_no")
    private String proposalNo;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 提交人用户 ID */
    @TableField("submitter_user_id")
    private Long submitterUserId;

    /** 提交人姓名快照 */
    private String submitterName;

    /** 状态 */
    private String status;

    /** 办理人 */
    private String handler;

    /** 立案时间 */
    @TableField("filed_at")
    private LocalDateTime filedAt;

    /** 办理时间 */
    @TableField("handled_at")
    private LocalDateTime handledAt;

    /** 反馈内容 */
    private String feedback;

    /** 反馈时间 */
    @TableField("feedback_at")
    private LocalDateTime feedbackAt;

    /** 满意度评分 */
    private Integer rating;

    /** 评价时间 */
    @TableField("rated_at")
    private LocalDateTime ratedAt;
}
