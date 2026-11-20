package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 自助改密请求（REQ-SEC-023）：旧口令用于校验身份，新口令交由后端策略统一校验。
 *
 * <p>{@code @Size} 仅做粗粒度边界防护；大小写/数字/特殊字符、弱口令与历史复用
 * 由 {@code PasswordPolicyService} 按 {@code whale.auth.password.*} 配置判定。</p>
 *
 * @author BlueWhale
 */
@Data
public class ChangeOwnPasswordRequest {

    @NotBlank(message = "{validation.auth.password.old-required}")
    private String oldPassword;

    @NotBlank(message = "{validation.auth.password.new-required}")
    @Size(min = 8, max = 64, message = "{validation.auth.password.length-range}")
    private String newPassword;
}
