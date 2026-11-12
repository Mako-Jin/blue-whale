package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 租户口令策略响应（REQ-SEC-023）：生效值 + 全局默认值
 *
 * @author BlueWhale
 */
@Data
public class TenantPasswordPolicyResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 生效的口令有效期（天） */
    private int maxAgeDays;

    /** 生效的到期前提醒天数 */
    private int warnDays;

    /** 是否使用租户自定义配置（false 表示沿用全局默认） */
    private boolean customized;

    /** 全局默认口令有效期（天） */
    private int defaultMaxAgeDays;

    /** 全局默认提醒天数 */
    private int defaultWarnDays;
}
