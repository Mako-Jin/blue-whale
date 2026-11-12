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

    @NotBlank(message = "权限编码不能为空")
    @Size(max = 128, message = "权限编码长度不能超过 128")
    private String permCode;

    @NotBlank(message = "权限名称不能为空")
    @Size(max = 64, message = "权限名称长度不能超过 64")
    private String permName;

    /** 权限类型：SYSTEM / MENU / API / BUTTON / DATA / ATTR */
    @NotBlank(message = "权限类型不能为空")
    private String permType;

    private String path;
    private String method;
    private Integer sortNo;
    private Integer status;
}
