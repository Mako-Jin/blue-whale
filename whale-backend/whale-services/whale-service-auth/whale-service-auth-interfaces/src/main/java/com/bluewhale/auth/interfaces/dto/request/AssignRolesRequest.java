package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 用户分配角色请求（全量覆盖）
 *
 * @author BlueWhale
 */
@Data
public class AssignRolesRequest {

    @NotNull(message = "{validation.auth.role.ids-required}")
    private List<Long> roleIds;
}
