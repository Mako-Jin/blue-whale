package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 启停用户请求
 *
 * @author BlueWhale
 */
@Data
public class UserStatusRequest {

    /** 状态：1 启用 / 0 停用 */
    @NotNull(message = "{validation.auth.common.status-required}")
    private Integer status;
}
