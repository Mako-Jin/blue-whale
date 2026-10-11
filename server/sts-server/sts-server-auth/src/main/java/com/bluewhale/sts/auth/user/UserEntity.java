package com.bluewhale.sts.auth.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.sts.auth.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户实体，对应表 {@code bw_auth_user}。
 *
 * <p><b>凭证直接落在本表，不另建凭证表 —— 这是有意偏离参照实现。</b>
 * {@code SystemTerminator} 把口令同时冗余在 {@code user_info} 与
 * {@code rel_tenant_users} 两张表里，两副本一旦因某条链路漏更新就会不一致，
 * 而"用哪个副本校验密码"本身就成了一个需要考古的问题。
 * 本切片的多租户模型是**用户归属单一租户**（{@code tenantId} 是普通列、
 * {@code username} 在租户内唯一），因此凭证天然只有一份。
 * 若将来要支持"同一自然人跨租户、口令各租户独立"，再拆凭证表 ——
 * 那时它的形态由真实需求决定，而不是提前设计。
 *
 * <p><b>口令只存哈希</b>（{@code passwordHash} + {@code passwordAlgorithm}），
 * 从不存明文，也不存可逆密文。算法标识随行存储，便于将来平滑升级算法：
 * 校验时按存储的算法校验，登录成功后再按新算法重算一遍覆盖。
 */
@Getter
@Setter
@TableName("bw_auth_user")
public class UserEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户主键 */
    private Long tenantId;

    /** 登录名，**租户内**唯一（不是全库唯一） */
    private String username;

    /** 显示名 / 姓名 */
    private String displayName;

    private String email;

    private String phone;

    /** 口令哈希 */
    private String passwordHash;

    /** 口令算法标识，如 {@code PBKDF2-SHA256} */
    private String passwordAlgorithm;

    /** 口令最后修改时间（毫秒） */
    private Long passwordUpdatedAt;

    /** 口令过期时间（毫秒），null 表示不过期 */
    private Long passwordExpiresAt;

    /** 是否强制下次登录改密：0 否 1 是 */
    private Integer mustChangePassword;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 连续登录失败次数 */
    private Integer failedAttempts;

    /** 锁定至（毫秒），null 表示未锁定 */
    private Long lockedUntil;

    private String lockReason;

    private Long lastLoginAt;

    private String avatarUrl;

    /** 是否启用多因子：0 否 1 是 */
    private Integer mfaEnabled;

    /** 多因子类型，如 TOTP / SMS */
    private String mfaType;

}
