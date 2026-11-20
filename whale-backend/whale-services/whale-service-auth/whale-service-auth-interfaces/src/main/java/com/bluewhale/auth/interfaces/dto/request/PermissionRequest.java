package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增/更新权限点请求
 *
 * @author BlueWhale
 */
@Data
public class PermissionRequest {

    private Long parentId;

    @NotBlank(message = "{validation.auth.permission.code-required}")
    @Size(max = 128, message = "{validation.auth.permission.code-size}")
    private String permCode;

    @NotBlank(message = "{validation.auth.permission.name-required}")
    @Size(max = 64, message = "{validation.auth.permission.name-size}")
    private String permName;

    /** 权限类型：SYSTEM / MENU / API / BUTTON / DATA / ATTR */
    @NotBlank(message = "{validation.auth.permission.type-required}")
    private String permType;

    private String path;
    private String method;
    private Integer sortNo;
    private Integer status;
}
