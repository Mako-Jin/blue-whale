package com.bluewhale.im.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 消息视图对象。
 *
 * <p>主键类字段统一转字符串，避免前端 JavaScript 数字精度丢失。</p>
 *
 * @author BlueWhale
 */
@Data
@Builder
public class MessageVO {

    /** 消息ID（字符串） */
    private String id;

    /** 会话ID（字符串） */
    private String conversationId;

    /** 会话内序号 */
    private Long seq;

    /** 发送者ID（字符串） */
    private String senderId;

    /** 发送者类型：USER / BOT / SYSTEM */
    private String senderType;

    /** 内容类型：TEXT / MARKDOWN / IMAGE / FILE / CARD */
    private String contentType;

    /** 消息内容 */
    private String content;

    /** 客户端消息ID */
    private String clientMsgId;

    /** 被回复消息ID（字符串） */
    private String replyTo;

    /** 状态：1 正常 / 0 已撤回 */
    private Integer status;

    /** 创建时间（ISO-8601） */
    private String createTime;
}
