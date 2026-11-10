package com.bluewhale.auth.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 租户口令策略实体（REQ-SEC-023，对应 {@code sys_tenant_password_policy}）。
 *
 * <p>每个租户至多一条配置；未配置的租户在应用层回退到全局默认策略。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_tenant_password_policy")
public class SysTenantPasswordPolicy implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 租户 ID（逻辑关联 sys_tenant.id，全局唯一） */
    private Long tenantId;
    /** 口令有效期（天） */
    private Integer maxAgeDays;
    /** 到期前提醒天数 */
    private Integer warnDays;
    private Integer deleted;
}
