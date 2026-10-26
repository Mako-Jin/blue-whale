package com.bluewhale.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户可进入的租户关系实体（Wave 5.54 Phase 5.1：一人多租户）。
 *
 * <p>对应 {@code sys_user_tenant}：跨租户关系表，不参与行级租户过滤。
 * 关系只回答「该用户可进入哪些租户」，租户隔离仍由 {@code sys_role.tenant_id}
 * 与业务表 {@code tenant_id} 过滤保证。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_user_tenant")
public class SysUserTenant implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 全局用户 ID（逻辑关联 sys_user.id，切换前后不变） */
    private Long userId;
    /** 可进入的租户 ID（逻辑关联 sys_tenant.id） */
    private Long tenantId;
    /** 状态：1 启用 / 0 停用 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
