package com.bluewhale.message.enums;

/**
 * 系统公告状态。
 *
 * @author BlueWhale
 */
public enum AnnouncementStatus {

    /** 已发布：收件箱惰性展开可见 */
    PUBLISHED,

    /** 已撤回：未展开用户不再看到；已展开的个人消息保留 */
    REVOKED
}
