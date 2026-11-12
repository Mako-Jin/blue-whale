package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新角色请求（角色编码不可修改）
 *
 * @author BlueWhale
 */
@Data
public class RoleUpdateRequest {

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64, message = "角色名称长度不能超过 64")
    private String roleName;

    private String description;
    private Integer sortNo;
    private Integer status;
}
