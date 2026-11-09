package com.bluewhale.assistant.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 会话视图对象。
 *
 * @author BlueWhale
 */
@Data
@Builder
public class SessionVO {

    /** 会话ID（字符串） */
    private String id;

    /** 助手ID（字符串） */
    private String assistantId;

    /** 助手名称（联表冗余展示） */
    private String assistantName;

    /** 会话标题 */
    private String title;

    /** 来源：platform / embed / openapi */
    private String source;

    /** 业务侧会话引用 */
    private String channelRef;

    /** 创建时间（ISO-8601） */
    private String createTime;

    /** 更新时间（ISO-8601） */
    private String updateTime;
}
