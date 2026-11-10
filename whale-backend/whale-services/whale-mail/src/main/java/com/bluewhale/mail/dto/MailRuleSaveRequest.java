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
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 64, message = "规则名称过长")
    private String name;

    /** 优先级（小的先匹配） */
    @Min(value = 1, message = "优先级需大于 0")
    private Integer sortNo;

    /** 是否启用 */
    private Boolean enabled;

    /** 发件人包含（逗号分隔多值） */
    @Size(max = 190, message = "发件人条件过长")
    private String matchFrom;

    /** 主题包含（逗号分隔多值） */
    @Size(max = 255, message = "主题条件过长")
    private String matchSubject;

    /** 收件人包含（逗号分隔多值） */
    @Size(max = 190, message = "收件人条件过长")
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
