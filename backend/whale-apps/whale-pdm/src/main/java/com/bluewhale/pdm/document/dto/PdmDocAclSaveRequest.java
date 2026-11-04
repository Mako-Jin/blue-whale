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
    @NotBlank(message = "资源类型不能为空")
    private String resourceType;

    /** 资源 ID */
    @NotNull(message = "资源ID不能为空")
    private Long resourceId;

    /** 主体类型：USER/ROLE/ALL */
    @NotBlank(message = "主体类型不能为空")
    private String subjectType;

    /** 主体标识：用户 ID / 角色 code；ALL 时为空并置 0 */
    @Size(max = 64, message = "主体标识不能超过 64 个字符")
    private String subjectId;

    /** 动作：VIEW/EDIT/DOWNLOAD/DELETE/MANAGE */
    @NotBlank(message = "动作不能为空")
    private String action;

    /** 效果：ALLOW/DENY */
    @NotBlank(message = "效果不能为空")
    private String effect;
}
