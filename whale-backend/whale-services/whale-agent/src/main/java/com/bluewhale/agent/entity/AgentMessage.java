package com.bluewhale.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息实体，对应表 {@code agent_message}。
 *
 * <p>{@code role} 取 {@code user} / {@code assistant}；助手消息落 {@code citationsJson}
 * （引用块数组，元素含 {@code sourceId}、{@code path}、{@code startLine}、{@code endLine}）
 * 与 token 统计，便于前端证据面板回放。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_message")
public class AgentMessage extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属会话ID */
    private Long conversationId;

    /** 角色：user / assistant */
    private String role;

    /** 消息正文（Markdown） */
    private String content;

    /** 引用块 JSON 数组（仅 assistant 消息） */
    private String citationsJson;

    /** token 统计（prompt + completion，未配置 LLM 时为 0） */
    private Integer tokens;
}
