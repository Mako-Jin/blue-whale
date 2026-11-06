package com.bluewhale.mail.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收信规则视图。
 *
 * @author BlueWhale
 */
@Data
public class MailRuleVO {

    /** 规则ID */
    private Long id;

    /** 规则名称 */
    private String name;

    /** 优先级（小的先匹配） */
    private Integer sortNo;

    /** 是否启用 */
    private Boolean enabled;

    /** 发件人条件 */
    private String matchFrom;

    /** 主题条件 */
    private String matchSubject;

    /** 收件人条件 */
    private String matchTo;

    /** 是否仅匹配含附件邮件 */
    private Boolean onlyWithAttachment;

    /** 归入文件夹ID */
    private Long actionFolderId;

    /** 归入文件夹名称（回显用，文件夹被下线后为空） */
    private String actionFolderName;

    /** 是否标记已读 */
    private Boolean actionMarkSeen;

    /** 是否加星标 */
    private Boolean actionFlagged;

    /** 累计命中封数 */
    private Long hitCount;

    /** 最近命中时间 */
    private LocalDateTime lastHitTime;
}
