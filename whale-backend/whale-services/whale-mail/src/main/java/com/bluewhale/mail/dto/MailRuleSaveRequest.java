package com.bluewhale.mail.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 收信规则保存请求。
 *
 * @author BlueWhale
 */
@Data
public class MailRuleSaveRequest {

    /** 规则ID（为空表示新建） */
    private Long id;

    /** 所属账号ID */
    private Long accountId;

    /** 规则名称 */
    @NotBlank(message = "{validation.mail.rule.name-required}")
    @Size(max = 64, message = "{validation.mail.rule.name-size}")
    private String name;

    /** 优先级（小的先匹配） */
    @Min(value = 1, message = "{validation.mail.rule.priority-min}")
    private Integer sortNo;

    /** 是否启用 */
    private Boolean enabled;

    /** 发件人包含（逗号分隔多值） */
    @Size(max = 190, message = "{validation.mail.rule.match-from-size}")
    private String matchFrom;

    /** 主题包含（逗号分隔多值） */
    @Size(max = 255, message = "{validation.mail.rule.match-subject-size}")
    private String matchSubject;

    /** 收件人包含（逗号分隔多值） */
    @Size(max = 190, message = "{validation.mail.rule.match-to-size}")
    private String matchTo;

    /** 仅匹配含附件的邮件 */
    private Boolean onlyWithAttachment;

    /** 归入文件夹ID（空表示不移动） */
    private Long actionFolderId;

    /** 标记已读 */
    private Boolean actionMarkSeen;

    /** 加星标 */
    private Boolean actionFlagged;
}
