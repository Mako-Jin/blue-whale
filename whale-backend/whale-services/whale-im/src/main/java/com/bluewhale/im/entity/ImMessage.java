package com.bluewhale.im.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息实体，对应表 {@code im_message}。
 *
 * <p>{@code (conversationId, clientMsgId)} 唯一索引保证客户端重发幂等；
 * {@code (conversationId, seq)} 唯一索引保证会话内序号不重复。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("im_message")
public class ImMessage extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 会话ID（逻辑关联 im_conversation.id） */
    private Long conversationId;

    /** 会话内单调递增序号（Redis INCR 分配） */
    private Long seq;

    /** 发送者ID / BOT 用户ID（逻辑关联 sys_user.id） */
    private Long senderId;

    /** 发送者类型：USER / BOT / SYSTEM */
    private String senderType;

    /** 内容类型：TEXT / MARKDOWN / IMAGE / FILE / CARD */
    private String contentType;

    /** 消息内容（文本原文或附件/卡片 JSON 引用） */
    private String content;

    /** 客户端消息ID（幂等键；服务端生成消息可为空） */
    private String clientMsgId;

    /** 被回复消息ID（逻辑关联 im_message.id） */
    private Long replyTo;

    /** 状态：1 正常 / 0 已撤回 */
    private Integer status;
}
