package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 租户启停请求。
 *
 * @author BlueWhale
 */
@Data
public class TenantStatusRequest {

    /** 状态：1 启用 / 0 停用 */
    @NotNull(message = "{validation.auth.common.status-required}")
    private Integer status;
}
