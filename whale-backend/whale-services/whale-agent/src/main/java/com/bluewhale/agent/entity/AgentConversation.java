package com.bluewhale.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会话实体，对应表 {@code agent_conversation}。
 *
 * <p>按用户隔离：{@code userId} 取自网关 {@code X-User-Id}，查询与删除均强制携带该条件。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_conversation")
public class AgentConversation extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属用户ID（网关注入的 X-User-Id） */
    private Long userId;

    /** 会话标题（默认取首个问题前 50 字） */
    private String title;

    /** 检索范围（逗号分隔：code,doc,sql,git,runtime；空表示全部） */
    private String scope;
}
