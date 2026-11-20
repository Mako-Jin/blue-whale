package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增角色请求
 *
 * @author BlueWhale
 */
@Data
public class RoleCreateRequest {

    @NotBlank(message = "{validation.auth.role.code-required}")
    @Size(max = 64, message = "{validation.auth.role.code-size}")
    private String roleCode;

    @NotBlank(message = "{validation.auth.role.name-required}")
    @Size(max = 64, message = "{validation.auth.role.name-size}")
    private String roleName;

    private String description;
    private Integer sortNo;
    private Integer status;
}
