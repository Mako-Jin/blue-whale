package com.bluewhale.assistant.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 用量看板行视图（助手×天聚合）。
 *
 * @author BlueWhale
 */
@Getter
@Builder
public class UsageVO {

    /** 助手ID */
    private String assistantId;

    /** 助手名称 */
    private String assistantName;

    /** 助手编码 */
    private String assistantCode;

    /** 统计日期（ISO-8601，如 2026-11-09） */
    private String statDate;

    /** 新增会话数 */
    private int sessionCount;

    /** 消息数 */
    private int messageCount;

    /** completion token 累计 */
    private long completionTokens;
}
