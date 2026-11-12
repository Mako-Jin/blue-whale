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
    @NotNull(message = "口令有效期不能为空")
    @Min(value = 1, message = "口令有效期最少 1 天")
    @Max(value = 365, message = "口令有效期最多 365 天")
    private Integer maxAgeDays;

    /** 到期前提醒天数 */
    @NotNull(message = "到期提醒天数不能为空")
    @Min(value = 0, message = "到期提醒天数不能为负数")
    @Max(value = 365, message = "到期提醒天数最多 365 天")
    private Integer warnDays;
}
