package com.bluewhale.auth.dto;

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

    @NotNull(message = "角色ID列表不能为 null")
    private List<Long> roleIds;
}
