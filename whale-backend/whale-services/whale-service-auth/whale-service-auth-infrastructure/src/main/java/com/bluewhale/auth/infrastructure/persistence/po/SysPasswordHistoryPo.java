package com.bluewhale.auth.infrastructure.persistence.po;

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
public class SysPasswordHistoryPo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 所属租户 ID（逻辑关联 sys_tenant.id） */
    private Long tenantId;
    /** 用户 ID（逻辑关联 sys_user.id） */
    private Long userId;
    /** BCrypt 口令密文 */
    private String password;
    /** 是否当前生效口令：1 是（仅最后一条为 1，其余为 0） */
    private Integer isActive;
    /** 口令到期时间（租户策略到期口径快照，可空） */
    private LocalDateTime expiresAt;
    /** 变更来源：LEGACY 存量 / USER_CHANGE 用户自改 / ADMIN_RESET 管理员重置 / SOCIAL_BIND 三方绑定设密 */
    private String changeSource;
    private LocalDateTime createTime;
}
