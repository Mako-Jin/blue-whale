package com.bluewhale.im.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 会话成员实体，对应表 {@code im_conversation_member}。
 *
 * <p>成员身份与已读游标合一：{@code lastReadSeq} 即该成员已读到的最大消息序号，
 * 未读数 = 会话 {@code lastSeq} - {@code lastReadSeq}。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("im_conversation_member")
public class ImConversationMember extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 会话ID（逻辑关联 im_conversation.id） */
    private Long conversationId;

    /** 用户ID / BOT 用户ID（逻辑关联 sys_user.id） */
    private Long userId;

    /** 成员类型：USER / BOT */
    private String memberType;

    /** 会话角色：OWNER / ADMIN / MEMBER */
    private String role;

    /** 已读游标：已读到的最大消息序号 */
    private Long lastReadSeq;

    /** 禁言截止时间（NULL 表示未禁言） */
    private LocalDateTime muteUntil;

    /** 加入时间 */
    private LocalDateTime joinTime;
}
