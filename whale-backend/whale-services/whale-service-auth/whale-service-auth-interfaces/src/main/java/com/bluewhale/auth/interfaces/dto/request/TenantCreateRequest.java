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

    @NotBlank(message = "租户编码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_-]{2,64}$", message = "租户编码仅支持 2-64 位字母、数字、下划线或中划线")
    private String tenantCode;

    @NotBlank(message = "租户名称不能为空")
    @Size(max = 128, message = "租户名称长度不能超过 128")
    private String tenantName;

    /** 到期时间，为空表示长期有效 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    /** 配额 JSON，如 {"maxUsers":50,"maxStorageMb":1024} */
    @Size(max = 1024, message = "配额 JSON 长度不能超过 1024")
    private String quotaJson;

    @NotBlank(message = "管理员用户名不能为空")
    @Size(max = 64, message = "管理员用户名长度不能超过 64")
    private String adminUsername;

    @NotBlank(message = "管理员密码不能为空")
    @Size(min = 6, max = 64, message = "管理员密码长度必须在 6-64 之间")
    private String adminPassword;

    private String adminNickname;
}
