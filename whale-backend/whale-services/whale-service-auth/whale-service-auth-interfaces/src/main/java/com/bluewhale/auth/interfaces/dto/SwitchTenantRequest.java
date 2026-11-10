package com.bluewhale.auth.interfaces.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 切换租户请求（Wave 5.54 Phase 5.1；V-03：旧 refreshToken 改由 Cookie 自动携带，不再接受请求体传入）。
 *
 * <p>只接受目标租户 ID；用户身份一律取自 {@code SecurityContext}，不接受客户端传入
 * {@code userId} / 角色。</p>
 *
 * @author BlueWhale
 */
@Data
public class SwitchTenantRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标租户 ID（必须是当前用户可进入的租户） */
    @NotNull(message = "租户ID不能为空")
    private Long tenantId;
}
