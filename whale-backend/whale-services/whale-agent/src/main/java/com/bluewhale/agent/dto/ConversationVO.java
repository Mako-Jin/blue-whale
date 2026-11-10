package com.bluewhale.agent.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 会话视图对象。
 *
 * @author BlueWhale
 */
@Data
@Builder
public class ConversationVO {

    /** 会话ID（字符串，避免前端精度丢失） */
    private String id;

    /** 会话标题 */
    private String title;

    /** 检索范围 */
    private String scope;

    /** 创建时间（ISO-8601） */
    private String createTime;

    /** 更新时间（ISO-8601） */
    private String updateTime;
}
