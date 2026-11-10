package com.bluewhale.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 回答评价实体，对应表 {@code agent_feedback}。
 *
 * <p>用于黄金集之外的线上质量回流：{@code rating} 取 1（👍）/ -1（👎）。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_feedback")
public class AgentFeedback extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 被评价的助手消息ID */
    private Long messageId;

    /** 评价用户ID */
    private Long userId;

    /** 评价：1 有用 / -1 无用 */
    private Integer rating;

    /** 评价备注 */
    private String comment;
}
