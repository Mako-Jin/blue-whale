package com.bluewhale.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 助手消息实体，对应表 {@code asst_message}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("asst_message")
public class AsstMessage extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属会话ID（asst_session.id） */
    private Long sessionId;

    /** 角色：user / assistant / system */
    private String role;

    /** 消息正文（Markdown） */
    private String content;

    /** 引用块 JSON 数组（仅 assistant 消息，SSE done 帧回写） */
    private String citationsJson;

    /** token 统计（prompt + completion，whale-ai 估算值） */
    private Integer tokens;

    /** 本条回答耗时（毫秒） */
    private Integer elapsedMs;

    /** 是否降级回答：0 正常 / 1 降级 */
    private Integer degraded;
}
