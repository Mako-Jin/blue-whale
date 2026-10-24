package com.bluewhale.im.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 会话成员视图对象。
 *
 * <p>用户ID以字符串输出，避免前端 JavaScript 数字精度丢失。</p>
 *
 * @author BlueWhale
 */
@Data
@Builder
public class MemberVO {

    /** 用户ID（字符串） */
    private String userId;

    /** 成员类型：USER / BOT */
    private String memberType;

    /** 会话角色：OWNER / ADMIN / MEMBER */
    private String role;

    /** 已读游标 */
    private Long lastReadSeq;

    /** 禁言截止时间（ISO-8601，可空） */
    private String muteUntil;

    /** 加入时间（ISO-8601） */
    private String joinTime;
}
