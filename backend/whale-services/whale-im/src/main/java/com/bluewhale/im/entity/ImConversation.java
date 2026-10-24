package com.bluewhale.im.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 会话实体，对应表 {@code im_conversation}。
 *
 * <p>会话类型：{@code SINGLE} 单聊 / {@code GROUP} 群聊 / {@code BOT} 机器人会话；
 * 会话列表按 {@code lastActiveAt} 倒序。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("im_conversation")
public class ImConversation extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 会话类型：SINGLE / GROUP / BOT */
    private String type;

    /** 会话标题（群聊必填；单聊为空由前端按对方展示） */
    private String title;

    /** 会话创建人ID（逻辑关联 sys_user.id） */
    private Long ownerId;

    /** 幂等键：单聊=用户对、机器人会话=用户ID、群聊为空 */
    private String pairKey;

    /** 当前最大消息序号 */
    private Long lastSeq;

    /** 最后一条消息ID（逻辑关联 im_message.id） */
    private Long lastMessageId;

    /** 最后活跃时间 */
    private LocalDateTime lastActiveAt;
}
