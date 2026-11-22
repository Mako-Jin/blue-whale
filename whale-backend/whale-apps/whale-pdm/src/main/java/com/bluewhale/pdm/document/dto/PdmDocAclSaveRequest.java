package com.bluewhale.pdm.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 图文档 ACL 保存请求
 *
 * @author BlueWhale
 */
@Data
public class PdmDocAclSaveRequest {

    /** 资源类型：FOLDER/DOCUMENT */
    @NotBlank(message = "{validation.pdm.pdm-doc-acl.resource-type-required}")
    private String resourceType;

    /** 资源 ID */
    @NotNull(message = "{validation.pdm.pdm-doc-acl.resource-id-required}")
    private Long resourceId;

    /** 主体类型：USER/ROLE/ALL */
    @NotBlank(message = "{validation.pdm.pdm-doc-acl.subject-type-required}")
    private String subjectType;

    /** 主体标识：用户 ID / 角色 code；ALL 时为空并置 0 */
    @Size(max = 64, message = "{validation.pdm.pdm-doc-acl.subject-id-size}")
    private String subjectId;

    /** 动作：VIEW/EDIT/DOWNLOAD/DELETE/MANAGE */
    @NotBlank(message = "{validation.pdm.pdm-doc-acl.action-required}")
    private String action;

    /** 效果：ALLOW/DENY */
    @NotBlank(message = "{validation.pdm.pdm-doc-acl.effect-required}")
    private String effect;
}
