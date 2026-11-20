package com.bluewhale.auth.interfaces.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户开通请求（Wave 5.54 Phase 1）。
 *
 * @author BlueWhale
 */
@Data
public class TenantCreateRequest {

    @NotBlank(message = "{validation.auth.tenant.code-required}")
    @Pattern(regexp = "^[A-Za-z0-9_-]{2,64}$", message = "{validation.auth.tenant.code-pattern}")
    private String tenantCode;

    @NotBlank(message = "{validation.auth.tenant.name-required}")
    @Size(max = 128, message = "{validation.auth.tenant.name-size}")
    private String tenantName;

    /** 到期时间，为空表示长期有效 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    /** 配额 JSON，如 {"maxUsers":50,"maxStorageMb":1024} */
    @Size(max = 1024, message = "{validation.auth.tenant.quota-size}")
    private String quotaJson;

    @NotBlank(message = "{validation.auth.tenant.admin-username-required}")
    @Size(max = 64, message = "{validation.auth.tenant.admin-username-size}")
    private String adminUsername;

    @NotBlank(message = "{validation.auth.tenant.admin-password-required}")
    @Size(min = 6, max = 64, message = "{validation.auth.tenant.admin-password-size}")
    private String adminPassword;

    private String adminNickname;
}
