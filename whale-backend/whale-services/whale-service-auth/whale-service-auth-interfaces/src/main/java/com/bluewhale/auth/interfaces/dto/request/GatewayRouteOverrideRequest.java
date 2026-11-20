package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 网关路由覆盖新增/更新请求（路由 ID 由路径参数传递）。
 *
 * @author BlueWhale
 */
@Data
public class GatewayRouteOverrideRequest {

    /** 是否启用：false 停用（该路由全站 404，需二次确认） */
    @NotNull(message = "{validation.auth.gateway.enabled-required}")
    private Boolean enabled;

    /** 生效起始时间；空值表示立即生效 */
    private LocalDateTime effectiveFrom;

    /** 生效截止时间；空值表示长期生效 */
    private LocalDateTime effectiveTo;

    /** 备注（启停原因） */
    @Size(max = 255, message = "{validation.auth.gateway.remark-size}")
    private String remark;
}
