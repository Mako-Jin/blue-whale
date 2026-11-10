package com.bluewhale.im.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 会话视图对象（会话列表用）。
 *
 * @author BlueWhale
 */
@Data
@Builder
public class ConversationVO {

    /** 会话ID（字符串） */
    private String id;

    /** 会话类型：SINGLE / GROUP / BOT */
    private String type;

    /** 会话标题 */
    private String title;

    /** 会话创建人ID（字符串） */
    private String ownerId;

    /** 当前最大消息序号 */
    private Long lastSeq;

    /** 未读消息数（{@code lastSeq - lastReadSeq}，不小于 0） */
    private Long unread;

    /** 最后活跃时间（ISO-8601） */
    private String lastActiveAt;

    /** 最后一条消息摘要（无消息时为 {@code null}） */
    private MessageVO lastMessage;
}
