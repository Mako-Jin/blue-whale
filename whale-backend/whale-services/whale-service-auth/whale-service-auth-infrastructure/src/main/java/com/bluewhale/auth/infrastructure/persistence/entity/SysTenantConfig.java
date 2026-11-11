package com.bluewhale.auth.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户配置实体（租户管理员维护的默认值层）。
 *
 * <p>与 {@link SysUserConfig} 同一口径：auth 只存取不解释语义；
 * 读取优先级约定为「用户配置 &gt; 租户配置 &gt; 消费方内置默认」。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_tenant_config")
public class SysTenantConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 租户 ID（逻辑关联 sys_tenant.id） */
    private Long tenantId;
    /** 配置键（前缀分域，与 sys_user_config 同一口径） */
    private String configKey;
    /** 配置值（JSON），作为该租户用户的默认值层 */
    private String configValue;
    /** 最后更新人用户 ID */
    private Long updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
