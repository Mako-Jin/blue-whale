package com.bluewhale.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 角色分配权限请求（全量覆盖，权限点 + 菜单统一挂 sys_role_permission）
 *
 * @author BlueWhale
 */
@Data
public class AssignPermissionsRequest {

    @NotNull(message = "权限ID列表不能为 null")
    private List<Long> permissionIds;
}
