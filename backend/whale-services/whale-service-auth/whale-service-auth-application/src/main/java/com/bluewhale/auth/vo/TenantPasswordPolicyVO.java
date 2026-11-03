package com.bluewhale.auth.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 租户口令策略视图（REQ-SEC-023）。
 *
 * <p>返回本租户当前生效的口令有效期与提醒天数，并附带全局默认值，
 * 便于前端展示「已自定义 / 恢复默认」状态。{@code customized=false} 表示当前沿用全局默认。</p>
 *
 * @author BlueWhale
 */
@Data
public class TenantPasswordPolicyVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 生效的口令有效期（天） */
    private int maxAgeDays;

    /** 生效的到期前提醒天数 */
    private int warnDays;

    /** 是否使用租户自定义配置（false 表示沿用全局默认） */
    private boolean customized;

    /** 全局默认口令有效期（天），用于「恢复默认」提示 */
    private int defaultMaxAgeDays;

    /** 全局默认提醒天数，用于「恢复默认」提示 */
    private int defaultWarnDays;

    /**
     * 构造租户口令策略视图
     *
     * @param tenantId 租户 ID
     * @param maxAgeDays 生效口令有效期（天）
     * @param warnDays 生效提醒天数
     * @param customized 是否租户自定义
     * @param defaultMaxAgeDays 全局默认有效期（天）
     * @param defaultWarnDays 全局默认提醒天数
     */
    public TenantPasswordPolicyVO(Long tenantId, int maxAgeDays, int warnDays, boolean customized,
                                  int defaultMaxAgeDays, int defaultWarnDays) {
        this.tenantId = tenantId;
        this.maxAgeDays = maxAgeDays;
        this.warnDays = warnDays;
        this.customized = customized;
        this.defaultMaxAgeDays = defaultMaxAgeDays;
        this.defaultWarnDays = defaultWarnDays;
    }
}
