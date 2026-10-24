package com.bluewhale.im.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会话与智能体绑定实体（{@code im_agent_binding}）。
 *
 * <p>一条绑定表示某会话由某机器人承接，并指定智能体场景；机器人会话与群内 @ 触发
 * 都依赖本表定位「该由哪个机器人、走哪个场景」。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("im_agent_binding")
public class ImAgentBinding extends BaseEntity {

    /** 会话ID */
    private Long conversationId;

    /** 机器人用户ID */
    private Long botUserId;

    /** 智能体场景：DEFAULT / ARCH / DATA / OPS */
    private String agentScenario;

    /** 智能体会话ID（透传 whale-agent 的会话标识） */
    private String agentConversationId;

    /** 是否启用：1 启用 / 0 停用 */
    private Integer enabled;
}
