package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 租户口令策略配置请求（REQ-SEC-023）。
 *
 * <p>范围限制：有效期 1–365 天（不允许通过租户配置关闭到期强制）；提醒天数 0–有效期，
 * 两者关系统一由应用层校验并返回明确文案。</p>
 *
 * @author BlueWhale
 */
@Data
public class TenantPasswordPolicyRequest {

    /** 口令有效期（天） */
    @NotNull(message = "{validation.auth.tenant.password-max-age-required}")
    @Min(value = 1, message = "{validation.auth.tenant.password-max-age-min}")
    @Max(value = 365, message = "{validation.auth.tenant.password-max-age-max}")
    private Integer maxAgeDays;

    /** 到期前提醒天数 */
    @NotNull(message = "{validation.auth.tenant.password-reminder-required}")
    @Min(value = 0, message = "{validation.auth.tenant.password-reminder-min}")
    @Max(value = 365, message = "{validation.auth.tenant.password-reminder-max}")
    private Integer warnDays;
}
