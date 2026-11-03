package com.bluewhale.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 口令历史实体（REQ-SEC-023，对应 {@code sys_password_history}）。
 *
 * <p>只保存 BCrypt 密文用于复用校验，不保存任何明文；关联表无逻辑删除字段，超出保留条数直接物理删除。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_password_history")
public class SysPasswordHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 所属租户 ID（逻辑关联 sys_tenant.id） */
    private Long tenantId;
    /** 用户 ID（逻辑关联 sys_user.id） */
    private Long userId;
    /** BCrypt 口令密文 */
    private String password;
    private LocalDateTime createTime;
}
